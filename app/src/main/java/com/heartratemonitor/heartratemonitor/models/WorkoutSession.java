package com.heartratemonitor.heartratemonitor.models;

/**
 * Representa una sesión de entrenamiento con datos de frecuencia cardíaca
 */
public class WorkoutSession {
    private long id;
    private String title;
    private long startTime;
    private long endTime;
    private String activityType;
    private int averageHeartRate;
    private int maxHeartRate;
    private int caloriesBurned;
    
    // Minutos en cada zona cardíaca
    private int timeInZone1;
    private int timeInZone2;
    private int timeInZone3;
    private int timeInZone4;
    private int timeInZone5;
    
    // Datos HRV
    private double sdnn;
    private double rmssd;
    private double pnn50;
    private double lfhfRatio;
    private int hrvScore; // Puntuación general HRV (0-100)

    // Constructor vacío
    public WorkoutSession() {
    }

    // Constructor completo
    public WorkoutSession(long id, String title, long startTime, long endTime, String activityType,
                         int averageHeartRate, int maxHeartRate, int caloriesBurned) {
        this.id = id;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.activityType = activityType;
        this.averageHeartRate = averageHeartRate;
        this.maxHeartRate = maxHeartRate;
        this.caloriesBurned = caloriesBurned;
    }

    // Getters y Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public int getAverageHeartRate() {
        return averageHeartRate;
    }

    public void setAverageHeartRate(int averageHeartRate) {
        this.averageHeartRate = averageHeartRate;
    }

    public int getMaxHeartRate() {
        return maxHeartRate;
    }

    public void setMaxHeartRate(int maxHeartRate) {
        this.maxHeartRate = maxHeartRate;
    }

    public int getCaloriesBurned() {
        return caloriesBurned;
    }

    public void setCaloriesBurned(int caloriesBurned) {
        this.caloriesBurned = caloriesBurned;
    }

    public int getTimeInZone1() {
        return timeInZone1;
    }

    public void setTimeInZone1(int timeInZone1) {
        this.timeInZone1 = timeInZone1;
    }

    public int getTimeInZone2() {
        return timeInZone2;
    }

    public void setTimeInZone2(int timeInZone2) {
        this.timeInZone2 = timeInZone2;
    }

    public int getTimeInZone3() {
        return timeInZone3;
    }

    public void setTimeInZone3(int timeInZone3) {
        this.timeInZone3 = timeInZone3;
    }

    public int getTimeInZone4() {
        return timeInZone4;
    }

    public void setTimeInZone4(int timeInZone4) {
        this.timeInZone4 = timeInZone4;
    }

    public int getTimeInZone5() {
        return timeInZone5;
    }

    public void setTimeInZone5(int timeInZone5) {
        this.timeInZone5 = timeInZone5;
    }

    public double getSdnn() {
        return sdnn;
    }

    public void setSdnn(double sdnn) {
        this.sdnn = sdnn;
    }

    public double getRmssd() {
        return rmssd;
    }

    public void setRmssd(double rmssd) {
        this.rmssd = rmssd;
    }

    public double getPnn50() {
        return pnn50;
    }

    public void setPnn50(double pnn50) {
        this.pnn50 = pnn50;
    }

    public double getLfhfRatio() {
        return lfhfRatio;
    }

    public void setLfhfRatio(double lfhfRatio) {
        this.lfhfRatio = lfhfRatio;
    }

    public int getHrvScore() {
        return hrvScore;
    }

    public void setHrvScore(int hrvScore) {
        this.hrvScore = hrvScore;
    }

    /**
     * Calcula la duración de la sesión en minutos
     * @return Duración en minutos
     */
    public int getDurationMinutes() {
        if (endTime <= startTime) {
            return 0;
        }
        return (int) ((endTime - startTime) / 60000);
    }
} 