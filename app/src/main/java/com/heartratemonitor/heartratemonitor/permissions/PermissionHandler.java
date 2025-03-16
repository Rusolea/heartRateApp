package com.heartratemonitor.heartratemonitor.permissions;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Manejador de permisos para la aplicación Heart Rate Monitor
 * Se encarga de verificar y solicitar los permisos necesarios para el funcionamiento
 * de Bluetooth y el acceso a la ubicación (necesario para escaneo BLE)
 */
public class PermissionHandler {
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    
    public static final String BLUETOOTH_PERMISSION = "bluetooth_permission";
    public static final String LOCATION_PERMISSION = "location_permission";
    
    private Activity activity;
    private PermissionListener permissionListener;
    
    public interface PermissionListener {
        void onPermissionGranted(String permission);
        void onPermissionDenied(String permission);
    }
    
    public PermissionHandler(Activity activity) {
        this.activity = activity;
    }
    
    public void setPermissionListener(PermissionListener listener) {
        this.permissionListener = listener;
    }
    
    /**
     * Verifica si la aplicación tiene los permisos necesarios para Bluetooth
     */
    public boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Para Android 12 (API 31) y superiores
            return checkBluetoothPermissions();
        } else {
            // Para versiones anteriores a Android 12
            return checkLocationPermission();
        }
    }
    
    /**
     * Verifica específicamente los permisos de Bluetooth
     */
    public boolean checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Permisos para Android 12+
            boolean connect = ContextCompat.checkSelfPermission(activity, 
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
                    
            boolean scan = ContextCompat.checkSelfPermission(activity, 
                    Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
                    
            return connect && scan;
        } else {
            // Permisos para Android 11 y anteriores
            boolean admin = ContextCompat.checkSelfPermission(activity, 
                    Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
                    
            boolean normal = ContextCompat.checkSelfPermission(activity, 
                    Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
                    
            return admin && normal && checkLocationPermission();
        }
    }
    
    /**
     * Verifica el permiso de ubicación (necesario para escaneo BLE en versiones anteriores)
     */
    public boolean checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+
            return ContextCompat.checkSelfPermission(activity, 
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 9 y anteriores
            return ContextCompat.checkSelfPermission(activity, 
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * Solicita permisos para Bluetooth
     */
    public void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Solicitar permisos para Android 12+
            ActivityCompat.requestPermissions(activity, 
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN, 
                            Manifest.permission.BLUETOOTH_CONNECT
                    }, 
                    REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            // Solicitar permisos para Android 11 y anteriores
            String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions = new String[]{
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                };
            } else {
                permissions = new String[]{
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                };
            }
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }
    
    /**
     * Maneja la respuesta a la solicitud de permisos
     */
    public void handlePermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            // Verificar si todos los permisos fueron concedidos
            boolean allGranted = true;
            
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                if (permissionListener != null) {
                    permissionListener.onPermissionGranted(BLUETOOTH_PERMISSION);
                }
            } else {
                if (permissionListener != null) {
                    permissionListener.onPermissionDenied(BLUETOOTH_PERMISSION);
                }
            }
        }
    }
} 