package com.heartratemonitor.heartratemonitor.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Clase utilizada para analizar datos de variabilidad de la frecuencia cardíaca (HRV)
 */
public class HRVAnalyzer {
    
    private List<Integer> rrIntervals;
    
    public HRVAnalyzer() {
        rrIntervals = new ArrayList<>();
    }
    
    /**
     * Añade un intervalo RR a la lista para análisis
     * @param rrInterval Intervalo RR en milisegundos
     */
    public void addRRInterval(int rrInterval) {
        if (rrInterval > 0) {
            rrIntervals.add(rrInterval);
        }
    }
    
    /**
     * Limpia todos los intervalos RR almacenados
     */
    public void clearRRIntervals() {
        rrIntervals.clear();
    }
    
    /**
     * Calcula el SDNN (desviación estándar de los intervalos NN)
     * @return SDNN en milisegundos o 0 si no hay suficientes datos
     */
    public double calculateSDNN() {
        if (rrIntervals.size() < 2) {
            return 0;
        }
        
        double mean = calculateMeanRR();
        double sumSquaredDiff = 0;
        
        for (int rr : rrIntervals) {
            double diff = rr - mean;
            sumSquaredDiff += diff * diff;
        }
        
        return Math.sqrt(sumSquaredDiff / (rrIntervals.size() - 1));
    }
    
    /**
     * Calcula el SDNN (desviación estándar de los intervalos NN) para un array de datos
     * @param rrData Array de intervalos RR en milisegundos
     * @return SDNN en milisegundos o 0 si no hay suficientes datos
     */
    public double calculateSDNN(double[] rrData) {
        if (rrData == null || rrData.length < 2) {
            return 0;
        }
        
        double mean = calculateMean(rrData);
        double sumSquaredDiff = 0;
        
        for (double rr : rrData) {
            double diff = rr - mean;
            sumSquaredDiff += diff * diff;
        }
        
        return Math.sqrt(sumSquaredDiff / (rrData.length - 1));
    }
    
    /**
     * Calcula el RMSSD (raíz cuadrada del promedio de las diferencias al cuadrado de intervalos RR sucesivos)
     * @return RMSSD en milisegundos o 0 si no hay suficientes datos
     */
    public double calculateRMSSD() {
        if (rrIntervals.size() < 2) {
            return 0;
        }
        
        double sumSquaredDiff = 0;
        int count = 0;
        
        for (int i = 1; i < rrIntervals.size(); i++) {
            int diff = rrIntervals.get(i) - rrIntervals.get(i - 1);
            sumSquaredDiff += diff * diff;
            count++;
        }
        
        return Math.sqrt(sumSquaredDiff / count);
    }
    
    /**
     * Calcula el RMSSD (raíz cuadrada del promedio de las diferencias al cuadrado de intervalos RR sucesivos) para un array de datos
     * @param rrData Array de intervalos RR en milisegundos
     * @return RMSSD en milisegundos o 0 si no hay suficientes datos
     */
    public double calculateRMSSD(double[] rrData) {
        if (rrData == null || rrData.length < 2) {
            return 0;
        }
        
        double sumSquaredDiff = 0;
        int count = 0;
        
        for (int i = 1; i < rrData.length; i++) {
            double diff = rrData[i] - rrData[i - 1];
            sumSquaredDiff += diff * diff;
            count++;
        }
        
        return Math.sqrt(sumSquaredDiff / count);
    }
    
    /**
     * Calcula el promedio de los intervalos RR
     * @return Promedio en milisegundos o 0 si no hay datos
     */
    public double calculateMeanRR() {
        if (rrIntervals.isEmpty()) {
            return 0;
        }
        
        double sum = 0;
        for (int rr : rrIntervals) {
            sum += rr;
        }
        
        return sum / rrIntervals.size();
    }
    
    /**
     * Calcula el promedio de los intervalos RR para un array de datos
     * @param rrData Array de intervalos RR en milisegundos
     * @return Promedio en milisegundos o 0 si no hay datos
     */
    private double calculateMean(double[] rrData) {
        if (rrData == null || rrData.length == 0) {
            return 0;
        }
        
        double sum = 0;
        for (double rr : rrData) {
            sum += rr;
        }
        
        return sum / rrData.length;
    }
    
    /**
     * Calcula el pNN50 (porcentaje de intervalos RR sucesivos que difieren en más de 50ms)
     * @return pNN50 como un porcentaje (0-100) o 0 si no hay suficientes datos
     */
    public double calculatePNN50() {
        if (rrIntervals.size() < 2) {
            return 0;
        }
        
        int nn50Count = 0;
        
        for (int i = 1; i < rrIntervals.size(); i++) {
            int diff = Math.abs(rrIntervals.get(i) - rrIntervals.get(i - 1));
            if (diff > 50) {
                nn50Count++;
            }
        }
        
        return (100.0 * nn50Count) / (rrIntervals.size() - 1);
    }
    
