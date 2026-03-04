package com.aipclm.system.sensor.repository;

import com.aipclm.system.sensor.model.ConnectionStatus;
import com.aipclm.system.sensor.model.SensorDevice;
import com.aipclm.system.sensor.model.SensorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SensorDeviceRepository extends JpaRepository<SensorDevice, UUID> {

    List<SensorDevice> findByFlightSessionId(UUID sessionId);

    List<SensorDevice> findByFlightSessionIdAndConnectionStatus(UUID sessionId, ConnectionStatus status);

    Optional<SensorDevice> findByFlightSessionIdAndSensorType(UUID sessionId, SensorType sensorType);

    List<SensorDevice> findByConnectionStatus(ConnectionStatus status);
}
