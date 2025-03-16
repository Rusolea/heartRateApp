package com.heartratemonitor.heartratemonitor.models;

/**
 * Representa un punto de datos de frecuencia cardíaca
 */
public class HeartRateData {
    private long id;
    private long sessionId;
    private long timestamp;
    private int heartRate;
    private Integer rrInterval; // Intervalo RR en ms (puede ser null)

    // Constructor vacío
    public HeartRateData() {
    }

    // Constructor para datos de frecuencia cardíaca sin RR
    public HeartRateData(long sessionId, long timestamp, int heartRate) {
        this.sessionId = sessionId;
        this.timestamp = timestamp;
        this.heartRate = heartRate;
    }

    // Constructor completo
    public HeartRateData(long id, long sessionId, long timestamp, int heartRate, Integer rrInterval) {
        this.id = id;
        this.sessionId = sessionId;
        this.timestamp = timestamp;
        this.heartRate = heartRate;
        this.rrInterval = rrInterval;
    }

    // Getters y Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(int heartRate) {
        this.heartRate = heartRate;
    }

    public Integer getRrInterval() {
        return rrInterval;
    }

    public void setRrInterval(Integer rrInterval) {
        this.rrInterval = rrInterval;
    }
} 