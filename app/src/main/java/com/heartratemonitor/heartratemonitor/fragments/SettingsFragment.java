package com.heartratemonitor.heartratemonitor.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.heartratemonitor.heartratemonitor.R;
import com.heartratemonitor.heartratemonitor.utils.HeartRateZoneCalculator;

public class SettingsFragment extends Fragment {

    private EditText ageEditText;
    private EditText restingHeartRateEditText;
    private EditText maxHeartRateEditText;
    private RadioGroup zoneCalculationMethod;
    private RadioButton karvonen;
    private RadioButton percentage;
    private Button saveButton;
    
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "HeartRateMonitorPrefs";
    private static final String KEY_AGE = "user_age";
    private static final String KEY_RESTING_HR = "resting_heart_rate";
    private static final String KEY_MAX_HR = "max_heart_rate";
    private static final String KEY_ZONE_CALC_METHOD = "zone_calculation_method";
    
    // Métodos de cálculo de zonas
    public static final int METHOD_KARVONEN = 0;
    public static final int METHOD_PERCENTAGE = 1;
    
    private SettingsFragmentListener listener;
    
    public interface SettingsFragmentListener {
        void onSettingsSaved();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Inicializar vistas
        ageEditText = view.findViewById(R.id.ageEditText);
        restingHeartRateEditText = view.findViewById(R.id.restingHeartRateEditText);
        maxHeartRateEditText = view.findViewById(R.id.maxHeartRateEditText);
        zoneCalculationMethod = view.findViewById(R.id.zoneCalculationMethod);
        karvonen = view.findViewById(R.id.karvonen);
        percentage = view.findViewById(R.id.percentage);
        saveButton = view.findViewById(R.id.saveButton);
        
        // Cargar configuraciones guardadas
        loadSettings();
        
        // Configurar listeners
        saveButton.setOnClickListener(v -> saveSettings());
        
        // Actualizar maxHeartRate automáticamente cuando se cambia la edad
        ageEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                updateMaxHeartRate();
            }
        });
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof SettingsFragmentListener) {
            listener = (SettingsFragmentListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement SettingsFragmentListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    private void loadSettings() {
        int age = sharedPreferences.getInt(KEY_AGE, 30);
        int restingHR = sharedPreferences.getInt(KEY_RESTING_HR, 70);
        int maxHR = sharedPreferences.getInt(KEY_MAX_HR, 220 - age);
        boolean useKarvonen = sharedPreferences.getInt(KEY_ZONE_CALC_METHOD, 
                HeartRateZoneCalculator.METHOD_KARVONEN) == HeartRateZoneCalculator.METHOD_KARVONEN;
        
        ageEditText.setText(String.valueOf(age));
        restingHeartRateEditText.setText(String.valueOf(restingHR));
        maxHeartRateEditText.setText(String.valueOf(maxHR));
        
        if (useKarvonen) {
            karvonen.setChecked(true);
        } else {
            percentage.setChecked(true);
        }
    }

    private void updateMaxHeartRate() {
        try {
            int age = Integer.parseInt(ageEditText.getText().toString());
            // Fórmula estándar para estimar la frecuencia cardíaca máxima
            int estimatedMaxHR = 220 - age;
            maxHeartRateEditText.setText(String.valueOf(estimatedMaxHR));
        } catch (NumberFormatException e) {
            // El usuario no ha ingresado un número válido
        }
    }

    private void saveSettings() {
        try {
            int age = Integer.parseInt(ageEditText.getText().toString());
            int restingHR = Integer.parseInt(restingHeartRateEditText.getText().toString());
            int maxHR = Integer.parseInt(maxHeartRateEditText.getText().toString());
            
            // Validar datos
            if (age < 10 || age > 100) {
                showError(getString(R.string.error_invalid_age));
                return;
            }
            
            if (restingHR < 40 || restingHR > 100) {
                showError(getString(R.string.error_invalid_resting_hr));
                return;
            }
            
            if (maxHR < 100 || maxHR > 220) {
                showError(getString(R.string.error_invalid_max_hr));
                return;
            }
            
            // Determinar método de cálculo seleccionado
            int calculationMethod = karvonen.isChecked() ? 
                    HeartRateZoneCalculator.METHOD_KARVONEN : 
                    HeartRateZoneCalculator.METHOD_PERCENTAGE;
            
            // Guardar configuraciones
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(KEY_AGE, age);
            editor.putInt(KEY_RESTING_HR, restingHR);
            editor.putInt(KEY_MAX_HR, maxHR);
            editor.putInt(KEY_ZONE_CALC_METHOD, calculationMethod);
            editor.apply();
            
            if (listener != null) {
                listener.onSettingsSaved();
            }
            
            Toast.makeText(getContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
            
        } catch (NumberFormatException e) {
            showError(getString(R.string.error_invalid_input));
        }
    }

    private void showError(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }
} 