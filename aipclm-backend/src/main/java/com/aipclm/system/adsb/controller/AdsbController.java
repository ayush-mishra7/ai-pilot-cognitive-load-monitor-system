package com.aipclm.system.adsb.controller;

import com.aipclm.system.adsb.model.AdsbAircraft;
import com.aipclm.system.adsb.service.AdsbService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for ADS-B aircraft surveillance data.
 * Provides endpoints for fetching live/synthetic traffic near a reference point.
 */
@RestController
@RequestMapping("/api/adsb")
@RequiredArgsConstructor
public class AdsbController {

    private final AdsbService adsbService;

    /**
     * Fetch fresh ADS-B data for aircraft near the given lat/lon.
     * Makes an API call (or generates synthetic) and persists results.
     */
    @PostMapping("/fetch")
    public ResponseEntity<List<AircraftDto>> fetchNearby(
            @RequestParam double lat,
            @RequestParam double lon) {
        List<AdsbAircraft> aircraft = adsbService.fetchNearbyAircraft(lat, lon);
        List<AircraftDto> dtos = aircraft.stream().map(AircraftDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get the latest cached ADS-B data for a reference point.
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<AircraftDto>> getCached(
            @RequestParam double lat,
            @RequestParam double lon) {
        List<AdsbAircraft> aircraft = adsbService.getLatestCached(lat, lon);
        List<AircraftDto> dtos = aircraft.stream().map(AircraftDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get a traffic density summary for a reference point.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getTrafficSummary(
            @RequestParam double lat,
            @RequestParam double lon) {
        AdsbService.TrafficSummary summary = adsbService.getTrafficSummary(lat, lon);
        return ResponseEntity.ok(Map.of(
                "totalAircraft", summary.totalAircraft(),
                "closestDistanceNm", summary.closestDistanceNm() != null ? summary.closestDistanceNm() : -1,
                "withinFiveNm", summary.withinFiveNm()
        ));
    }

    // ─────────────────────────── DTO ─────────────────────────────────────────

    public record AircraftDto(
            String icao24, String callsign, String originCountry,
            Double latitude, Double longitude, Double altitudeFt,
            Double groundSpeedKts, Double trackDeg, Double verticalRateFpm,
            boolean onGround, String squawk, Double distanceNm
    ) {
        static AircraftDto from(AdsbAircraft a) {
            return new AircraftDto(
                    a.getIcao24(), a.getCallsign(), a.getOriginCountry(),
                    a.getLatitude(), a.getLongitude(), a.getAltitudeFt(),
                    a.getGroundSpeedKts(), a.getTrackDeg(), a.getVerticalRateFpm(),
                    a.isOnGround(), a.getSquawk(), a.getDistanceNm());
        }
    }
}
