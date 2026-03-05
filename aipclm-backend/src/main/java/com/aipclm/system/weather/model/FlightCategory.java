package com.aipclm.system.weather.model;

/**
 * FAA flight category based on ceiling and visibility.
 */
public enum FlightCategory {
    /** Visual Flight Rules — ceiling > 3000 ft and visibility > 5 sm */
    VFR,
    /** Marginal VFR — ceiling 1000-3000 ft or visibility 3-5 sm */
    MVFR,
    /** Instrument Flight Rules — ceiling 500-999 ft or visibility 1-3 sm */
    IFR,
    /** Low IFR — ceiling < 500 ft or visibility < 1 sm */
    LIFR
}
