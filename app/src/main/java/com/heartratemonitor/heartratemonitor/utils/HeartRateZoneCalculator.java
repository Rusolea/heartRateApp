package com.heartratemonitor.heartratemonitor.utils;

/**
 * Utilidad para calcular zonas de frecuencia cardíaca
 */
public class HeartRateZoneCalculator {

    // Métodos de cálculo de zonas
    public static final int METHOD_KARVONEN = 0;
    public static final int METHOD_PERCENTAGE = 1;

    /**
     * Calcula la frecuencia cardíaca máxima estimada según la edad
     * @param age Edad en años
     * @return Frecuencia cardíaca máxima estimada
     */
    public static int calculateMaxHeartRate(int age) {
        return 220 - age;
    }
    
    /**
     * Determina la zona de frecuencia cardíaca (1-5) basado en la 
     * frecuencia cardíaca actual y la máxima
     * @param heartRate Frecuencia cardíaca actual
     * @param maxHeartRate Frecuencia cardíaca máxima
     * @return Zona (1-5)
     */
    public static int calculateZone(int heartRate, int maxHeartRate) {
        float percentage = (float) heartRate / maxHeartRate * 100;
        
        if (percentage < 60) {
            return 1; // Zona 1: <60% - Muy ligera
        } else if (percentage < 70) {
            return 2; // Zona 2: 60-70% - Ligera
        } else if (percentage < 80) {
            return 3; // Zona 3: 70-80% - Moderada
        } else if (percentage < 90) {
            return 4; // Zona 4: 80-90% - Fuerte
        } else {
            return 5; // Zona 5: 90-100% - Máxima
        }
    }
    
    /**
     * Calcula las calorías quemadas basado en la frecuencia cardíaca, peso y duración
     * @param heartRate Frecuencia cardíaca promedio
     * @param weightKg Peso en kg
     * @param durationMinutes Duración en minutos
     * @param gender Género ('M' para masculino, 'F' para femenino)
     * @return Calorías estimadas
     */
    public static int calculateCalories(int heartRate, float weightKg, int durationMinutes, char gender) {
        // Fórmula basada en estudios que relacionan frecuencia cardíaca con gasto calórico
        // Adaptada de varios estudios de fisiología del ejercicio
        float caloriesPerMinute;
        
        if (gender == 'M') {
            // Para hombres
            caloriesPerMinute = (float) ((0.6309 * heartRate + 0.1988 * weightKg + 0.2017 * 30 - 55.0969) / 4.184);
        } else {
            // Para mujeres
            caloriesPerMinute = (float) ((0.4472 * heartRate + 0.1263 * weightKg + 0.074 * 30 - 20.4022) / 4.184);
        }
        
        // Prevenir valores negativos para frecuencias cardíacas muy bajas
        if (caloriesPerMinute <= 0) {
            caloriesPerMinute = 1.0f;
        }
        
        return Math.round(caloriesPerMinute * durationMinutes);
    }
    
    /**
     * Versión simplificada para calcular calorías sin considerar género
     */
    public static int calculateCalories(int heartRate, float weightKg, int durationMinutes) {
        // Usar aproximación genérica si no se proporciona género
        float caloriesPerMinute = (float) ((0.55 * heartRate + 0.16 * weightKg - 30) / 4.184);
        
        // Prevenir valores negativos
        if (caloriesPerMinute <= 0) {
            caloriesPerMinute = 1.0f;
        }
        
        return Math.round(caloriesPerMinute * durationMinutes);
    }
} 