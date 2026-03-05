package com.aipclm.system.weather.repository;

import com.aipclm.system.weather.model.WeatherObservation;
import com.aipclm.system.weather.model.WeatherReportType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeatherObservationRepository extends JpaRepository<WeatherObservation, UUID> {

    /** Latest observation of a given type for an ICAO code */
    Optional<WeatherObservation> findTopByIcaoCodeAndReportTypeOrderByObservedAtDesc(
            String icaoCode, WeatherReportType reportType);

    /** All observations for a station, newest first */
    List<WeatherObservation> findByIcaoCodeOrderByObservedAtDesc(String icaoCode);

    /** Last N observations for a station */
    List<WeatherObservation> findTop20ByIcaoCodeAndReportTypeOrderByObservedAtDesc(
            String icaoCode, WeatherReportType reportType);
}
