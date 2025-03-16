package com.heartratemonitor.heartratemonitor.fragments;

import android.content.Context;
import android.os.Bundle;
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
    
    private MonitorFragmentListener listener;
    private TextView tvConnectionStatus;
    private TextView tvHeartRate;
    private TextView tvRRInterval;
    private TextView tvMonitoringTime;
    private TextView tvDescription;
    private HeartRateView heartRateIndicator;
    
    private TextView zone1;
    private TextView zone2;
    private TextView zone3;
    private TextView zone4;
    private TextView zone5;
    
    private List<Integer> zones;
    private long monitoringStartTime;
    private boolean isMonitoring = false;
    
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
        
        // Estas vistas no están en el layout actual, las comentamos por ahora
        // tvRRInterval = view.findViewById(R.id.tvRRInterval);
        // tvMonitoringTime = view.findViewById(R.id.tvMonitoringTime);
        // tvDescription = view.findViewById(R.id.tvDescription);
        // heartRateIndicator = view.findViewById(R.id.heartRateIndicator);
        
        // Inicializar zonas
        zone1 = view.findViewById(R.id.zone1);
        zone2 = view.findViewById(R.id.zone2);
        zone3 = view.findViewById(R.id.zone3);
        zone4 = view.findViewById(R.id.zone4);
        zone5 = view.findViewById(R.id.zone5);
        
        updateHeartRateZones();
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
                    
                    // Actualizar la zona de frecuencia cardíaca
                    updateHeartRateZone(heartRate);
                    
                    // Si hay un indicador visual, actualizarlo (comentado porque no existe en el layout)
                    // if (heartRateIndicator != null) {
                    //     heartRateIndicator.setValue(heartRate);
                    // }
                    
                    // Actualizar texto de descripción según la zona (comentado porque no existe en el layout)
                    // updateDescriptionText(heartRate);
                    
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
        int currentZone = getCurrentZone(heartRate);
        TextView zoneView = null;
        
        switch (currentZone) {
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

    // Método para actualizar el texto descriptivo según la zona (comentado porque no existe en el layout)
    /*
    private void updateDescriptionText(int heartRate) {
        if (tvDescription == null) return;
        
        int zone = getCurrentZone(heartRate);
        String description = "";
        
        switch (zone) {
            case 1:
                description = getString(R.string.zone1_description);
                break;
            case 2:
                description = getString(R.string.zone2_description);
                break;
            case 3:
                description = getString(R.string.zone3_description);
                break;
            case 4:
                description = getString(R.string.zone4_description);
                break;
            case 5:
                description = getString(R.string.zone5_description);
                break;
            default:
                description = getString(R.string.no_zone_description);
                break;
        }
        
        tvDescription.setText(description);
    }
    */

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

    public void updateMonitoringTime() {
        if (getActivity() != null && isMonitoringActive() /* && tvMonitoringTime != null */) {
            getActivity().runOnUiThread(() -> {
                long currentTime = System.currentTimeMillis();
                long elapsedTime = currentTime - monitoringStartTime;
                
                // Formatear el tiempo en HH:MM:SS
                String timeString = formatElapsedTime(elapsedTime);
                // Comentamos porque la vista no existe en el layout
                // tvMonitoringTime.setText(timeString);
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
        updateMonitoringTime();
    }
    
    public void stopMonitoring() {
        monitoringStartTime = 0;
    }
}
