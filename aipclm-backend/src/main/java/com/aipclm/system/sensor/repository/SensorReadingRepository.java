package com.aipclm.system.sensor.repository;

import com.aipclm.system.sensor.model.SensorReading;
import com.aipclm.system.sensor.model.SensorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SensorReadingRepository extends JpaRepository<SensorReading, UUID> {

    /** Most recent reading for a given sensor device. */
    Optional<SensorReading> findTopBySensorDeviceIdOrderByTimestampDesc(UUID deviceId);

    /** Most recent reading per sensor type for a session (for pipeline override). */
    @Query("SELECT r FROM SensorReading r " +
           "WHERE r.flightSession.id = :sid " +
           "  AND r.sensorDevice.sensorType = :type " +
           "ORDER BY r.timestamp DESC LIMIT 1")
    Optional<SensorReading> findLatestBySessionAndType(@Param("sid") UUID sessionId,
                                                       @Param("type") SensorType type);

    /** All readings for session, ordered by time (for analytics). */
    List<SensorReading> findByFlightSessionIdOrderByTimestampAsc(UUID sessionId);

    /** All readings for session + device type (for analytics). */
    @Query("SELECT r FROM SensorReading r " +
           "WHERE r.flightSession.id = :sid " +
           "  AND r.sensorDevice.sensorType = :type " +
           "ORDER BY r.timestamp ASC")
    List<SensorReading> findBySessionAndTypeOrdered(@Param("sid") UUID sessionId,
                                                    @Param("type") SensorType type);

    /** Count of readings per session (for status dashboard). */
    long countByFlightSessionId(UUID sessionId);
}
