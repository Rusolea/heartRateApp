package com.heartratemonitor.heartratemonitor.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.heartratemonitor.heartratemonitor.MainActivity;
import com.heartratemonitor.heartratemonitor.R;
import com.heartratemonitor.heartratemonitor.bluetooth.BluetoothHandler;
import com.heartratemonitor.heartratemonitor.utils.HRVAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HeartRateService extends Service {
    private static final String TAG = "HeartRateService";
    private static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "heart_rate_channel";
    private static final String ACTION_STOP_SERVICE = "com.heartratemonitor.heartratemonitor.STOP_SERVICE";
    private static final String ACTION_PAUSE_SERVICE = "com.heartratemonitor.heartratemonitor.PAUSE_SERVICE";
    private static final String ACTION_RESUME_SERVICE = "com.heartratemonitor.heartratemonitor.RESUME_SERVICE";
    private static final String ACTION_UPDATE_HEART_RATE = "com.heartratemonitor.heartratemonitor.UPDATE_HEART_RATE";
    private static final String ACTION_SERVICE_STOPPED = "com.heartratemonitor.heartratemonitor.SERVICE_STOPPED";
    
    // Estados del servicio
    private static final int STATE_IDLE = 0;
    private static final int STATE_MONITORING = 1;
    private static final int STATE_PAUSED = 2;
    
    // Valores para las zonas de frecuencia cardíaca
    private List<Integer> zones;
    
    private final IBinder binder = new LocalBinder();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private String deviceAddress;
    private int currentHeartRate = 0;
    private String currentZone = "Sin datos";
    private int maxHeartRate = 220; // Valor por defecto, debe personalizarse
    
    private NotificationManager notificationManager;
    private boolean isMonitoring = false;
    private int serviceState = STATE_IDLE;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    // Clase Binder para clientes del servicio
    public class LocalBinder extends Binder {
        public HeartRateService getService() {
            return HeartRateService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Inicializar gestor de notificaciones
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Crear canal de notificaciones para Android O y superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Heart Rate Monitor",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Canal para monitoreo de frecuencia cardíaca");
            notificationManager.createNotificationChannel(channel);
        }
        
        // Inicializar zonas de frecuencia cardíaca
        initHeartRateZones();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP_SERVICE.equals(action)) {
                stopHeartRateMonitoring();
                // Enviar broadcast para notificar a la actividad
                Intent broadcastIntent = new Intent(ACTION_SERVICE_STOPPED);
                sendBroadcast(broadcastIntent);
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_PAUSE_SERVICE.equals(action)) {
                pauseHeartRateMonitoring();
                return START_STICKY;
            } else if (ACTION_RESUME_SERVICE.equals(action)) {
                resumeHeartRateMonitoring();
                return START_STICKY;
            }
            
            // Si hay una dirección de dispositivo, conectarse
            if (intent.hasExtra("deviceAddress")) {
                deviceAddress = intent.getStringExtra("deviceAddress");
                maxHeartRate = intent.getIntExtra("maxHeartRate", 220);
                
                startForeground(NOTIFICATION_ID, createNotification("Conectando...", 0, ""));
                connectToDevice(deviceAddress);
                isMonitoring = true;
                serviceState = STATE_MONITORING;
            }
        }
        
        return START_STICKY;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopHeartRateMonitoring();
    }
    
    private void initHeartRateZones() {
        zones = new ArrayList<>();
        // Ejemplo de zonas (los valores pueden ajustarse según configuración)
        zones.add(90);  // Zona 1: 0-90
        zones.add(120); // Zona 2: 91-120
        zones.add(150); // Zona 3: 121-150
        zones.add(170); // Zona 4: 151-170
        // Zona 5: >170
    }
    
    private Notification createNotification(String title, int heartRate, String zone) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_heart)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true);
                
        // Construir el texto de la notificación
        String contentText = heartRate > 0 
                ? "BPM: " + heartRate + (zone.isEmpty() ? "" : " | Zona: " + zone)
                : "Esperando datos de frecuencia cardíaca...";
        
        builder.setContentText(contentText);
        
        // Agregar acciones según el estado
        if (serviceState == STATE_MONITORING) {
            // Acción de pausar
            Intent pauseIntent = new Intent(this, HeartRateService.class);
            pauseIntent.setAction(ACTION_PAUSE_SERVICE);
            PendingIntent pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent,
                    PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_pause, getString(R.string.pause_service), pausePendingIntent);
            
            // Acción de detener
            Intent stopIntent = new Intent(this, HeartRateService.class);
            stopIntent.setAction(ACTION_STOP_SERVICE);
            PendingIntent stopPendingIntent = PendingIntent.getService(this, 2, stopIntent,
                    PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_stop, getString(R.string.stop_service), stopPendingIntent);
        } else if (serviceState == STATE_PAUSED) {
            // Acción de reanudar
            Intent resumeIntent = new Intent(this, HeartRateService.class);
            resumeIntent.setAction(ACTION_RESUME_SERVICE);
            PendingIntent resumePendingIntent = PendingIntent.getService(this, 3, resumeIntent,
                    PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_play, getString(R.string.resume_service), resumePendingIntent);
            
            // Acción de detener
            Intent stopIntent = new Intent(this, HeartRateService.class);
            stopIntent.setAction(ACTION_STOP_SERVICE);
            PendingIntent stopPendingIntent = PendingIntent.getService(this, 2, stopIntent,
                    PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_stop, getString(R.string.stop_service), stopPendingIntent);
        }
        
        return builder.build();
    }
    
    private void connectToDevice(String address) {
        if (bluetoothAdapter == null) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                Log.e(TAG, "Bluetooth no disponible");
                return;
            }
        }
        
        // Comprobar si hay una conexión existente
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        
        try {
            // Obtener el dispositivo Bluetooth
            final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            
            // Conectar al dispositivo GATT
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
            updateNotification("Conectando a " + device.getName(), 0, "");
            Log.d(TAG, "Conectando a " + device.getName());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Dirección del dispositivo no válida: " + address, e);
        }
    }
    
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Conectado al dispositivo GATT.");
                // Actualizar UI con estado de conexión
                updateNotification("Conectado a " + gatt.getDevice().getName(), 0, "");
                
                // Descubrir servicios
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Desconectado del dispositivo GATT.");
                
                // Actualizar UI con estado de desconexión
                updateNotification("Desconectado", 0, "");
                
                // Intentar reconectar
                connectToDevice(deviceAddress);
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Buscar servicio de frecuencia cardíaca
                UUID HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
                UUID HR_CHARACTERISTIC_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
                
                BluetoothGattService hrService = gatt.getService(HR_SERVICE_UUID);
                if (hrService != null) {
                    BluetoothGattCharacteristic hrCharacteristic = 
                            hrService.getCharacteristic(HR_CHARACTERISTIC_UUID);
                    
                    if (hrCharacteristic != null) {
                        // Habilitar notificaciones para recibir actualizaciones
                        boolean success = gatt.setCharacteristicNotification(hrCharacteristic, true);
                        Log.d(TAG, "Configuración de notificaciones HR: " + success);
                        
                        updateNotification("Monitoreando frecuencia cardíaca", 0, "");
                    } else {
                        Log.e(TAG, "Característica de frecuencia cardíaca no encontrada");
                    }
                } else {
                    Log.e(TAG, "Servicio de frecuencia cardíaca no encontrado");
                }
            } else {
                Log.e(TAG, "Error al descubrir servicios: " + status);
            }
        }
        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // Analizar los datos de frecuencia cardíaca
            if (characteristic.getUuid().toString().equals("00002a37-0000-1000-8000-00805f9b34fb")) {
                int flag = characteristic.getProperties();
                int format = -1;
                if ((flag & 0x01) != 0) {
                    format = BluetoothGattCharacteristic.FORMAT_UINT16;
                } else {
                    format = BluetoothGattCharacteristic.FORMAT_UINT8;
                }
                
                final int heartRate = characteristic.getIntValue(format, 1);
                Log.d(TAG, "Frecuencia cardíaca recibida: " + heartRate);
                
                // Obtener la zona actual
                int zone = getCurrentZone(heartRate);
                String zoneText = "Zona " + zone;
                
                // Actualizar la UI
                currentHeartRate = heartRate;
                currentZone = zoneText;
                
                // Notificar a los componentes de la app
                broadcastHeartRateUpdate(heartRate, zone);
                
                // Actualizar la notificación
                updateNotification("Monitoreo activo", heartRate, zoneText);
            }
        }
    };
    
    private void broadcastHeartRateUpdate(int heartRate, int zone) {
        Intent intent = new Intent(ACTION_UPDATE_HEART_RATE);
        intent.putExtra("heartRate", heartRate);
        intent.putExtra("zone", zone);
        intent.putExtra("percentage", calculateHeartRatePercentage(heartRate));
        sendBroadcast(intent);
    }
    
    private int calculateHeartRatePercentage(int heartRate) {
        return (int) (((double) heartRate / maxHeartRate) * 100);
    }
    
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
    
    private void updateNotification(String title, int heartRate, String zone) {
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(title, heartRate, zone));
        }
    }
    
    public void pauseHeartRateMonitoring() {
        serviceState = STATE_PAUSED;
        updateNotification(getString(R.string.measurement_paused), currentHeartRate, currentZone);
    }
    
    public void resumeHeartRateMonitoring() {
        serviceState = STATE_MONITORING;
        updateNotification(getString(R.string.service_notification_monitoring), currentHeartRate, currentZone);
    }
    
    public void stopHeartRateMonitoring() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        serviceState = STATE_IDLE;
        isMonitoring = false;
        
        // Enviar broadcast para notificar a la actividad si la detención es manual
        // y no por la destrucción del servicio
        Intent broadcastIntent = new Intent(ACTION_SERVICE_STOPPED);
        sendBroadcast(broadcastIntent);
    }
    
    public boolean isMonitoring() {
        return isMonitoring;
    }
    
    public int getServiceState() {
        return serviceState;
    }
    
    public void setMaxHeartRate(int maxHeartRate) {
        this.maxHeartRate = maxHeartRate;
    }
} 