    /**
     * Calcula el pNN50 (porcentaje de intervalos RR sucesivos que difieren en más de 50ms) para un array de datos
     * @param rrData Array de intervalos RR en milisegundos
     * @return pNN50 como un porcentaje (0-100) o 0 si no hay suficientes datos
     */
    public double calculatePNN50(double[] rrData) {
        if (rrData == null || rrData.length < 2) {
            return 0;
        }
        
        int nn50Count = 0;
        
        for (int i = 1; i < rrData.length; i++) {
            double diff = Math.abs(rrData[i] - rrData[i - 1]);
            if (diff > 50) {
                nn50Count++;
            }
        }
        
        return (100.0 * nn50Count) / (rrData.length - 1);
    }
    
    /**
     * Calcula la potencia en la banda de baja frecuencia (LF: 0.04-0.15 Hz)
     * @param rrData Array de intervalos RR en milisegundos
     * @return Potencia en la banda LF o 0 si no hay suficientes datos
     */
    public double calculateLF(double[] rrData) {
        if (rrData == null || rrData.length < 10) {
            return 0;
        }
        
        // Implementación simplificada de la potencia LF
        // En una implementación real se requeriría un análisis espectral (FFT)
        double sum = 0;
        for (double rr : rrData) {
            // Simulamos un valor basado en la variabilidad
            sum += Math.pow(rr / 1000.0, 2);
        }
        
        return sum * 0.5; // Aproximación simplificada
    }
    
    /**
     * Calcula la potencia en la banda de alta frecuencia (HF: 0.15-0.4 Hz)
     * @param rrData Array de intervalos RR en milisegundos
     * @return Potencia en la banda HF o 0 si no hay suficientes datos
     */
    public double calculateHF(double[] rrData) {
        if (rrData == null || rrData.length < 10) {
            return 0;
        }
        
        // Implementación simplificada de la potencia HF
        // En una implementación real se requeriría un análisis espectral (FFT)
        double sum = 0;
        for (double rr : rrData) {
            // Simulamos un valor basado en la variabilidad
            sum += Math.pow(rr / 1000.0, 2);
        }
        
        return sum * 0.3; // Aproximación simplificada
    }
    
    /**
     * Calcula la relación entre la potencia de baja frecuencia y alta frecuencia (LF/HF)
     * @param rrData Array de intervalos RR en milisegundos
     * @return Relación LF/HF o 1.0 si no hay suficientes datos
     */
    public double calculateLFHFRatio(double[] rrData) {
        if (rrData == null || rrData.length < 10) {
            return 1.0; // Valor neutral por defecto
        }
        
        double lf = calculateLF(rrData);
        double hf = calculateHF(rrData);
        
        // Evitar división por cero
        if (hf == 0) {
            return 1.0;
        }
        
        return lf / hf;
    }
    
    /**
     * Obtiene una puntuación simple de HRV basada en el RMSSD
     * @return Puntuación de HRV (mayor = mejor) o 0 si no hay suficientes datos
     */
    public int getHRVScore() {
        double rmssd = calculateRMSSD();
        
        if (rmssd == 0) {
            return 0;
        }
        
        // Valores típicos de RMSSD: 15-40 para adultos sanos
        // Convertimos a una escala de 0-100
        if (rmssd < 10) {
            return (int)(rmssd * 3);
        } else if (rmssd < 30) {
            return 30 + (int)((rmssd - 10) * 2);
        } else if (rmssd < 60) {
            return 70 + (int)((rmssd - 30));
        } else {
            return 100;
        }
    }
    
    /**
     * Calcula una puntuación de HRV basada en RMSSD para un array de datos
     * @param rrData Array de intervalos RR en milisegundos
     * @return Puntuación de HRV (mayor = mejor) o 0 si no hay suficientes datos
     */
    public int calculateHRVScore(double[] rrData) {
        double rmssd = calculateRMSSD(rrData);
        
        if (rmssd == 0) {
            return 0;
        }
        
        // Valores típicos de RMSSD: 15-40 para adultos sanos
        // Convertimos a una escala de 0-100
        if (rmssd < 10) {
            return (int)(rmssd * 3);
        } else if (rmssd < 30) {
            return 30 + (int)((rmssd - 10) * 2);
        } else if (rmssd < 60) {
            return 70 + (int)((rmssd - 30));
        } else {
            return 100;
        }
    }
    
    /**
     * Devuelve el número de muestras de intervalos RR registrados
     * @return Número de muestras
     */
    public int getSampleCount() {
        return rrIntervals.size();
    }
}
