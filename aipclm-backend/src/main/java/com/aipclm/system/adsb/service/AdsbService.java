package com.aipclm.system.adsb.service;

import com.aipclm.system.adsb.model.AdsbAircraft;
import com.aipclm.system.adsb.repository.AdsbAircraftRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service for ingesting ADS-B aircraft state vectors from the OpenSky Network
 * REST API, or generating synthetic traffic for simulation mode.
 *
 * <p>OpenSky API: <a href="https://opensky-network.org/apidoc/rest.html">docs</a></p>
 *
 * <p>The API is free for non-commercial use and returns state vectors within a
 * bounding box. When no API credentials are configured, synthetic traffic is
 * generated.</p>
 */
@Service
@Slf4j
public class AdsbService {

    private final AdsbAircraftRepository repository;
    private final WebClient openSkyClient;
    private final boolean liveMode;
    private final int timeoutMs;

    /** Search radius in degrees (~1° lat ≈ 60 NM) */
    private static final double SEARCH_RADIUS_DEG = 1.5;
    private static final double DEG_TO_NM = 60.0;

    public AdsbService(AdsbAircraftRepository repository,
                       @Value("${adsb.api.base-url:https://opensky-network.org/api}") String baseUrl,
                       @Value("${adsb.api.username:}") String username,
                       @Value("${adsb.api.password:}") String password,
                       @Value("${adsb.api.timeout-ms:8000}") int timeoutMs) {
        this.repository = repository;
        this.timeoutMs = timeoutMs;

        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            this.liveMode = true;
            builder.defaultHeaders(h -> h.setBasicAuth(username, password));
            log.info("ADS-B service initialised — LIVE mode (OpenSky authenticated)");
        } else {
            // Anonymous access to OpenSky is rate-limited but still works
            this.liveMode = false;
            log.info("ADS-B service initialised — SYNTHETIC mode (no OpenSky credentials)");
        }
        this.openSkyClient = builder.build();
    }

    // ────────────────────────────── Public API ──────────────────────────────

    /**
     * Fetch aircraft near a reference point (lat/lon). Uses OpenSky if the
     * API call succeeds; falls back to synthetic traffic otherwise.
     */
    public List<AdsbAircraft> fetchNearbyAircraft(double lat, double lon) {
        if (liveMode) {
            try {
                List<AdsbAircraft> live = fetchFromOpenSky(lat, lon);
                if (!live.isEmpty()) {
                    repository.saveAll(live);
                    return live;
                }
            } catch (Exception e) {
                log.warn("OpenSky API call failed, falling back to synthetic: {}", e.getMessage());
            }
        }
        List<AdsbAircraft> synthetic = generateSyntheticTraffic(lat, lon);
        repository.saveAll(synthetic);
        return synthetic;
    }

    /**
     * Return the latest cached aircraft for a reference point.
     */
    public List<AdsbAircraft> getLatestCached(double lat, double lon) {
        Instant since = Instant.now().minusSeconds(300);
        return repository.findNearbyAircraft(lat, lon, since);
    }

    /**
     * Return a traffic summary for the given reference point.
     */
    public TrafficSummary getTrafficSummary(double lat, double lon) {
        Instant since = Instant.now().minusSeconds(300);
        List<AdsbAircraft> nearby = repository.findNearbyAircraft(lat, lon, since);
        if (nearby.isEmpty()) {
            return new TrafficSummary(0, null, 0);
        }
        AdsbAircraft closest = nearby.get(0);
        long withinFiveNm = nearby.stream()
                .filter(a -> a.getDistanceNm() != null && a.getDistanceNm() <= 5.0)
                .count();
        return new TrafficSummary(nearby.size(),
                closest.getDistanceNm(),
                (int) withinFiveNm);
    }

    /**
     * Purge ADS-B records older than the specified number of hours.
     */
    public void purgeOldRecords(int hoursOld) {
        Instant cutoff = Instant.now().minus(Duration.ofHours(hoursOld));
        repository.deleteByObservedAtBefore(cutoff);
        log.info("Purged ADS-B records older than {} hours", hoursOld);
    }

    // ────────────────────────────── OpenSky API ─────────────────────────────

    private List<AdsbAircraft> fetchFromOpenSky(double lat, double lon) {
        double lamin = lat - SEARCH_RADIUS_DEG;
        double lamax = lat + SEARCH_RADIUS_DEG;
        double lomin = lon - SEARCH_RADIUS_DEG;
        double lomax = lon + SEARCH_RADIUS_DEG;

        JsonNode response = openSkyClient.get()
                .uri(u -> u.path("/states/all")
                        .queryParam("lamin", lamin)
                        .queryParam("lamax", lamax)
                        .queryParam("lomin", lomin)
                        .queryParam("lomax", lomax)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .onErrorResume(e -> Mono.empty())
                .block();

        List<AdsbAircraft> result = new ArrayList<>();
        if (response == null || !response.has("states") || response.get("states").isNull()) {
            return result;
        }

        JsonNode states = response.get("states");
        Instant now = Instant.now();

        for (JsonNode sv : states) {
            if (!sv.isArray() || sv.size() < 17) continue;

            String icao24 = textOrNull(sv.get(0));
            String callsign = textOrNull(sv.get(1));
            String country = textOrNull(sv.get(2));
            Double acLon = doubleOrNull(sv.get(5));
            Double acLat = doubleOrNull(sv.get(6));
            Double baroAlt = doubleOrNull(sv.get(7));  // metres
            boolean onGround = sv.get(8).asBoolean(false);
            Double velocity = doubleOrNull(sv.get(9));   // m/s
            Double heading = doubleOrNull(sv.get(10));
            Double vertRate = doubleOrNull(sv.get(11));  // m/s
            String squawk = textOrNull(sv.get(14));

            if (icao24 == null || acLat == null || acLon == null) continue;

            double distNm = haversineNm(lat, lon, acLat, acLon);

            AdsbAircraft ac = AdsbAircraft.builder()
                    .icao24(icao24.trim())
                    .callsign(callsign != null ? callsign.trim() : null)
                    .originCountry(country)
                    .latitude(acLat)
                    .longitude(acLon)
                    .altitudeFt(baroAlt != null ? baroAlt * 3.28084 : null)
                    .groundSpeedKts(velocity != null ? velocity * 1.94384 : null)
                    .trackDeg(heading)
                    .verticalRateFpm(vertRate != null ? vertRate * 196.85 : null)
                    .onGround(onGround)
                    .squawk(squawk)
                    .distanceNm(distNm)
                    .referenceLat(lat)
                    .referenceLon(lon)
                    .observedAt(now)
                    .build();
            result.add(ac);
        }

        log.info("OpenSky returned {} aircraft near ({}, {})", result.size(), lat, lon);
        return result;
    }

    // ──────────────────────── Synthetic Traffic Generator ────────────────────

    private static final String[] SYNTHETIC_CALLSIGNS = {
            "UAL123", "DAL456", "AAL789", "SWA321", "JBU654",
            "FDX987", "UPS112", "SKW233", "ENY344", "RPA455",
            "ASA566", "HAL677", "NKS788", "FFT899", "EJA100"
    };

    private List<AdsbAircraft> generateSyntheticTraffic(double lat, double lon) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int count = rng.nextInt(3, 12); // 3-11 aircraft nearby
        List<AdsbAircraft> traffic = new ArrayList<>(count);
        Instant now = Instant.now();

        for (int i = 0; i < count; i++) {
            double bearingRad = rng.nextDouble(0, 2 * Math.PI);
            double distNm = rng.nextDouble(0.5, 50.0);
            double distDeg = distNm / DEG_TO_NM;

            double acLat = lat + distDeg * Math.cos(bearingRad);
            double acLon = lon + distDeg * Math.sin(bearingRad) / Math.cos(Math.toRadians(lat));

            // Generate realistic parameters
            boolean onGround = rng.nextDouble() < 0.05; // 5% on ground
            double altFt = onGround ? 0 : rng.nextDouble(1000, 41000);
            double gsKts = onGround ? rng.nextDouble(0, 30) : rng.nextDouble(150, 520);
            double heading = rng.nextDouble(0, 360);
            double vRateFpm = onGround ? 0 : rng.nextDouble(-2000, 2000);

            String callsign = SYNTHETIC_CALLSIGNS[rng.nextInt(SYNTHETIC_CALLSIGNS.length)];
            String icao24 = String.format("%06x", rng.nextInt(0, 0xFFFFFF));

            // Occasional emergency squawk
            String squawk = rng.nextDouble() < 0.02 ? "7700" : String.format("%04d", rng.nextInt(1, 7777));

            AdsbAircraft ac = AdsbAircraft.builder()
                    .icao24(icao24)
                    .callsign(callsign)
                    .originCountry("United States")
                    .latitude(acLat)
                    .longitude(acLon)
                    .altitudeFt(altFt)
                    .groundSpeedKts(gsKts)
                    .trackDeg(heading)
                    .verticalRateFpm(vRateFpm)
                    .onGround(onGround)
                    .squawk(squawk)
                    .distanceNm(distNm)
                    .referenceLat(lat)
                    .referenceLon(lon)
                    .observedAt(now)
                    .build();
            traffic.add(ac);
        }

        log.debug("Generated {} synthetic ADS-B aircraft near ({}, {})", count, lat, lon);
        return traffic;
    }

    // ──────────────────────────── Geometry helpers ───────────────────────────

    private static double haversineNm(double lat1, double lon1, double lat2, double lon2) {
        double R = 3440.065; // Earth radius in NM
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private static String textOrNull(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asText();
    }

    private static Double doubleOrNull(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asDouble();
    }

    // ──────────────────────────── Inner DTOs ────────────────────────────────

    /**
     * Summary of nearby traffic for quick consumption by the simulation engine.
     */
    public record TrafficSummary(int totalAircraft, Double closestDistanceNm, int withinFiveNm) {}
}
