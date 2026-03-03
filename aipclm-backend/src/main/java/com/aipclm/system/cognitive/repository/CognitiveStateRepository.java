package com.aipclm.system.cognitive.repository;

import com.aipclm.system.cognitive.model.CognitiveState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CognitiveStateRepository extends JpaRepository<CognitiveState, UUID> {

    @Query("""
            SELECT c FROM CognitiveState c
            WHERE c.telemetryFrame.flightSession.id = :sessionId
            ORDER BY c.timestamp DESC
            LIMIT 5
            """)
    List<CognitiveState> findTop5BySessionIdOrderByTimestampDesc(@Param("sessionId") UUID sessionId);

    Optional<CognitiveState> findByTelemetryFrameId(UUID telemetryFrameId);

    @Query("""
            SELECT c FROM CognitiveState c
            WHERE c.telemetryFrame.flightSession.id = :sessionId
            ORDER BY c.timestamp ASC
            """)
    List<CognitiveState> findAllBySessionIdOrderByTimestampAsc(@Param("sessionId") UUID sessionId);

    /**
     * Returns the most recent N cognitive states for a session (ordered oldest-first).
     * Used for EMA smoothing and fatigue trend slope computation.
     */
    @Query("""
            SELECT c FROM CognitiveState c
            WHERE c.telemetryFrame.flightSession.id = :sessionId
            ORDER BY c.timestamp DESC
            LIMIT :limit
            """)
    List<CognitiveState> findRecentBySessionId(@Param("sessionId") UUID sessionId,
                                                @Param("limit") int limit);
}
