package com.heartratemonitor.heartratemonitor.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.heartratemonitor.heartratemonitor.R;
import com.heartratemonitor.heartratemonitor.models.WorkoutSession;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador para mostrar sesiones de entrenamiento en un RecyclerView
 */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {
    
    private final List<WorkoutSession> sessions;
    private final SessionClickListener listener;
    private final SimpleDateFormat dateFormat;
    
    /**
     * Interfaz para manejar clicks en los elementos de la lista
     */
    public interface SessionClickListener {
        void onSessionClicked(long sessionId);
        void onSessionExportClicked(long sessionId);
        void onSessionDeleteClicked(long sessionId);
    }
    
    public SessionAdapter(List<WorkoutSession> sessions, SessionClickListener listener) {
        this.sessions = sessions;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    }
    
    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        WorkoutSession session = sessions.get(position);
        holder.bind(session);
    }
    
    @Override
    public int getItemCount() {
        return sessions.size();
    }
    
    /**
     * ViewHolder para las sesiones
     */
    class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView dateTextView;
        TextView heartRateTextView;
        TextView durationTextView;
        ImageButton exportButton;
        ImageButton deleteButton;
        
        SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            heartRateTextView = itemView.findViewById(R.id.heartRateTextView);
            durationTextView = itemView.findViewById(R.id.durationTextView);
            exportButton = itemView.findViewById(R.id.exportButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            
            // Configurar listeners
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSessionClicked(sessions.get(position).getId());
                }
            });
            
            exportButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSessionExportClicked(sessions.get(position).getId());
                }
            });
            
            deleteButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSessionDeleteClicked(sessions.get(position).getId());
                }
            });
        }
        
        void bind(WorkoutSession session) {
            titleTextView.setText(session.getTitle());
            dateTextView.setText(dateFormat.format(new Date(session.getStartTime())));
            
            // Mostrar frecuencia cardíaca promedio
            String heartRateText = itemView.getContext().getString(
                    R.string.avg_heart_rate_value, session.getAverageHeartRate());
            heartRateTextView.setText(heartRateText);
            
            // Mostrar duración
            int durationMinutes = session.getDurationMinutes();
            String durationText;
            if (durationMinutes < 60) {
                durationText = itemView.getContext().getString(
                        R.string.duration_minutes, durationMinutes);
            } else {
                int hours = durationMinutes / 60;
                int minutes = durationMinutes % 60;
                durationText = itemView.getContext().getString(
                        R.string.duration_hours_minutes, hours, minutes);
            }
            durationTextView.setText(durationText);
        }
    }
} 