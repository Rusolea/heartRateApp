package com.heartratemonitor.heartratemonitor.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.heartratemonitor.heartratemonitor.R;
import com.heartratemonitor.heartratemonitor.views.HeartRateView;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

public class MonitorFragment extends Fragment {
    
    // Interfaz de escucha para comunicación con la actividad
    public interface MonitorFragmentListener {
        void onHeartRateUpdated(int heartRate);
        void onRRIntervalUpdated(int rrInterval);
        void onConnectionStatusChanged(String status);
    }
    
    // Estados de monitoreo
    private static final int STATE_IDLE = 0;
    private static final int STATE_MONITORING = 1;
    private static final int STATE_PAUSED = 2;
    
    private MonitorFragmentListener listener;
    private TextView tvConnectionStatus;
    private TextView tvHeartRate;
    private TextView tvRRInterval;
    private TextView tvMonitoringTime;
    private TextView tvZoneDescription;
    private TextView tvHeartRatePercentage;
    private HeartRateView heartRateIndicator;
    
    private TextView zone1;
    private TextView zone2;
    private TextView zone3;
    private TextView zone4;
    private TextView zone5;
    
    private List<Integer> zones;
    private long monitoringStartTime;
    private long totalMonitoringTime = 0;
    private long pauseStartTime = 0;
    private boolean isMonitoring = false;
    private int monitoringState = STATE_IDLE;
    
    // Variables para el seguimiento del tiempo en zonas
    private long[] zoneTimes = new long[6]; // 0-5 zonas (0 es fuera de zona)
    private int currentZone = 0;
    private long lastZoneUpdateTime = 0;
    
    // Handler para actualizar el tiempo y los porcentajes periódicamente
    private Handler timeHandler = new Handler(Looper.getMainLooper());
    private Runnable timeUpdateRunnable;
    
