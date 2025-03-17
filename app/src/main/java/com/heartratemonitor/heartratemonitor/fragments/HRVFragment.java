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
            WorkoutSession session = dbHelper.getSessionById(sessionId);
            List<Integer> rrIntervals = dbHelper.getRRIntervalsForSession(sessionId);
            
            if (getActivity() == null) return;
            
            getActivity().runOnUiThread(() -> {
                loadingProgressBar.setVisibility(View.GONE);
                
                if (session != null && rrIntervals != null && !rrIntervals.isEmpty()) {
                    displaySessionData(session, rrIntervals);
                } else {
                    showError(getString(R.string.error_loading_hrv_data));
                }
            });
        }).start();
    }

    private void displaySessionData(WorkoutSession session, List<Integer> rrIntervals) {
        // Utilizar los valores ya calculados de la sesión
        double sdnn = session.getSdnn();
        double rmssd = session.getRmssd();
        double pnn50 = session.getPnn50();
        double lfhfRatio = session.getLfhfRatio();
        int hrvScore = session.getHrvScore();
        
        // Si los valores están vacíos (cero), intentamos calcularlos con los intervalos RR disponibles
        if (sdnn == 0 && rrIntervals != null && !rrIntervals.isEmpty()) {
            // Convertir lista de enteros a double[] para el análisis
            double[] rrData = new double[rrIntervals.size()];
            for (int i = 0; i < rrIntervals.size(); i++) {
                rrData[i] = rrIntervals.get(i);
            }
            
            // Calcular métricas de HRV
            sdnn = hrvAnalyzer.calculateSDNN(rrData);
            rmssd = hrvAnalyzer.calculateRMSSD(rrData);
            pnn50 = hrvAnalyzer.calculatePNN50(rrData);
            lfhfRatio = hrvAnalyzer.calculateLFHFRatio(rrData);
            hrvScore = hrvAnalyzer.calculateHRVScore(rrData);
        }
        
        // Formatear fecha
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        String dateStr = dateFormat.format(new Date(session.getStartTime()));
        
        // Actualizar vistas
        sessionTitleTextView.setText(session.getTitle());
        dateTextView.setText(dateStr);
        sdnnTextView.setText(getString(R.string.hrv_sdnn_value, String.format("%.2f", sdnn)));
        rmssdTextView.setText(getString(R.string.hrv_rmssd_value, String.format("%.2f", rmssd)));
        pnn50TextView.setText(getString(R.string.hrv_pnn50_value, String.format("%.2f", pnn50)));
        lfrTextView.setText(getString(R.string.hrv_lfr_value, String.format("%.2f", lfhfRatio)));
        hrvScoreTextView.setText(String.valueOf(hrvScore));
        
        // Establecer análisis textual
        String analysisText = getHRVAnalysisText(hrvScore, rmssd, lfhfRatio);
        hrvAnalysisTextView.setText(analysisText);
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