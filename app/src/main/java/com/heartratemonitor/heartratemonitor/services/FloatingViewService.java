package com.heartratemonitor.heartratemonitor.services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.heartratemonitor.heartratemonitor.R;

public class FloatingViewService extends Service {
    private static final String TAG = "FloatingViewService";
    private static final String ACTION_UPDATE_HEART_RATE = "com.heartratemonitor.heartratemonitor.UPDATE_HEART_RATE";
    
    private WindowManager windowManager;
    private View floatingView;
    private TextView tvHeartRate;
    private TextView tvHeartRatePercentage;
    private TextView tvZone;
    private ImageView btnClose;
    private WindowManager.LayoutParams params;
    
    private final BroadcastReceiver heartRateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_UPDATE_HEART_RATE.equals(intent.getAction())) {
                int heartRate = intent.getIntExtra("heartRate", 0);
                int zone = intent.getIntExtra("zone", 0);
                int percentage = intent.getIntExtra("percentage", 0);
                
                updateFloatingView(heartRate, zone, percentage);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Crear la vista flotante
        createFloatingView();
        
        // Registrar receptor para actualizaciones de frecuencia cardíaca
        IntentFilter filter = new IntentFilter(ACTION_UPDATE_HEART_RATE);
        registerReceiver(heartRateReceiver, filter);
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("stop_service", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Eliminar la vista flotante
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
            floatingView = null;
        }
        
        // Dar de baja el receptor de actualizaciones
        try {
            unregisterReceiver(heartRateReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error al dar de baja el receptor: " + e.getMessage());
        }
    }
    
    private void createFloatingView() {
        // Inflar la vista
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_view, null);
        
        // Inicializar componentes
        tvHeartRate = floatingView.findViewById(R.id.tvHeartRate);
        tvHeartRatePercentage = floatingView.findViewById(R.id.tvHeartRatePercentage);
        tvZone = floatingView.findViewById(R.id.tvZone);
        btnClose = floatingView.findViewById(R.id.btnClose);
        
        // Establecer acción de cierre
        btnClose.setOnClickListener(v -> {
            stopSelf();
        });
        
        // Configurar los parámetros de la ventana
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getLayoutType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;
        
        // Obtener el servicio de gestión de ventanas
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // Agregar la vista al administrador de ventanas
        windowManager.addView(floatingView, params);
        
        // Configurar la capacidad de mover la vista
        setupViewDragging();
    }
    
    private int getLayoutType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_PHONE;
        }
    }
    
    private void setupViewDragging() {
        // Listener para mover la vista flotante por la pantalla
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Ignorar clic en botón cerrar
                if (isClickOnClose(event)) {
                    return false;
                }
                
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        // Calcular movimiento
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        
                        // Actualizar posición de la vista
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                        
                    default:
                        return false;
                }
            }
            
            private boolean isClickOnClose(MotionEvent event) {
                // Verificar si el toque es sobre el botón cerrar
                if (btnClose != null) {
                    int[] location = new int[2];
                    btnClose.getLocationOnScreen(location);
                    int x = location[0];
                    int y = location[1];
                    
                    return event.getRawX() >= x && event.getRawX() <= x + btnClose.getWidth() &&
                           event.getRawY() >= y && event.getRawY() <= y + btnClose.getHeight();
                }
                return false;
            }
        });
    }
    
    private void updateFloatingView(int heartRate, int zone, int percentage) {
        if (tvHeartRate != null) {
            tvHeartRate.setText(String.valueOf(heartRate));
        }
        
        if (tvHeartRatePercentage != null) {
            tvHeartRatePercentage.setText(percentage + "%");
            
            // Actualizar color según la zona
            int colorResId;
            switch (zone) {
                case 1:
                    colorResId = R.color.zone1;
                    break;
                case 2:
                    colorResId = R.color.zone2;
                    break;
                case 3:
                    colorResId = R.color.zone3;
                    break;
                case 4:
                    colorResId = R.color.zone4;
                    break;
                case 5:
                    colorResId = R.color.zone5;
                    break;
                default:
                    colorResId = R.color.colorAccent;
                    break;
            }
            
            tvHeartRatePercentage.setTextColor(getResources().getColor(colorResId));
        }
        
        if (tvZone != null) {
            tvZone.setText("Zona " + zone);
        }
    }
} 