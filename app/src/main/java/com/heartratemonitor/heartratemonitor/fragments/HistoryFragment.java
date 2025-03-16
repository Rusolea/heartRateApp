package com.heartratemonitor.heartratemonitor.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.heartratemonitor.heartratemonitor.R;
import com.heartratemonitor.heartratemonitor.adapters.SessionAdapter;
import com.heartratemonitor.heartratemonitor.database.DatabaseHelper;
import com.heartratemonitor.heartratemonitor.models.WorkoutSession;
import com.heartratemonitor.heartratemonitor.models.HeartRateData;

import java.util.List;

/**
 * Fragmento para mostrar el historial de sesiones
 */
public class HistoryFragment extends Fragment implements SessionAdapter.SessionClickListener {
    
    private RecyclerView recyclerView;
    private TextView emptyView;
    private SessionAdapter adapter;
    private DatabaseHelper databaseHelper;
    
    // Interfaz para comunicación con actividad principal
    public interface HistoryFragmentListener {
        void onSessionClicked(long sessionId);
        void onSessionExportRequested(long sessionId, List<HeartRateData> heartRateDataList);
        void onSessionDeleteRequested(long sessionId);
    }
    
    private HistoryFragmentListener listener;
    
    public HistoryFragment() {
        // Constructor vacío requerido
    }
    
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof HistoryFragmentListener) {
            listener = (HistoryFragmentListener) context;
        } else {
            throw new RuntimeException(context + " debe implementar HistoryFragmentListener");
        }
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        recyclerView = view.findViewById(R.id.recyclerView);
        emptyView = view.findViewById(R.id.emptyView);
        
        // Configurar RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        
        // Inicializar base de datos
        databaseHelper = DatabaseHelper.getInstance(requireContext());
        
        // Cargar datos
        loadData();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Recargar datos para mostrar cambios
        loadData();
    }
    
    private void loadData() {
        List<WorkoutSession> sessions = databaseHelper.getAllSessions();
        
        if (sessions.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            
            adapter = new SessionAdapter(sessions, this);
            recyclerView.setAdapter(adapter);
        }
    }
    
    /**
     * Actualiza la lista de sesiones
     */
    public void refreshSessionList() {
        loadData();
    }
    
    // Implementación de SessionAdapter.SessionClickListener
    
    @Override
    public void onSessionClicked(long sessionId) {
        // Abrir detalle de sesión
        if (listener != null) {
            listener.onSessionClicked(sessionId);
        }
    }
    
    @Override
    public void onSessionExportClicked(long sessionId) {
        // Exportar datos de la sesión
        if (listener != null) {
            List<HeartRateData> heartRateData = databaseHelper.getHeartRateDataForSession(sessionId);
            listener.onSessionExportRequested(sessionId, heartRateData);
        }
    }
    
    @Override
    public void onSessionDeleteClicked(long sessionId) {
        // Confirmar eliminación
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_confirm_delete_title)
                .setMessage(R.string.dialog_delete_message)
                .setPositiveButton(R.string.dialog_delete, (dialog, which) -> {
                    // Eliminar sesión
                    if (listener != null) {
                        listener.onSessionDeleteRequested(sessionId);
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }
} 