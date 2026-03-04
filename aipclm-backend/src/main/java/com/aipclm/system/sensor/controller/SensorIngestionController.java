package com.aipclm.system.sensor.controller;

import com.aipclm.system.sensor.model.SensorDevice;
import com.aipclm.system.sensor.model.SensorReading;
import com.aipclm.system.sensor.model.SensorType;
import com.aipclm.system.sensor.repository.SensorDeviceRepository;
import com.aipclm.system.sensor.repository.SensorReadingRepository;
import com.aipclm.system.sensor.service.SensorIngestionService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sensor")
@RequiredArgsConstructor
public class SensorIngestionController {

    private final SensorIngestionService sensorIngestionService;
    private final SensorDeviceRepository sensorDeviceRepository;
    private final SensorReadingRepository sensorReadingRepository;

    // ─────────── Device Management ───────────

    @PostMapping("/device")
    public ResponseEntity<DeviceDto> registerDevice(@RequestBody RegisterDeviceRequest req) {
        SensorDevice device = sensorIngestionService.registerDevice(
                req.getDeviceName(), req.getSensorType(),
                req.getManufacturer(), req.getModelNumber());
        return ResponseEntity.ok(toDeviceDto(device));
    }

    @GetMapping("/device/list")
    public ResponseEntity<List<DeviceDto>> listAllDevices() {
        List<DeviceDto> devices = sensorDeviceRepository.findAll().stream()
                .map(this::toDeviceDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(devices);
    }

    @GetMapping("/device/{deviceId}")
    public ResponseEntity<DeviceDto> getDevice(@PathVariable UUID deviceId) {
        SensorDevice device = sensorDeviceRepository.findById(deviceId).orElse(null);
        if (device == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toDeviceDto(device));
    }

    @PutMapping("/device/{deviceId}/connect/{sessionId}")
    public ResponseEntity<DeviceDto> connectDevice(@PathVariable UUID deviceId,
                                                    @PathVariable UUID sessionId) {
        SensorDevice device = sensorIngestionService.connectDevice(deviceId, sessionId);
        return ResponseEntity.ok(toDeviceDto(device));
    }

    @PutMapping("/device/{deviceId}/disconnect")
    public ResponseEntity<DeviceDto> disconnectDevice(@PathVariable UUID deviceId) {
        SensorDevice device = sensorIngestionService.disconnectDevice(deviceId);
        return ResponseEntity.ok(toDeviceDto(device));
    }

    @PutMapping("/device/{deviceId}/calibrate")
    public ResponseEntity<DeviceDto> calibrateDevice(@PathVariable UUID deviceId,
                                                      @RequestParam(defaultValue = "0.0") double offset,
                                                      @RequestParam(defaultValue = "1.0") double gain) {
        SensorDevice device = sensorIngestionService.calibrateDevice(deviceId, offset, gain);
        return ResponseEntity.ok(toDeviceDto(device));
    }

    // ─────────── Reading Ingestion ───────────

    @PostMapping("/reading")
    public ResponseEntity<ReadingDto> ingestReading(@RequestBody IngestReadingRequest req) {
        SensorReading reading = sensorIngestionService.ingestReading(
                req.getDeviceId(), req.getRawValue(), req.getUnit(), req.getSignalQuality());
        return ResponseEntity.ok(toReadingDto(reading));
    }

    @PostMapping("/reading/batch")
    public ResponseEntity<List<ReadingDto>> ingestBatch(@RequestBody BatchReadingRequest req) {
        List<SensorIngestionService.RawReading> readings = req.getReadings().stream()
                .map(r -> new SensorIngestionService.RawReading(r.getRawValue(), r.getUnit(), r.getSignalQuality()))
                .collect(Collectors.toList());
        List<SensorReading> saved = sensorIngestionService.ingestBatch(req.getDeviceId(), readings);
        return ResponseEntity.ok(saved.stream().map(this::toReadingDto).collect(Collectors.toList()));
    }

    // ─────────── Session Sensor Status ───────────

    @GetMapping("/session/{sessionId}/status")
    public ResponseEntity<List<SensorIngestionService.SensorStatusEntry>> getSensorStatus(
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(sensorIngestionService.getSessionSensorStatus(sessionId));
    }

    @GetMapping("/session/{sessionId}/latest-values")
    public ResponseEntity<Map<SensorType, Double>> getLatestValues(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(sensorIngestionService.getLatestSensorValues(sessionId));
    }

    @GetMapping("/session/{sessionId}/readings")
    public ResponseEntity<List<ReadingDto>> getSessionReadings(
            @PathVariable UUID sessionId,
            @RequestParam(required = false) SensorType sensorType) {
        List<SensorReading> readings;
        if (sensorType != null) {
            readings = sensorReadingRepository.findBySessionAndTypeOrdered(sessionId, sensorType);
        } else {
            readings = sensorReadingRepository.findByFlightSessionIdOrderByTimestampAsc(sessionId);
        }
        return ResponseEntity.ok(readings.stream().map(this::toReadingDto).collect(Collectors.toList()));
    }

    // ─────────── Quick-Register Preset ───────────

    @PostMapping("/quick-register")
    public ResponseEntity<List<DeviceDto>> quickRegister() {
        List<SensorDevice> devices = List.of(
                sensorIngestionService.registerDevice("Garmin HRM-Pro Plus", SensorType.HEART_RATE_MONITOR,
                        "Garmin", "HRM-Pro+"),
                sensorIngestionService.registerDevice("Muse 2 EEG Headband", SensorType.EEG_HEADBAND,
                        "InteraXon", "Muse 2"),
                sensorIngestionService.registerDevice("Tobii Pro Nano", SensorType.EYE_TRACKER,
                        "Tobii", "Pro Nano"),
                sensorIngestionService.registerDevice("Shimmer3 GSR+", SensorType.GSR_SENSOR,
                        "Shimmer", "GSR+ Unit"),
                sensorIngestionService.registerDevice("Masimo MightySat Rx", SensorType.PULSE_OXIMETER,
                        "Masimo", "MightySat Rx"),
                sensorIngestionService.registerDevice("Empatica E4 Temp", SensorType.SKIN_TEMPERATURE_SENSOR,
                        "Empatica", "E4")
        );
        return ResponseEntity.ok(devices.stream().map(this::toDeviceDto).collect(Collectors.toList()));
    }

    // ─────────── DTOs ───────────

    @Data
    public static class RegisterDeviceRequest {
        private String deviceName;
        private SensorType sensorType;
        private String manufacturer;
        private String modelNumber;
    }

    @Data
    public static class IngestReadingRequest {
        private UUID deviceId;
        private double rawValue;
        private String unit;
        private double signalQuality;
    }

    @Data
    public static class BatchReadingRequest {
        private UUID deviceId;
        private List<IngestReadingRequest> readings;
    }

    @Data @Builder
    public static class DeviceDto {
        private UUID id;
        private String deviceName;
        private String sensorType;
        private String manufacturer;
        private String modelNumber;
        private String connectionStatus;
        private double calibrationOffset;
        private double calibrationGain;
        private UUID sessionId;
        private Instant lastDataReceivedAt;
    }

    @Data @Builder
    public static class ReadingDto {
        private UUID id;
        private UUID deviceId;
        private String sensorType;
        private int frameNumber;
        private double rawValue;
        private double normalizedValue;
        private String unit;
        private double signalQuality;
        private Instant timestamp;
    }

    // ─────────── Mappers ───────────

    private DeviceDto toDeviceDto(SensorDevice d) {
        return DeviceDto.builder()
                .id(d.getId())
                .deviceName(d.getDeviceName())
                .sensorType(d.getSensorType().name())
                .manufacturer(d.getManufacturer())
                .modelNumber(d.getModelNumber())
                .connectionStatus(d.getConnectionStatus().name())
                .calibrationOffset(d.getCalibrationOffset())
                .calibrationGain(d.getCalibrationGain())
                .sessionId(d.getFlightSession() != null ? d.getFlightSession().getId() : null)
                .lastDataReceivedAt(d.getLastDataReceivedAt())
                .build();
    }

    private ReadingDto toReadingDto(SensorReading r) {
        return ReadingDto.builder()
                .id(r.getId())
                .deviceId(r.getSensorDevice().getId())
                .sensorType(r.getSensorDevice().getSensorType().name())
                .frameNumber(r.getFrameNumber())
                .rawValue(r.getRawValue())
                .normalizedValue(r.getNormalizedValue())
                .unit(r.getUnit())
                .signalQuality(r.getSignalQuality())
                .timestamp(r.getTimestamp())
                .build();
    }
}
