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
    private static final String ACTION_UPDATE_HEART_RATE = "com.heartratemonitor.heartratemonitor.UPDATE_HEART_RATE";
    
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
        if (intent != null && ACTION_STOP_SERVICE.equals(intent.getAction())) {
            stopHeartRateMonitoring();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Si hay una dirección de dispositivo, conectarse
        if (intent != null && intent.hasExtra("deviceAddress")) {
            deviceAddress = intent.getStringExtra("deviceAddress");
            maxHeartRate = intent.getIntExtra("maxHeartRate", 220);
            
            startForeground(NOTIFICATION_ID, createNotification("Conectando...", 0, ""));
            connectToDevice(deviceAddress);
            isMonitoring = true;
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
        
        Intent stopIntent = new Intent(this, HeartRateService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE);
        
        // Construir la notificación
        String contentText = heartRate > 0 
                ? "BPM: " + heartRate + (zone.isEmpty() ? "" : " | Zona: " + zone)
                : "Esperando datos de frecuencia cardíaca...";
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_heart)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(R.drawable.ic_delete, "Detener", stopPendingIntent);
        
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
    
    public void stopHeartRateMonitoring() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        isMonitoring = false;
    }
    
    public boolean isMonitoring() {
        return isMonitoring;
    }
    
    public void setMaxHeartRate(int maxHeartRate) {
        this.maxHeartRate = maxHeartRate;
    }
} 