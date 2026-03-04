package com.aipclm.system.sensor.model;

/**
 * Types of wearable / physiological sensors supported by AI-PCLM.
 */
public enum SensorType {
    /** Chest-strap or optical heart-rate monitor (Garmin HRM-Pro, Polar H10). */
    HEART_RATE_MONITOR,
    /** Consumer EEG headband (Muse 2, NeuroSky MindWave). */
    EEG_HEADBAND,
    /** Eye-tracking device (Tobii Pro Nano, Pupil Labs). */
    EYE_TRACKER,
    /** Galvanic skin response / electro-dermal activity (Shimmer3 GSR+, Empatica E4). */
    GSR_SENSOR,
    /** Finger-tip / wrist pulse oximeter (Masimo MightySat, Nonin). */
    PULSE_OXIMETER,
    /** Skin-temperature sensor (Empatica E4 wrist band). */
    SKIN_TEMPERATURE_SENSOR
}
