package com.heartratemonitor.heartratemonitor.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.heartratemonitor.heartratemonitor.R;
import com.heartratemonitor.heartratemonitor.database.DatabaseHelper;
import com.heartratemonitor.heartratemonitor.models.HeartRateData;
import com.heartratemonitor.heartratemonitor.utils.HRVAnalyzer;
import com.heartratemonitor.heartratemonitor.models.WorkoutSession;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Date;

public class HRVFragment extends Fragment {

    private TextView sessionTitleTextView;
    private TextView dateTextView;
    private TextView sdnnTextView;
    private TextView rmssdTextView;
    private TextView pnn50TextView;
    private TextView lfrTextView;
    private TextView hrvScoreTextView;
    private TextView hrvAnalysisTextView;
    private Button exportButton;
    private ProgressBar loadingProgressBar;
    private DatabaseHelper dbHelper;
    private long sessionId = -1;
    private HRVAnalyzer hrvAnalyzer;
    private HRVFragmentListener listener;

    public interface HRVFragmentListener {
        void onHRVFragmentExportRequested(long sessionId, List<Integer> rrIntervals);
    }

    public static HRVFragment newInstance(long sessionId) {
        HRVFragment fragment = new HRVFragment();
        Bundle args = new Bundle();
        args.putLong("session_id", sessionId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            sessionId = getArguments().getLong("session_id", -1);
        }
        dbHelper = new DatabaseHelper(getContext());
        hrvAnalyzer = new HRVAnalyzer();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_hrv, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Inicializar vistas
        sessionTitleTextView = view.findViewById(R.id.sessionTitleTextView);
        dateTextView = view.findViewById(R.id.dateTextView);
        sdnnTextView = view.findViewById(R.id.sdnnTextView);
        rmssdTextView = view.findViewById(R.id.rmssdTextView);
        pnn50TextView = view.findViewById(R.id.pnn50TextView);
        lfrTextView = view.findViewById(R.id.lfrTextView);
        hrvScoreTextView = view.findViewById(R.id.hrvScoreTextView);
        hrvAnalysisTextView = view.findViewById(R.id.hrvAnalysisTextView);
        exportButton = view.findViewById(R.id.exportButton);
        loadingProgressBar = view.findViewById(R.id.loadingProgressBar);
        
        if (sessionId != -1) {
            loadSessionData();
        } else {
            // No hay sesión para analizar
            showError(getString(R.string.error_no_session_data));
        }
        
        exportButton.setOnClickListener(v -> {
            if (sessionId != -1) {
                List<Integer> rrIntervals = dbHelper.getRRIntervalsForSession(sessionId);
                if (listener != null) {
                    listener.onHRVFragmentExportRequested(sessionId, rrIntervals);
                }
            }
        });
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof HRVFragmentListener) {
            listener = (HRVFragmentListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement HRVFragmentListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    private void loadSessionData() {
        loadingProgressBar.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            try {
                // Obtener la sesión y los intervalos RR
                WorkoutSession session = dbHelper.getSessionById(sessionId);
                List<Integer> rrIntervals = dbHelper.getRRIntervalsForSession(sessionId);
                
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    loadingProgressBar.setVisibility(View.GONE);
                    
                    // Verificar si la sesión existe
                    if (session != null) {
                        // Mostrar los datos disponibles, incluso si no hay intervalos RR
                        displaySessionData(session, rrIntervals != null ? rrIntervals : new ArrayList<>());
                    } else {
                        // Si la sesión no existe, mostrar error
                        showError(getString(R.string.error_no_session_data));
                    }
                });
            } catch (Exception e) {
                // Capturar cualquier excepción que pueda ocurrir durante la carga
                e.printStackTrace();
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        loadingProgressBar.setVisibility(View.GONE);
                        showError("Error: " + e.getMessage());
                    });
                }
            }
        }).start();
    }

    private void displaySessionData(WorkoutSession session, List<Integer> rrIntervals) {
        try {
            // Utilizar los valores ya calculados de la sesión
            double sdnn = session.getSdnn();
            double rmssd = session.getRmssd();
            double pnn50 = session.getPnn50();
            double lfhfRatio = session.getLfhfRatio();
            int hrvScore = session.getHrvScore();
            
            // Si los valores están vacíos (cero) Y tenemos intervalos RR disponibles, 
            // intentamos calcularlos nuevamente
            if ((sdnn == 0 || rmssd == 0 || pnn50 == 0 || lfhfRatio == 0 || hrvScore == 0) 
                    && !rrIntervals.isEmpty()) {
                // Convertir lista de enteros a double[] para el análisis
                double[] rrData = new double[rrIntervals.size()];
                for (int i = 0; i < rrIntervals.size(); i++) {
                    rrData[i] = rrIntervals.get(i);
                }
                
                // Calcular métricas de HRV solo para los valores que son cero
                if (sdnn == 0) sdnn = hrvAnalyzer.calculateSDNN(rrData);
                if (rmssd == 0) rmssd = hrvAnalyzer.calculateRMSSD(rrData);
                if (pnn50 == 0) pnn50 = hrvAnalyzer.calculatePNN50(rrData);
                if (lfhfRatio == 0) lfhfRatio = hrvAnalyzer.calculateLFHFRatio(rrData);
                if (hrvScore == 0) hrvScore = hrvAnalyzer.calculateHRVScore(rrData);
                
                // Actualizar la sesión con los nuevos valores calculados
                if (sdnn > 0 || rmssd > 0 || pnn50 > 0 || lfhfRatio > 0 || hrvScore > 0) {
                    WorkoutSession updatedSession = new WorkoutSession();
                    updatedSession.setId(session.getId());
                    updatedSession.setTitle(session.getTitle());
                    updatedSession.setStartTime(session.getStartTime());
                    updatedSession.setEndTime(session.getEndTime());
                    updatedSession.setActivityType(session.getActivityType());
                    updatedSession.setAverageHeartRate(session.getAverageHeartRate());
                    updatedSession.setMaxHeartRate(session.getMaxHeartRate());
                    updatedSession.setCaloriesBurned(session.getCaloriesBurned());
                    updatedSession.setTimeInZone1(session.getTimeInZone1());
                    updatedSession.setTimeInZone2(session.getTimeInZone2());
                    updatedSession.setTimeInZone3(session.getTimeInZone3());
                    updatedSession.setTimeInZone4(session.getTimeInZone4());
                    updatedSession.setTimeInZone5(session.getTimeInZone5());
                    
                    // Actualizar solo los valores que hemos recalculado
                    updatedSession.setSdnn(sdnn > 0 ? sdnn : session.getSdnn());
                    updatedSession.setRmssd(rmssd > 0 ? rmssd : session.getRmssd());
                    updatedSession.setPnn50(pnn50 > 0 ? pnn50 : session.getPnn50());
                    updatedSession.setLfhfRatio(lfhfRatio > 0 ? lfhfRatio : session.getLfhfRatio());
                    updatedSession.setHrvScore(hrvScore > 0 ? hrvScore : session.getHrvScore());
                    
                    // Actualizar la sesión en la base de datos
                    new Thread(() -> dbHelper.updateSession(updatedSession)).start();
                }
            }
            
            // Formatear fecha
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            String dateStr = dateFormat.format(new Date(session.getStartTime()));
            
            // Actualizar vistas con valores seguros (nunca nulos)
            sessionTitleTextView.setText(session.getTitle());
            dateTextView.setText(dateStr);
            
            // Formatear los valores numéricos o mostrar "N/A" si son cero
            sdnnTextView.setText(sdnn > 0 ? 
                    getString(R.string.hrv_sdnn_value, String.format("%.2f", sdnn)) : 
                    getString(R.string.not_available));
            
            rmssdTextView.setText(rmssd > 0 ? 
                    getString(R.string.hrv_rmssd_value, String.format("%.2f", rmssd)) : 
                    getString(R.string.not_available));
            
            pnn50TextView.setText(pnn50 > 0 ? 
                    getString(R.string.hrv_pnn50_value, String.format("%.2f", pnn50)) : 
                    getString(R.string.not_available));
            
            lfrTextView.setText(lfhfRatio > 0 ? 
                    getString(R.string.hrv_lfr_value, String.format("%.2f", lfhfRatio)) : 
                    getString(R.string.not_available));
            
            hrvScoreTextView.setText(hrvScore > 0 ? 
                    String.valueOf(hrvScore) : 
                    getString(R.string.not_available));
            
            // Establecer análisis textual solo si tenemos datos válidos
            if (hrvScore > 0 && rmssd > 0 && lfhfRatio > 0) {
                String analysisText = getHRVAnalysisText(hrvScore, rmssd, lfhfRatio);
                hrvAnalysisTextView.setText(analysisText);
            } else {
                hrvAnalysisTextView.setText(getString(R.string.insufficient_data_for_analysis));
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error al mostrar datos: " + e.getMessage());
        }
    }

    private String getHRVAnalysisText(int hrvScore, double rmssd, double lfHfRatio) {
        StringBuilder analysis = new StringBuilder();
        
        // Análisis basado en la puntuación
        if (hrvScore >= 80) {
            analysis.append(getString(R.string.hrv_analysis_excellent));
        } else if (hrvScore >= 60) {
            analysis.append(getString(R.string.hrv_analysis_good));
        } else if (hrvScore >= 40) {
            analysis.append(getString(R.string.hrv_analysis_moderate));
        } else {
            analysis.append(getString(R.string.hrv_analysis_poor));
        }
        
        analysis.append("\n\n");
        
        // Análisis basado en RMSSD (indicador de actividad parasimpática)
        if (rmssd > 50) {
            analysis.append(getString(R.string.hrv_analysis_rmssd_high));
        } else if (rmssd > 20) {
            analysis.append(getString(R.string.hrv_analysis_rmssd_normal));
        } else {
            analysis.append(getString(R.string.hrv_analysis_rmssd_low));
        }
        
        analysis.append("\n\n");
        
        // Análisis de balance simpático/parasimpático basado en la relación LF/HF
        if (lfHfRatio > 2.0) {
            analysis.append(getString(R.string.hrv_analysis_lfhf_high));
        } else if (lfHfRatio < 0.5) {
            analysis.append(getString(R.string.hrv_analysis_lfhf_low));
        } else {
            analysis.append(getString(R.string.hrv_analysis_lfhf_balanced));
        }
        
        return analysis.toString();
    }

    private void showError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }
} 