    // Frecuencia cardíaca máxima (valor por defecto, debería personalizarse según edad y condición del usuario)
    private int maxHeartRate = 220;
    
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            listener = (MonitorFragmentListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " debe implementar MonitorFragmentListener");
        }
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_monitor, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Inicializar vistas con los IDs correctos del layout
        tvConnectionStatus = view.findViewById(R.id.statusText);
        tvHeartRate = view.findViewById(R.id.heartRateText);
        tvHeartRatePercentage = view.findViewById(R.id.heartRatePercentageText);
        tvMonitoringTime = view.findViewById(R.id.monitoringTimeText);
        tvZoneDescription = view.findViewById(R.id.zoneDescriptionText);
        
        // Estas vistas no están en el layout actual, las comentamos por ahora
        // tvRRInterval = view.findViewById(R.id.tvRRInterval);
        // heartRateIndicator = view.findViewById(R.id.heartRateIndicator);
        
        // Inicializar zonas
        zone1 = view.findViewById(R.id.zone1);
        zone2 = view.findViewById(R.id.zone2);
        zone3 = view.findViewById(R.id.zone3);
        zone4 = view.findViewById(R.id.zone4);
        zone5 = view.findViewById(R.id.zone5);
        
        // Inicializar el array de tiempos en zona
        for (int i = 0; i < zoneTimes.length; i++) {
            zoneTimes[i] = 0;
        }
        
        // Configurar el temporizador para actualizar la UI
        timeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMonitoringActive()) {
                    updateMonitoringTime();
                    updateZoneTimes();
                    timeHandler.postDelayed(this, 1000); // Actualizar cada segundo
                }
            }
        };
        
        updateHeartRateZones();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (isMonitoringActive()) {
            startTimeUpdates();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        stopTimeUpdates();
    }
    
    private void startTimeUpdates() {
        stopTimeUpdates(); // Por seguridad, para evitar múltiples runnables
        timeHandler.post(timeUpdateRunnable);
    }
    
    private void stopTimeUpdates() {
        timeHandler.removeCallbacks(timeUpdateRunnable);
    }
    
    private boolean isMonitoringActive() {
        return monitoringStartTime > 0;
    }
    
    // Cambiado a público para que lo pueda usar MainActivity
    public void updateHeartRateZones() {
        // Inicializar zonas de frecuencia cardíaca
        zones = new ArrayList<>();
        // Ejemplo de zonas (los valores pueden ajustarse según configuración)
        zones.add(90);  // Zona 1: 0-90
        zones.add(120); // Zona 2: 91-120
        zones.add(150); // Zona 3: 121-150
        zones.add(170); // Zona 4: 151-170
        // Zona 5: >170
    }
    
    // Método para calcular y mostrar el porcentaje de frecuencia cardíaca máxima
    private void updateHeartRatePercentage(int heartRate) {
        // Calcular el porcentaje de frecuencia cardíaca máxima
        int percentage = (int) (((double) heartRate / maxHeartRate) * 100);
        // Mostrar el porcentaje en el TextView correspondiente
        if (tvHeartRatePercentage != null) {
            tvHeartRatePercentage.setText(String.format("%d%%", percentage));
            
            // Actualizar el color del texto según la zona
            updateHeartRatePercentageColor(heartRate);
        }
    }
    
    // Método para cambiar el color del texto del porcentaje según la zona
    private void updateHeartRatePercentageColor(int heartRate) {
        if (tvHeartRatePercentage == null) return;
        
        int zone = getCurrentZone(heartRate);
        int color;
        
        switch (zone) {
            case 1:
                color = getResources().getColor(R.color.zone1);
                break;
            case 2:
                color = getResources().getColor(R.color.zone2);
                break;
            case 3:
                color = getResources().getColor(R.color.zone3);
                break;
            case 4:
                color = getResources().getColor(R.color.zone4);
                break;
            case 5:
                color = getResources().getColor(R.color.zone5);
                break;
            default:
                color = getResources().getColor(R.color.colorAccent); // Color por defecto para zona 0
                break;
        }
        
        tvHeartRatePercentage.setTextColor(color);
    }

    // Método agregado para controlar el estado de monitoreo
    public void updateMonitoringState(boolean isMonitoring) {
        this.isMonitoring = isMonitoring;
        if (isMonitoring) {
            startMonitoring();
        } else {
            stopMonitoring();
        }
    }

    public void updateConnectionStatus(String status) {
        // Usar runOnUiThread para asegurar que las actualizaciones de UI 
        // se ejecuten en el hilo principal
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (tvConnectionStatus != null) {
                    tvConnectionStatus.setText(status);
                }
                
                if (listener != null) {
                    listener.onConnectionStatusChanged(status);
                }
            });
        }
    }

    public void updateHeartRate(int heartRate) {
        // Usar runOnUiThread para asegurar que las actualizaciones de UI 
        // se ejecuten en el hilo principal
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (tvHeartRate != null) {
                    tvHeartRate.setText(String.valueOf(heartRate));
                    
                    // Actualizar el porcentaje de frecuencia cardíaca máxima
                    updateHeartRatePercentage(heartRate);
                    
                    // Actualizar la zona de frecuencia cardíaca
                    updateHeartRateZone(heartRate);
                    
                    // Si hay un indicador visual, actualizarlo (comentado porque no existe en el layout)
                    // if (heartRateIndicator != null) {
                    //     heartRateIndicator.setValue(heartRate);
                    // }
                    
                    // Actualizar texto de descripción según la zona
                    updateZoneDescription(heartRate);
                    
                    // Actualizar tiempo de monitoreo
                    updateMonitoringTime();
                    
                    // Notificar a la actividad
                    if (listener != null) {
                        listener.onHeartRateUpdated(heartRate);
                    }
                }
            });
        }
    }
    
    // Método para establecer la frecuencia cardíaca máxima
    public void setMaxHeartRate(int maxHeartRate) {
        this.maxHeartRate = maxHeartRate;
    }

    // Método para actualizar la zona de frecuencia cardíaca basada en el valor actual
    private void updateHeartRateZone(int heartRate) {
        if (zones == null || zones.isEmpty()) {
            updateHeartRateZones(); // Inicializar zonas si es necesario
        }
        
        // Resetear todos los indicadores de zona
        if (zone1 != null) zone1.setAlpha(0.5f);
        if (zone2 != null) zone2.setAlpha(0.5f);
        if (zone3 != null) zone3.setAlpha(0.5f);
        if (zone4 != null) zone4.setAlpha(0.5f);
        if (zone5 != null) zone5.setAlpha(0.5f);
        
        // Destacar la zona actual
        int newZone = getCurrentZone(heartRate);
        
        // Si ha cambiado la zona, actualizar tiempos
        if (newZone != currentZone && isMonitoringActive()) {
            long now = System.currentTimeMillis();
            if (lastZoneUpdateTime > 0) {
                zoneTimes[currentZone] += (now - lastZoneUpdateTime);
            }
            lastZoneUpdateTime = now;
            currentZone = newZone;
            
            // Actualizar los porcentajes de tiempo en zona
            updateZoneTimePercentages();
            
            // Actualizar el color del texto del porcentaje
            updateHeartRatePercentageColor(heartRate);
        }
        
        TextView zoneView = null;
        
        switch (newZone) {
            case 1:
                zoneView = zone1;
                break;
            case 2:
                zoneView = zone2;
                break;
            case 3:
                zoneView = zone3;
                break;
            case 4:
                zoneView = zone4;
                break;
            case 5:
                zoneView = zone5;
                break;
        }
        
        if (zoneView != null) {
            zoneView.setAlpha(1.0f);
        }
    }

    // Método para determinar la zona actual basada en el ritmo cardíaco
    private int getCurrentZone(int heartRate) {
        if (zones == null || zones.isEmpty()) {
            return 0;
        }
        
        for (int i = 0; i < zones.size(); i++) {
            if (heartRate <= zones.get(i)) {
                return i + 1;
            }
        }
        
        return 5; // Zona máxima si excede todos los límites
    }

    // Método para actualizar el texto descriptivo según la zona
    private void updateZoneDescription(int heartRate) {
        if (tvZoneDescription == null) return;
        
        int zone = getCurrentZone(heartRate);
        String description = "";
        int color;
        
        switch (zone) {
            case 0:
                description = "Fuera de zona - Actividad muy ligera";
                color = getResources().getColor(R.color.colorAccent); // Color por defecto
                break;
            case 1:
                description = "Zona 1 - Actividad muy ligera (50-60%)";
                color = getResources().getColor(R.color.zone1);
                break;
            case 2:
                description = "Zona 2 - Quema de grasa (60-70%)";
                color = getResources().getColor(R.color.zone2);
                break;
            case 3:
                description = "Zona 3 - Cardio (70-80%)";
                color = getResources().getColor(R.color.zone3);
                break;
            case 4:
                description = "Zona 4 - Rendimiento intenso (80-90%)";
                color = getResources().getColor(R.color.zone4);
                break;
            case 5:
                description = "Zona 5 - Máximo esfuerzo (90-100%)";
                color = getResources().getColor(R.color.zone5);
                break;
            default:
                description = "Sin datos de zona";
                color = getResources().getColor(R.color.colorAccent);
                break;
        }
        
        tvZoneDescription.setText(description);
        tvZoneDescription.setTextColor(color);
    }

    public void updateRRInterval(int rrInterval) {
        // También usar runOnUiThread para los intervalos RR
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                // Comentamos ya que la vista no existe en el layout
                // if (tvRRInterval != null) {
                //     tvRRInterval.setText(String.valueOf(rrInterval) + " ms");
                // }
                
                // Notificar a la actividad
                if (listener != null) {
                    listener.onRRIntervalUpdated(rrInterval);
                }
            });
        }
    }

    // Actualizar los tiempos de zona (llamado por el handler cada segundo)
    private void updateZoneTimes() {
        if (isMonitoringActive() && lastZoneUpdateTime > 0) {
            long now = System.currentTimeMillis();
            zoneTimes[currentZone] += (now - lastZoneUpdateTime);
            lastZoneUpdateTime = now;
            
            // Actualizar porcentajes
            updateZoneTimePercentages();
        }
    }
    
    // Calcular y mostrar los porcentajes de tiempo en cada zona
    private void updateZoneTimePercentages() {
        if (!isMonitoringActive()) return;
        
        long totalTime = 0;
        for (int i = 0; i < zoneTimes.length; i++) {
            totalTime += zoneTimes[i];
        }
        
        if (totalTime > 0) {
            // Calcular porcentajes
            int[] percentages = new int[zoneTimes.length];
            for (int i = 0; i < zoneTimes.length; i++) {
                percentages[i] = (int) ((zoneTimes[i] * 100) / totalTime);
            }
            
            // Actualizar textos
            if (zone1 != null) zone1.setText(String.format("Z1\n%d%%", percentages[1]));
            if (zone2 != null) zone2.setText(String.format("Z2\n%d%%", percentages[2]));
            if (zone3 != null) zone3.setText(String.format("Z3\n%d%%", percentages[3]));
            if (zone4 != null) zone4.setText(String.format("Z4\n%d%%", percentages[4]));
            if (zone5 != null) zone5.setText(String.format("Z5\n%d%%", percentages[5]));
        }
    }

    public void updateMonitoringTime() {
        if (getActivity() != null && isMonitoringActive() && tvMonitoringTime != null) {
            getActivity().runOnUiThread(() -> {
                long elapsedTime = 0;
                
                if (monitoringState == STATE_MONITORING && monitoringStartTime > 0) {
                    elapsedTime = totalMonitoringTime + (System.currentTimeMillis() - monitoringStartTime);
                } else if (monitoringState == STATE_PAUSED) {
                    elapsedTime = totalMonitoringTime;
                }
                
                tvMonitoringTime.setText(formatElapsedTime(elapsedTime));
            });
        }
    }

    private String formatElapsedTime(long timeInMillis) {
        long seconds = (timeInMillis / 1000) % 60;
        long minutes = (timeInMillis / (1000 * 60)) % 60;
        long hours = (timeInMillis / (1000 * 60 * 60));
        
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    public void startMonitoring() {
        monitoringStartTime = System.currentTimeMillis();
        lastZoneUpdateTime = monitoringStartTime;
        currentZone = 0;
        totalMonitoringTime = 0;
        pauseStartTime = 0;
        monitoringState = STATE_MONITORING;
        
        // Reiniciar tiempos de zona
        for (int i = 0; i < zoneTimes.length; i++) {
            zoneTimes[i] = 0;
        }
        
        // Iniciar actualizaciones periódicas
        startTimeUpdates();
        
        updateMonitoringTime();
    }
    
    public void stopMonitoring() {
        stopTimeUpdates();
        monitoringStartTime = 0;
        pauseStartTime = 0;
        lastZoneUpdateTime = 0;
        totalMonitoringTime = 0;
        monitoringState = STATE_IDLE;
    }
    
    // Método para pausar el monitoreo
    public void pauseMonitoring() {
        if (monitoringState == STATE_MONITORING) {
            pauseStartTime = System.currentTimeMillis();
            monitoringState = STATE_PAUSED;
            
            // Detener actualizaciones periódicas mientras está pausado
            stopTimeUpdates();
            
            // Guardar el tiempo acumulado hasta este momento
            if (monitoringStartTime > 0) {
                totalMonitoringTime += (pauseStartTime - monitoringStartTime);
            }
        }
    }
    
    // Método para reanudar el monitoreo
    public void resumeMonitoring() {
        if (monitoringState == STATE_PAUSED) {
            monitoringStartTime = System.currentTimeMillis();
            lastZoneUpdateTime = monitoringStartTime;
            monitoringState = STATE_MONITORING;
            
            // Reanudar actualizaciones periódicas
            startTimeUpdates();
        }
    }
}
