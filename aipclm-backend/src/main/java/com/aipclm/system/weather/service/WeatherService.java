package com.aipclm.system.weather.service;

import com.aipclm.system.weather.model.FlightCategory;
import com.aipclm.system.weather.model.WeatherObservation;
import com.aipclm.system.weather.model.WeatherReportType;
import com.aipclm.system.weather.repository.WeatherObservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches METAR/TAF weather data from the AVWX REST API (https://avwx.rest)
 * and the CheckWX API as fallback. Parses observations into {@link WeatherObservation}
 * entities. Provides a simulated weather generator for offline/demo mode.
 *
 * <p>Set {@code weather.api.key} in application.yml to enable live API calls.
 * When no key is configured, the service generates realistic synthetic weather.</p>
 */
@Service
@Slf4j
public class WeatherService {

    private final WeatherObservationRepository repository;
    private final WebClient avwxClient;
    private final String apiKey;
    private final Duration timeout;
    private final boolean liveMode;

    private static final java.util.Random RANDOM = new java.util.Random();

    public WeatherService(
            WeatherObservationRepository repository,
            WebClient.Builder webClientBuilder,
            @Value("${weather.api.key:}") String apiKey,
            @Value("${weather.api.base-url:https://avwx.rest/api}") String baseUrl,
            @Value("${weather.api.timeout-ms:5000}") long timeoutMs) {
        this.repository = repository;
        this.apiKey = apiKey;
        this.liveMode = apiKey != null && !apiKey.isBlank();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.avwxClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        log.info("[Weather] Service initialized: liveMode={}, baseUrl={}", liveMode, baseUrl);
    }

    /**
     * Fetch the latest METAR for an ICAO code.
     * If live API is configured, calls AVWX; otherwise generates synthetic weather.
     * Result is persisted and returned.
     */
    public WeatherObservation fetchMetar(String icaoCode) {
        String icao = icaoCode.toUpperCase().trim();

        if (liveMode) {
            try {
                return fetchLiveMetar(icao);
            } catch (Exception e) {
                log.warn("[Weather] Live METAR fetch failed for {}: {}. Falling back to synthetic.", icao, e.getMessage());
            }
        }

        return generateSyntheticMetar(icao);
    }

    /**
     * Fetch the latest TAF (forecast) for an ICAO code.
     */
    public WeatherObservation fetchTaf(String icaoCode) {
        String icao = icaoCode.toUpperCase().trim();

        if (liveMode) {
            try {
                return fetchLiveTaf(icao);
            } catch (Exception e) {
                log.warn("[Weather] Live TAF fetch failed for {}: {}. Falling back to synthetic.", icao, e.getMessage());
            }
        }

        return generateSyntheticTaf(icao);
    }

    /**
     * Get the most recent cached observation for a station.
     * Does NOT make an API call — returns whatever is in the database.
     */
    public Optional<WeatherObservation> getLatestCached(String icaoCode) {
        return repository.findTopByIcaoCodeAndReportTypeOrderByObservedAtDesc(
                icaoCode.toUpperCase(), WeatherReportType.METAR);
    }

    /**
     * Get recent weather history for a station (up to 20 observations).
     */
    public List<WeatherObservation> getRecentHistory(String icaoCode) {
        return repository.findTop20ByIcaoCodeAndReportTypeOrderByObservedAtDesc(
                icaoCode.toUpperCase(), WeatherReportType.METAR);
    }

    /**
     * Computes a weather severity score (0.0 → 1.0) from observation data.
     * This score is used to modulate cognitive load in the simulation pipeline.
     */
    public static double computeWeatherSeverity(WeatherObservation obs) {
        double severity = 0.0;

        // Wind component
        int windSpeed = obs.getWindSpeedKts() != null ? obs.getWindSpeedKts() : 0;
        int gustSpeed = obs.getWindGustKts() != null ? obs.getWindGustKts() : 0;
        severity += Math.min(0.25, windSpeed / 60.0);
        severity += Math.min(0.10, (gustSpeed - windSpeed) / 40.0);

        // Visibility component
        double vis = obs.getVisibilitySm() != null ? obs.getVisibilitySm() : 10.0;
        if (vis < 1.0) severity += 0.25;
        else if (vis < 3.0) severity += 0.15;
        else if (vis < 5.0) severity += 0.08;

        // Ceiling component
        int ceiling = obs.getCeilingFt() != null ? obs.getCeilingFt() : 99999;
        if (ceiling < 200) severity += 0.20;
        else if (ceiling < 500) severity += 0.15;
        else if (ceiling < 1000) severity += 0.10;
        else if (ceiling < 3000) severity += 0.05;

        // Hazards
        if (obs.isThunderstormReported()) severity += 0.20;
        if (obs.isIcingReported()) severity += 0.15;
        if (obs.isWindShearReported()) severity += 0.15;

        // Precipitation
        if (obs.getPrecipitation() != null) {
            String precip = obs.getPrecipitation().toUpperCase();
            if (precip.contains("TS")) severity += 0.10;
            else if (precip.contains("SN") || precip.contains("FZRA")) severity += 0.12;
            else if (precip.contains("RA")) severity += 0.05;
        }

        return Math.min(1.0, severity);
    }

    // ──────────── Live API calls ────────────

    @SuppressWarnings("unchecked")
    private WeatherObservation fetchLiveMetar(String icao) {
        log.info("[Weather] Fetching live METAR for {}", icao);

        Map<String, Object> response = avwxClient.get()
                .uri("/metar/{icao}?options=info,translate", icao)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .block();

        if (response == null) throw new IllegalStateException("Null METAR response for " + icao);

        WeatherObservation obs = parseAvwxMetar(response, icao);
        obs.setWeatherSeverityScore(computeWeatherSeverity(obs));
        return repository.save(obs);
    }

    @SuppressWarnings("unchecked")
    private WeatherObservation fetchLiveTaf(String icao) {
        log.info("[Weather] Fetching live TAF for {}", icao);

        Map<String, Object> response = avwxClient.get()
                .uri("/taf/{icao}", icao)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .block();

        if (response == null) throw new IllegalStateException("Null TAF response for " + icao);

        WeatherObservation obs = WeatherObservation.builder()
                .icaoCode(icao)
                .reportType(WeatherReportType.TAF)
                .rawText((String) response.getOrDefault("raw", ""))
                .observedAt(Instant.now())
                .build();

        obs.setWeatherSeverityScore(0.0); // TAF severity computed differently
        return repository.save(obs);
    }

    @SuppressWarnings("unchecked")
    private WeatherObservation parseAvwxMetar(Map<String, Object> data, String icao) {
        var builder = WeatherObservation.builder()
                .icaoCode(icao)
                .reportType(WeatherReportType.METAR)
                .rawText((String) data.getOrDefault("raw", ""))
                .observedAt(Instant.now());

        // Temperature
        Map<String, Object> temp = (Map<String, Object>) data.get("temperature");
        if (temp != null && temp.get("value") != null) {
            builder.temperatureC(((Number) temp.get("value")).doubleValue());
        }

        // Dewpoint
        Map<String, Object> dew = (Map<String, Object>) data.get("dewpoint");
        if (dew != null && dew.get("value") != null) {
            builder.dewpointC(((Number) dew.get("value")).doubleValue());
        }

        // Wind
        Map<String, Object> windDir = (Map<String, Object>) data.get("wind_direction");
        if (windDir != null && windDir.get("value") != null) {
            builder.windDirectionDeg(((Number) windDir.get("value")).intValue());
        }
        Map<String, Object> windSpd = (Map<String, Object>) data.get("wind_speed");
        if (windSpd != null && windSpd.get("value") != null) {
            builder.windSpeedKts(((Number) windSpd.get("value")).intValue());
        }
        Map<String, Object> windGust = (Map<String, Object>) data.get("wind_gust");
        if (windGust != null && windGust.get("value") != null) {
            builder.windGustKts(((Number) windGust.get("value")).intValue());
        }

        // Visibility
        Map<String, Object> vis = (Map<String, Object>) data.get("visibility");
        if (vis != null && vis.get("value") != null) {
            builder.visibilitySm(((Number) vis.get("value")).doubleValue());
        }

        // Altimeter
        Map<String, Object> alt = (Map<String, Object>) data.get("altimeter");
        if (alt != null && alt.get("value") != null) {
            builder.altimeterInhg(((Number) alt.get("value")).doubleValue());
        }

        // Flight category
        String fc = (String) data.get("flight_rules");
        if (fc != null) {
            try {
                builder.flightCategory(FlightCategory.valueOf(fc));
            } catch (IllegalArgumentException ignored) {}
        }

        // Clouds → ceiling
        List<Map<String, Object>> clouds = (List<Map<String, Object>>) data.get("clouds");
        if (clouds != null) {
            for (var cloud : clouds) {
                String type = (String) cloud.get("type");
                if ("BKN".equals(type) || "OVC".equals(type)) {
                    Number alt2 = (Number) cloud.get("altitude");
                    if (alt2 != null) {
                        builder.ceilingFt(alt2.intValue() * 100); // METAR gives hundreds of feet
                        break;
                    }
                }
            }
        }

        // Weather codes
        List<Map<String, Object>> wxCodes = (List<Map<String, Object>>) data.get("wx_codes");
        if (wxCodes != null && !wxCodes.isEmpty()) {
            StringBuilder precipBuilder = new StringBuilder();
            for (var wx : wxCodes) {
                String repr = (String) wx.get("repr");
                if (repr != null) {
                    if (precipBuilder.length() > 0) precipBuilder.append(",");
                    precipBuilder.append(repr);
                    if (repr.contains("TS")) builder.thunderstormReported(true);
                    if (repr.contains("FZ") || repr.contains("IC") || repr.contains("PL"))
                        builder.icingReported(true);
                }
            }
            builder.precipitation(precipBuilder.toString());
        }

        // Remarks — check for wind shear
        String remarks = (String) data.get("remarks");
        if (remarks != null && remarks.contains("WS")) {
            builder.windShearReported(true);
        }

        return builder.build();
    }

    // ──────────── Synthetic Weather Generation ────────────

    /**
     * Generates a realistic METAR observation for demo/offline mode.
     * Produces varying weather conditions to exercise the cognitive load pipeline.
     */
    public WeatherObservation generateSyntheticMetar(String icao) {
        // Weighted random weather scenario
        double roll = RANDOM.nextDouble();
        SyntheticWeatherProfile profile;
        if (roll < 0.35) profile = SyntheticWeatherProfile.CLEAR;
        else if (roll < 0.55) profile = SyntheticWeatherProfile.MARGINAL;
        else if (roll < 0.75) profile = SyntheticWeatherProfile.IFR;
        else if (roll < 0.88) profile = SyntheticWeatherProfile.STORMY;
        else profile = SyntheticWeatherProfile.SEVERE;

        int windDir = RANDOM.nextInt(360);
        int windSpeed = profile.baseWindKts + RANDOM.nextInt(profile.windVarianceKts + 1);
        Integer gustSpeed = profile.gustProbability() > RANDOM.nextDouble()
                ? windSpeed + 5 + RANDOM.nextInt(15) : null;
        double visibility = Math.max(0.25, profile.baseVisibilitySm + RANDOM.nextGaussian() * profile.visVariance);
        int ceiling = Math.max(100, profile.baseCeilingFt + (int) (RANDOM.nextGaussian() * profile.ceilingVariance));
        double temp = 15.0 + RANDOM.nextGaussian() * 10;
        double dew = temp - 2 - Math.abs(RANDOM.nextGaussian() * 5);

        FlightCategory fc;
        if (ceiling > 3000 && visibility > 5) fc = FlightCategory.VFR;
        else if (ceiling >= 1000 && visibility >= 3) fc = FlightCategory.MVFR;
        else if (ceiling >= 500 && visibility >= 1) fc = FlightCategory.IFR;
        else fc = FlightCategory.LIFR;

        String rawMetar = String.format(
                "%s %s%03dKT %sSM %s %02d/%02d A%04d",
                icao,
                String.format("%03d%02d", windDir, windSpeed),
                gustSpeed != null ? gustSpeed : 0,
                visibility >= 10 ? "10" : String.format("%.1f", visibility),
                profile.cloudCover,
                Math.round(temp), Math.round(dew),
                (int) (profile.altimeter * 100));

        WeatherObservation obs = WeatherObservation.builder()
                .icaoCode(icao)
                .reportType(WeatherReportType.METAR)
                .rawText(rawMetar)
                .temperatureC(Math.round(temp * 10) / 10.0)
                .dewpointC(Math.round(dew * 10) / 10.0)
                .windDirectionDeg(windDir)
                .windSpeedKts(windSpeed)
                .windGustKts(gustSpeed)
                .visibilitySm(Math.round(visibility * 10) / 10.0)
                .altimeterInhg(profile.altimeter)
                .ceilingFt(ceiling > 99000 ? null : ceiling)
                .flightCategory(fc)
                .precipitation(profile.precipitation)
                .icingReported(profile.icing)
                .windShearReported(profile.windShear)
                .thunderstormReported(profile.thunderstorm)
                .observedAt(Instant.now())
                .build();

        obs.setWeatherSeverityScore(computeWeatherSeverity(obs));

        log.info("[Weather] Synthetic METAR for {}: category={} wind={}@{}kt severity={:.2f}",
                icao, fc, windDir, windSpeed, obs.getWeatherSeverityScore());

        return repository.save(obs);
    }

    private WeatherObservation generateSyntheticTaf(String icao) {
        WeatherObservation obs = WeatherObservation.builder()
                .icaoCode(icao)
                .reportType(WeatherReportType.TAF)
                .rawText("TAF " + icao + " SYNTHETIC FORECAST")
                .observedAt(Instant.now())
                .build();
        return repository.save(obs);
    }

    private enum SyntheticWeatherProfile {
        CLEAR(5, 5, 10.0, 1.0, 99999, 500, false, false, false, null, "SKC", 29.92),
        MARGINAL(10, 8, 4.0, 1.5, 2500, 800, false, false, false, null, "BKN025", 29.85),
        IFR(15, 10, 1.5, 0.5, 700, 200, false, false, false, "RA", "OVC007", 29.75),
        STORMY(25, 15, 2.0, 1.0, 1200, 500, false, true, true, "TSRA", "BKN012CB", 29.60),
        SEVERE(35, 20, 0.5, 0.3, 300, 150, true, true, true, "+TSRA", "OVC003CB", 29.40);

        final int baseWindKts, windVarianceKts;
        final double baseVisibilitySm, visVariance;
        final int baseCeilingFt, ceilingVariance;
        final boolean icing, windShear, thunderstorm;
        final String precipitation, cloudCover;
        final double altimeter;

        SyntheticWeatherProfile(int baseWind, int windVar, double baseVis, double visVar,
                                 int baseCeiling, int ceilingVar,
                                 boolean icing, boolean windShear, boolean ts,
                                 String precip, String cloud, double altimeter) {
            this.baseWindKts = baseWind; this.windVarianceKts = windVar;
            this.baseVisibilitySm = baseVis; this.visVariance = visVar;
            this.baseCeilingFt = baseCeiling; this.ceilingVariance = ceilingVar;
            this.icing = icing; this.windShear = windShear; this.thunderstorm = ts;
            this.precipitation = precip; this.cloudCover = cloud; this.altimeter = altimeter;
        }

        double gustProbability() { return this == CLEAR ? 0.05 : this == MARGINAL ? 0.2 : 0.5; }
    }
}
