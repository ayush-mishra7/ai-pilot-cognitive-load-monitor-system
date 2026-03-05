package com.aipclm.system.adsb.repository;

import com.aipclm.system.adsb.model.AdsbAircraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AdsbAircraftRepository extends JpaRepository<AdsbAircraft, UUID> {

    /**
     * Find all aircraft observed within a time window, ordered by distance.
     */
    @Query("SELECT a FROM AdsbAircraft a WHERE a.observedAt >= :since " +
           "AND a.referenceLat = :lat AND a.referenceLon = :lon " +
           "ORDER BY a.distanceNm ASC")
    List<AdsbAircraft> findNearbyAircraft(@Param("lat") Double lat,
                                          @Param("lon") Double lon,
                                          @Param("since") Instant since);

    /**
     * Find all aircraft from the most recent fetch batch for a reference point.
     */
    @Query("SELECT a FROM AdsbAircraft a WHERE a.referenceLat = :lat " +
           "AND a.referenceLon = :lon ORDER BY a.fetchedAt DESC, a.distanceNm ASC")
    List<AdsbAircraft> findLatestByReference(@Param("lat") Double lat,
                                             @Param("lon") Double lon);

    /**
     * Find aircraft by callsign pattern.
     */
    List<AdsbAircraft> findByCallsignContainingIgnoreCaseOrderByObservedAtDesc(String callsign);

    /**
     * Count aircraft within a given distance from the most recent fetch.
     */
    @Query("SELECT COUNT(a) FROM AdsbAircraft a WHERE a.referenceLat = :lat " +
           "AND a.referenceLon = :lon AND a.distanceNm <= :maxDist " +
           "AND a.observedAt >= :since")
    long countNearbyWithinDistance(@Param("lat") Double lat,
                                  @Param("lon") Double lon,
                                  @Param("maxDist") Double maxDistNm,
                                  @Param("since") Instant since);

    /**
     * Delete old records to keep the table lean.
     */
    void deleteByObservedAtBefore(Instant cutoff);
}
