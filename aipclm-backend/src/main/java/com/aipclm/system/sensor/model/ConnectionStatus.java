package com.aipclm.system.sensor.model;

/**
 * Connection lifecycle of a wearable sensor device.
 */
public enum ConnectionStatus {
    DISCONNECTED,
    CALIBRATING,
    CONNECTED,
    ERROR
}
