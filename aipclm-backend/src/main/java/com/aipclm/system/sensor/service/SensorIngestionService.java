package com.aipclm.system.sensor.service;

import com.aipclm.system.sensor.model.*;
import com.aipclm.system.sensor.repository.SensorDeviceRepository;
import com.aipclm.system.sensor.repository.SensorReadingRepository;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.repository.FlightSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Manages wearable sensor devices and ingests live physiological readings.
 * <ul>
 *   <li>Device lifecycle: register → calibrate → connect → disconnect</li>
 *   <li>Reading ingestion: normalise raw value via calibration, persist, and
 *       broadcast status updates over WebSocket</li>
 *   <li>Pipeline integration: provides latest sensor readings so the simulation
 *       engine can override simulated biometrics with real data</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SensorIngestionService {

    private final SensorDeviceRepository deviceRepository;
    private final SensorReadingRepository readingRepository;
    private final FlightSessionRepository sessionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ─────────── Device Lifecycle ───────────

    @Transactional
    public SensorDevice registerDevice(String deviceName, SensorType sensorType,
                                        String manufacturer, String modelNumber) {
        SensorDevice device = SensorDevice.builder()
                .deviceName(deviceName)
                .sensorType(sensorType)
                .manufacturer(manufacturer)
                .modelNumber(modelNumber)
                .connectionStatus(ConnectionStatus.DISCONNECTED)
                .build();
        device = deviceRepository.save(device);
        log.info("[Sensor] Registered device {} type={}", device.getId(), sensorType);
        return device;
    }

    @Transactional
    public SensorDevice connectDevice(UUID deviceId, UUID sessionId) {
        SensorDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));
        FlightSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        // Prevent duplicate sensor type per session
        Optional<SensorDevice> existing = deviceRepository
                .findByFlightSessionIdAndSensorType(sessionId, device.getSensorType());
        if (existing.isPresent() && !existing.get().getId().equals(deviceId)) {
            throw new IllegalStateException("Sensor type " + device.getSensorType()
                    + " already connected for session " + sessionId);
        }

        device.setFlightSession(session);
        device.setConnectionStatus(ConnectionStatus.CALIBRATING);
        device = deviceRepository.save(device);

        // Simulate a brief calibration → auto-connect
        device.setConnectionStatus(ConnectionStatus.CONNECTED);
        device = deviceRepository.save(device);

        broadcastSensorStatus(sessionId);
        log.info("[Sensor] Connected device {} to session {}", deviceId, sessionId);
        return device;
    }

    @Transactional
    public SensorDevice disconnectDevice(UUID deviceId) {
        SensorDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));
        UUID sessionId = device.getFlightSession() != null ? device.getFlightSession().getId() : null;
        device.setConnectionStatus(ConnectionStatus.DISCONNECTED);
        device.setFlightSession(null);
        device = deviceRepository.save(device);

        if (sessionId != null) {
            broadcastSensorStatus(sessionId);
        }
        log.info("[Sensor] Disconnected device {}", deviceId);
        return device;
    }

    @Transactional
    public SensorDevice calibrateDevice(UUID deviceId, double offset, double gain) {
        SensorDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));
        device.setCalibrationOffset(offset);
        device.setCalibrationGain(gain);
        device = deviceRepository.save(device);
        log.info("[Sensor] Calibrated device {} offset={} gain={}", deviceId, offset, gain);
        return device;
    }

    // ─────────── Reading Ingestion ───────────

    @Transactional
    public SensorReading ingestReading(UUID deviceId, double rawValue, String unit,
                                        double signalQuality) {
        SensorDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        if (device.getConnectionStatus() != ConnectionStatus.CONNECTED) {
            throw new IllegalStateException("Device " + deviceId + " is not connected");
        }
        if (device.getFlightSession() == null) {
            throw new IllegalStateException("Device " + deviceId + " has no session bound");
        }

        FlightSession session = device.getFlightSession();
        double normalizedValue = normalizeValue(rawValue, device);
        int currentFrame = session.getTotalFramesGenerated();

        SensorReading reading = SensorReading.builder()
                .sensorDevice(device)
                .flightSession(session)
                .frameNumber(currentFrame)
                .rawValue(rawValue)
                .normalizedValue(normalizedValue)
                .unit(unit)
                .signalQuality(Math.max(0, Math.min(1.0, signalQuality)))
                .timestamp(Instant.now())
                .build();

        reading = readingRepository.save(reading);

        device.setLastDataReceivedAt(Instant.now());
        deviceRepository.save(device);

        log.debug("[Sensor] Ingested reading from device {} raw={} norm={} quality={}",
                deviceId, rawValue, normalizedValue, signalQuality);
        return reading;
    }

    @Transactional
    public List<SensorReading> ingestBatch(UUID deviceId, List<RawReading> readings) {
        List<SensorReading> saved = new ArrayList<>();
        for (RawReading r : readings) {
            saved.add(ingestReading(deviceId, r.rawValue(), r.unit(), r.signalQuality()));
        }
        return saved;
    }

    public record RawReading(double rawValue, String unit, double signalQuality) {}

    // ─────────── Pipeline Integration ───────────

    /**
     * Returns a map of SensorType → latest normalizedValue for the given session.
     * Used by SimulationEngineService to override simulated biometrics.
     */
    public Map<SensorType, Double> getLatestSensorValues(UUID sessionId) {
        List<SensorDevice> connected = deviceRepository
                .findByFlightSessionIdAndConnectionStatus(sessionId, ConnectionStatus.CONNECTED);
        Map<SensorType, Double> values = new EnumMap<>(SensorType.class);

        for (SensorDevice device : connected) {
            readingRepository.findLatestBySessionAndType(sessionId, device.getSensorType())
                    .ifPresent(r -> {
                        if (r.getSignalQuality() >= 0.3) { // reject noisy readings
                            values.put(device.getSensorType(), r.getNormalizedValue());
                        }
                    });
        }
        return values;
    }

    /**
     * Returns full sensor status for a session — used for dashboard display.
     */
    public List<SensorStatusEntry> getSessionSensorStatus(UUID sessionId) {
        List<SensorDevice> devices = deviceRepository.findByFlightSessionId(sessionId);
        List<SensorStatusEntry> statuses = new ArrayList<>();

        for (SensorDevice d : devices) {
            Optional<SensorReading> latest = readingRepository
                    .findLatestBySessionAndType(sessionId, d.getSensorType());
            statuses.add(new SensorStatusEntry(
                    d.getId(),
                    d.getDeviceName(),
                    d.getSensorType().name(),
                    d.getManufacturer(),
                    d.getModelNumber(),
                    d.getConnectionStatus().name(),
                    latest.map(SensorReading::getNormalizedValue).orElse(null),
                    latest.map(SensorReading::getSignalQuality).orElse(null),
                    latest.map(SensorReading::getUnit).orElse(null),
                    d.getLastDataReceivedAt(),
                    readingRepository.countByFlightSessionId(sessionId)
            ));
        }
        return statuses;
    }

    public record SensorStatusEntry(
            UUID deviceId, String deviceName, String sensorType,
            String manufacturer, String modelNumber, String connectionStatus,
            Double latestValue, Double signalQuality, String unit,
            Instant lastDataAt, long totalReadings) {}

    // ─────────── Normalisation ───────────

    private double normalizeValue(double rawValue, SensorDevice device) {
        double value = device.getCalibrationGain() * rawValue + device.getCalibrationOffset();

        // Clamp to physiological ranges
        return switch (device.getSensorType()) {
            case HEART_RATE_MONITOR -> Math.max(30, Math.min(240, value));       // bpm
            case EEG_HEADBAND       -> Math.max(0, Math.min(100, value));        // µV power
            case EYE_TRACKER        -> Math.max(0, Math.min(20, value));         // mm pupil diameter
            case GSR_SENSOR         -> Math.max(0, Math.min(40, value));         // µS
            case PULSE_OXIMETER     -> Math.max(70, Math.min(100, value));       // SpO2 %
            case SKIN_TEMPERATURE_SENSOR -> Math.max(30, Math.min(42, value));   // °C
        };
    }

    // ─────────── WebSocket ───────────

    private void broadcastSensorStatus(UUID sessionId) {
        try {
            List<SensorStatusEntry> statuses = getSessionSensorStatus(sessionId);
            messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/sensor-status", statuses);
        } catch (Exception e) {
            log.warn("[Sensor] Failed to broadcast sensor status for session={}: {}", sessionId, e.getMessage());
        }
    }
}
