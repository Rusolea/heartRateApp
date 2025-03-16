package com.heartratemonitor.heartratemonitor.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothHandler {

    private static final String TAG = "BluetoothHandler";
    
    // Códigos de error
    public static final int ERROR_SCAN_FAILED = 1;
    public static final int ERROR_CONNECT_FAILED = 2;
    public static final int ERROR_SERVICE_NOT_FOUND = 3;
    
    // Tiempo máximo de escaneo en ms
    private static final long SCAN_TIMEOUT = 15000;
    
    // Solicitud para habilitar Bluetooth
    public static final int REQUEST_ENABLE_BT = 1;

    // UUIDs del servicio de frecuencia cardíaca
    private final UUID HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    private final UUID HEART_RATE_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");
    private final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final BluetoothListener listener;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private ScanCallback scanCallback;
    private Handler timeoutHandler;
    
    // Lista de dispositivos encontrados durante el escaneo
    private List<BluetoothDeviceInfo> devices = new ArrayList<>();

    /**
     * Clase para almacenar información básica de dispositivos Bluetooth
     */
    public static class BluetoothDeviceInfo {
        private String name;
        private String address;
        
        public BluetoothDeviceInfo(String name, String address) {
            this.name = name != null ? name : "Unknown Device";
            this.address = address;
        }
        
        public String getName() {
            return name;
        }
        
        public String getAddress() {
            return address;
        }
    }

    public BluetoothHandler(Context context, BluetoothListener listener) {
        this.context = context;
        this.listener = listener;
        this.timeoutHandler = new Handler();
        
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        this.bluetoothAdapter = adapter;
        
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    public void startBleScan() {
        if (bluetoothLeScanner == null) {
            listener.onBluetoothError(ERROR_SCAN_FAILED);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            listener.onBluetoothError(ERROR_SCAN_FAILED);
            return;
        }

        listener.onStatusUpdate("Starting BLE scan...");
        
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                Log.d(TAG, "Found device: " + device.getAddress() + 
                        (device.getName() != null ? " - " + device.getName() : ""));
                        
                // Verificar si anuncia el servicio de frecuencia cardíaca
                boolean hasHRService = false;
                if (result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null) {
                    hasHRService = result.getScanRecord().getServiceUuids().contains(new ParcelUuid(HEART_RATE_SERVICE_UUID));
                    Log.d(TAG, "Device " + device.getAddress() + " has heart rate service: " + hasHRService);
                    
                    if (hasHRService) {
                        stopScan();
                        connectToDevice(device);
                    }
                }
                
                // En modo depuración, también mostrar dispositivos sin servicio de HR
                if (!hasHRService) {
                    try {
                        String deviceName = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                deviceName = device.getName();
                            }
                        } else {
                            deviceName = device.getName();
                        }
                        
                        // Si el nombre es nulo o vacío, usar dirección
                        if (deviceName == null || deviceName.isEmpty()) {
                            deviceName = "Unknown Device: " + device.getAddress();
                        }
                        
                        // Añadir a la lista si no existe
                        boolean deviceExists = false;
                        for (BluetoothDeviceInfo info : devices) {
                            if (info.getAddress().equals(device.getAddress())) {
                                deviceExists = true;
                                break;
                            }
                        }
                        
                        if (!deviceExists) {
                            devices.add(new BluetoothDeviceInfo(deviceName, device.getAddress()));
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "Security exception during scan", e);
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "Scan failed with error code: " + errorCode);
                listener.onBluetoothError(ERROR_SCAN_FAILED);
            }
        };
        
        bluetoothLeScanner.startScan(scanCallback);
        
        // Detener el escaneo después de 15 segundos para ahorrar batería
        timeoutHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScan();
                if (!devices.isEmpty()) {
                    listener.onScanComplete(devices);
                } else {
                    listener.onBluetoothError(ERROR_SCAN_FAILED);
                }
            }
        }, SCAN_TIMEOUT);
    }

    public void stopScan() {
        // Cancelar timeout si existe
        timeoutHandler.removeCallbacksAndMessages(null);
            
        // Verificar permiso para Android 12+ (API 31)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                listener.onBluetoothError(ERROR_SCAN_FAILED);
                return;
            }
        }
        try {
            if (bluetoothLeScanner != null && scanCallback != null) {
                bluetoothLeScanner.stopScan(scanCallback);
                listener.onStatusUpdate("Scan stopped");
            }
            
            // Notificar resultados
            if (listener != null) {
                listener.onScanComplete(devices);
            }
        } catch (SecurityException e) {
            listener.onBluetoothError(ERROR_SCAN_FAILED);
        }
    }

    /**
     * Conecta al dispositivo utilizando su dirección MAC
     * @param address Dirección MAC del dispositivo Bluetooth
     */
    public void connectToDevice(String address) {
        if (bluetoothAdapter == null) {
            listener.onBluetoothError(ERROR_CONNECT_FAILED);
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            listener.onBluetoothError(ERROR_CONNECT_FAILED);
            return;
        }
        
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            connectToDevice(device);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid Bluetooth address: " + address, e);
            listener.onBluetoothError(ERROR_CONNECT_FAILED);
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        listener.onStatusUpdate("Connecting to device: " + (device.getName() != null ? device.getName() : "Unknown"));
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            listener.onBluetoothError(ERROR_CONNECT_FAILED);
            return;
        }
        
        bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    listener.onStatusUpdate("Connected. Discovering services...");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        listener.onBluetoothError(ERROR_CONNECT_FAILED);
                        return;
                    }
                    gatt.discoverServices();
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    listener.onStatusUpdate("Disconnected");
                    bluetoothGatt = null;
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                Log.d(TAG, "onServicesDiscovered status: " + status);
                
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Service discovery failed with status: " + status);
                    listener.onBluetoothError(ERROR_SERVICE_NOT_FOUND);
                    return;
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "No BLUETOOTH_CONNECT permission");
                    listener.onBluetoothError(ERROR_CONNECT_FAILED);
                    return;
                }
                
                // Buscar servicio de frecuencia cardíaca
                Log.d(TAG, "Looking for Heart Rate Service: " + HEART_RATE_SERVICE_UUID);
                
                try {
                    // Listar todos los servicios disponibles para depuración
                    if (gatt.getServices() != null) {
                        for (android.bluetooth.BluetoothGattService service : gatt.getServices()) {
                            Log.d(TAG, "Found service: " + service.getUuid().toString());
                        }
                    }
                    
                    android.bluetooth.BluetoothGattService hrService = gatt.getService(HEART_RATE_SERVICE_UUID);
                    if (hrService == null) {
                        Log.e(TAG, "Heart Rate Service not found");
                        listener.onBluetoothError(ERROR_SERVICE_NOT_FOUND);
                        return;
                    }
                    
                    Log.d(TAG, "Found Heart Rate Service. Looking for Heart Rate Measurement characteristic: " + HEART_RATE_CHARACTERISTIC_UUID);
                    
                    // Listar todas las características del servicio
                    for (BluetoothGattCharacteristic characteristic : hrService.getCharacteristics()) {
                        Log.d(TAG, "Found characteristic: " + characteristic.getUuid().toString());
                    }
                    
                    BluetoothGattCharacteristic characteristic = hrService.getCharacteristic(HEART_RATE_CHARACTERISTIC_UUID);
                    
                    if (characteristic != null) {
                        Log.d(TAG, "Found Heart Rate Measurement characteristic. Enabling notifications...");
                        
                        // Habilitar notificaciones para recibir datos
                        boolean success = gatt.setCharacteristicNotification(characteristic, true);
                        Log.d(TAG, "setCharacteristicNotification result: " + success);
                        
                        if (success) {
                            // Escribir descriptor para activar notificaciones
                            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
                            if (descriptor != null) {
                                Log.d(TAG, "Found Client Characteristic Config descriptor. Setting notification value...");
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                boolean writeResult = gatt.writeDescriptor(descriptor);
                                Log.d(TAG, "writeDescriptor result: " + writeResult);
                                listener.onStatusUpdate("Ready to receive heart rate data");
                            } else {
                                Log.e(TAG, "Client Characteristic Config descriptor not found");
                                listener.onBluetoothError(ERROR_SERVICE_NOT_FOUND);
                            }
                        } else {
                            Log.e(TAG, "Failed to enable characteristic notification");
                            listener.onBluetoothError(ERROR_SERVICE_NOT_FOUND);
                        }
                    } else {
                        Log.e(TAG, "Heart Rate Measurement characteristic not found");
                        listener.onBluetoothError(ERROR_SERVICE_NOT_FOUND);
                    }
                } catch (NullPointerException e) {
                    Log.e(TAG, "Error discovering services", e);
                    listener.onBluetoothError(ERROR_SERVICE_NOT_FOUND);
                    return;
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                Log.d(TAG, "onCharacteristicChanged: " + characteristic.getUuid().toString());
                
                if (characteristic.getUuid().equals(HEART_RATE_CHARACTERISTIC_UUID)) {
                    byte[] data = characteristic.getValue();
                    if (data != null && data.length > 0) {
                        // Analizar flags
                        byte flags = data[0];
                        int offset = 1;
                        
                        // Bit 0: Formato de Heart Rate (0: UINT8, 1: UINT16)
                        boolean isFormatUint16 = ((flags & 0x01) != 0);
                        
                        // Bit 3: Gasto de energía presente
                        boolean hasEnergyExpended = ((flags & 0x08) != 0);
                        
                        // Bit 4: Intervalos RR presentes
                        boolean hasRrIntervals = ((flags & 0x10) != 0);
                        
                        // Log para depuración
                        Log.d(TAG, "Heart Rate Data: format=" + (isFormatUint16 ? "UINT16" : "UINT8") + 
                                ", hasEnergy=" + hasEnergyExpended + 
                                ", hasRR=" + hasRrIntervals);
                        
                        // Leer frecuencia cardíaca
                        int heartRate;
                        if (isFormatUint16) {
                            heartRate = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                            offset += 2;
                        } else {
                            heartRate = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
                            offset += 1;
                        }
                        
                        Log.d(TAG, "Heart Rate Value: " + heartRate + " BPM");
                        
                        // Notificar frecuencia cardíaca
                        listener.onHeartRateUpdate(heartRate);
                        
                        // Saltar gasto de energía si está presente
                        if (hasEnergyExpended) {
                            offset += 2;  // Energy Expended usa 2 bytes (UINT16)
                        }
                        
                        // Leer intervalos RR si están presentes
                        if (hasRrIntervals) {
                            List<Integer> rrIntervals = new ArrayList<>();
                            
                            // Cada intervalo RR usa 2 bytes (UINT16)
                            // y puede haber varios en un solo paquete
                            while (offset + 1 < data.length) {
                                int rrInterval = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                                
                                // Convertir de 1/1024 segundos a milisegundos
                                float rrMs = rrInterval * 1000f / 1024f;
                                
                                rrIntervals.add(Math.round(rrMs));
                                offset += 2;
                            }
                            
                            if (!rrIntervals.isEmpty()) {
                                Log.d(TAG, "RR Intervals: " + rrIntervals.size() + " values");
                                listener.onRRIntervalsUpdate(rrIntervals);
                            }
                        }
                    } else {
                        Log.e(TAG, "Received empty heart rate data");
                    }
                } else {
                    Log.d(TAG, "Characteristic changed for unknown UUID: " + characteristic.getUuid().toString());
                }
            }
        });
    }

    public void cleanup() {
        if (bluetoothLeScanner != null && scanCallback != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothLeScanner.stopScan(scanCallback);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping BLE scan", e);
            }
        }
        
        if (bluetoothGatt != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothGatt.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing GATT connection", e);
            }
            bluetoothGatt = null;
        }
        
        // Eliminar cualquier callback pendiente
        timeoutHandler.removeCallbacksAndMessages(null);
    }
    
    /**
     * Verifica si el adaptador Bluetooth está disponible y habilitado
     */
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }
    
    /**
     * Inicia la búsqueda de dispositivos Bluetooth LE
     */
    public void startScan() {
        // Limpiar lista anterior
        devices.clear();
        
        // Verificar si el adaptador está habilitado
        if (!isBluetoothEnabled()) {
            if (listener != null) {
                listener.onBluetoothError(ERROR_SCAN_FAILED);
            }
            return;
        }
        
        // Obtener el escáner BLE
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        
        if (bluetoothLeScanner == null) {
            if (listener != null) {
                listener.onBluetoothError(ERROR_SCAN_FAILED);
            }
            return;
        }
        
        // Verificar permisos para Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                if (listener != null) {
                    listener.onBluetoothError(ERROR_SCAN_FAILED);
                }
                return;
            }
        }
        
        // Iniciar escaneo
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                
                try {
                    String deviceName = null;
                    String deviceAddress = null;
                    
                    // Verificar permiso para obtener el nombre del dispositivo
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            deviceName = device.getName();
                            deviceAddress = device.getAddress();
                        }
                    } else {
                        deviceName = device.getName();
                        deviceAddress = device.getAddress();
                    }
                    
                    // Verificar si el dispositivo ya está en la lista
                    boolean deviceExists = false;
                    for (BluetoothDeviceInfo info : devices) {
                        if (info.getAddress().equals(deviceAddress)) {
                            deviceExists = true;
                            break;
                        }
                    }
                    
                    // Añadir a la lista si es nuevo
                    if (!deviceExists && deviceAddress != null) {
                        devices.add(new BluetoothDeviceInfo(deviceName, deviceAddress));
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception during scan", e);
                }
            }
            
            @Override
            public void onScanFailed(int errorCode) {
                if (listener != null) {
                    listener.onBluetoothError(ERROR_SCAN_FAILED);
                }
            }
        };
        
        // Iniciar escaneo
        bluetoothLeScanner.startScan(scanCallback);
        
        // Establecer timeout
        timeoutHandler.postDelayed(this::stopScan, SCAN_TIMEOUT);
    }
    
    /**
     * Cierra todas las conexiones y limpia recursos
     */
    public void close() {
        cleanup();
    }
    
    /**
     * Desconecta del dispositivo actual
     */
    public void disconnect() {
        if (bluetoothGatt != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothGatt.disconnect();
                    }
                } else {
                    bluetoothGatt.disconnect();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting from device", e);
            }
        }
    }

    /**
     * Comprueba si hay un dispositivo conectado actualmente
     * @return true si hay un dispositivo conectado, false en caso contrario
     */
    public boolean isDeviceConnected() {
        return bluetoothGatt != null;
    }
}
