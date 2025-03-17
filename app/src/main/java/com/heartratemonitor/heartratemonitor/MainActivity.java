package com.heartratemonitor.heartratemonitor;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.heartratemonitor.heartratemonitor.bluetooth.BluetoothHandler;
import com.heartratemonitor.heartratemonitor.bluetooth.BluetoothListener;
import com.heartratemonitor.heartratemonitor.database.DatabaseHelper;
import com.heartratemonitor.heartratemonitor.models.HeartRateData;
import com.heartratemonitor.heartratemonitor.utils.HRVAnalyzer;
import com.heartratemonitor.heartratemonitor.models.WorkoutSession;
import com.heartratemonitor.heartratemonitor.fragments.HistoryFragment;
import com.heartratemonitor.heartratemonitor.fragments.HRVFragment;
import com.heartratemonitor.heartratemonitor.fragments.MonitorFragment;
import com.heartratemonitor.heartratemonitor.fragments.SettingsFragment;
import com.heartratemonitor.heartratemonitor.permissions.PermissionHandler;
import com.heartratemonitor.heartratemonitor.services.FloatingViewService;
import com.heartratemonitor.heartratemonitor.services.HeartRateService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements 
        BluetoothListener, 
        PermissionHandler.PermissionListener,
        MonitorFragment.MonitorFragmentListener,
        HistoryFragment.HistoryFragmentListener,
        HRVFragment.HRVFragmentListener,
        SettingsFragment.SettingsFragmentListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_OVERLAY_PERMISSION = 2;
    
    // Estados de monitoreo
    private static final int STATE_IDLE = 0;
    private static final int STATE_MONITORING = 1;
    private static final int STATE_PAUSED = 2;
    
    private PermissionHandler permissionHandler;
    private BluetoothHandler bluetoothHandler;
    private DatabaseHelper dbHelper;
    
    private FloatingActionButton fab;
    private FloatingActionButton fabStop;
    private MonitorFragment monitorFragment;
    
    private boolean isMonitoring = false;
    private int monitoringState = STATE_IDLE;
    private long currentSessionId = -1;
    private ArrayList<Integer> rrIntervals = new ArrayList<>();
    private HRVAnalyzer hrvAnalyzer;
    
    // Variable para controlar el estado del servicio flotante
    private boolean isFloatingServiceRunning = false;

    // BroadcastReceiver para capturar eventos del servicio
    private BroadcastReceiver serviceReceiver;
    private static final String ACTION_SERVICE_STOPPED = "com.heartratemonitor.heartratemonitor.SERVICE_STOPPED";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Configurar la toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        
        // Inicializar FABs
        fab = findViewById(R.id.fab);
        fabStop = findViewById(R.id.fab_stop);
        
        if (fab != null) {
            fab.setOnClickListener(view -> {
                switch (monitoringState) {
                    case STATE_IDLE:
                        startMonitoring();
                        break;
                    case STATE_MONITORING:
                        pauseMonitoring();
                        break;
                    case STATE_PAUSED:
                        resumeMonitoring();
                        break;
                }
            });
        }
        
        if (fabStop != null) {
            fabStop.setOnClickListener(view -> {
                if (monitoringState == STATE_MONITORING || monitoringState == STATE_PAUSED) {
                    stopMonitoring();
                }
            });
        }
        
        // Inicializar el fragmento de monitor
        if (savedInstanceState == null) {
            monitorFragment = new MonitorFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, monitorFragment, "monitor")
                    .commit();
        } else {
            monitorFragment = (MonitorFragment) getSupportFragmentManager().findFragmentByTag("monitor");
            if (monitorFragment == null) {
                monitorFragment = new MonitorFragment();
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, monitorFragment, "monitor")
                        .commit();
            }
        }
        
        // Configurar la frecuencia cardíaca máxima en el fragmento de monitor
        // Esto debería venir de las preferencias del usuario
        // Por defecto podríamos usar la fórmula 220 - edad
        int userAge = getUserAgeFromPreferences(); // Implementar esta función
        if (userAge > 0) {
            int maxHeartRate = 220 - userAge;
            if (monitorFragment != null) {
                monitorFragment.setMaxHeartRate(maxHeartRate);
            }
        }
        
        // Inicializar manejadores
        permissionHandler = new PermissionHandler(this);
        permissionHandler.setPermissionListener(this);
        bluetoothHandler = new BluetoothHandler(this, this);
        dbHelper = new DatabaseHelper(this);
        
        // Inicializar y registrar el BroadcastReceiver
        setupServiceReceiver();
        
        // Verificar permisos
        permissionHandler.checkPermissions();
    }
    
    private void setupServiceReceiver() {
        serviceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_SERVICE_STOPPED.equals(intent.getAction())) {
                    // El servicio ha sido detenido desde la notificación
                    // Si la medición estaba activa, finalizamos la sesión
                    if (isMonitoring && currentSessionId != -1) {
                        // Actualizar UI y finalizar sesión
                        runOnUiThread(() -> {
                            stopMonitoring();
                        });
                    }
                }
            }
        };
        
        // Registrar el receptor
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SERVICE_STOPPED);
        registerReceiver(serviceReceiver, filter);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        
        // Añadir opción para activar/desactivar vista flotante
        MenuItem floatingItem = menu.findItem(R.id.action_floating_view);
        floatingItem.setTitle(isFloatingServiceRunning ? R.string.floating_view_disable : R.string.floating_view_enable);
        
        // Configurar visibilidad de botones según el estado de la conexión
        MenuItem searchItem = menu.findItem(R.id.action_search_device);
        MenuItem disconnectItem = menu.findItem(R.id.action_disconnect);
        
        // Si hay un dispositivo conectado, mostrar botón de desconectar y ocultar buscar
        boolean isDeviceConnected = bluetoothHandler != null && bluetoothHandler.isDeviceConnected();
        searchItem.setVisible(!isDeviceConnected);
        disconnectItem.setVisible(isDeviceConnected);
        
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_search_device) {
            startDeviceSearch();
            return true;
        } else if (id == R.id.action_disconnect) {
            disconnectDevice();
            return true;
        } else if (id == R.id.action_history) {
            showFragment(new HistoryFragment(), "history");
            return true;
        } else if (id == R.id.action_hrv) {
            // Mostramos una lista de sesiones para seleccionar
            showSessionSelectionDialog();
            return true;
        } else if (id == R.id.action_settings) {
            showFragment(new SettingsFragment(), "settings");
            return true;
        } else if (id == R.id.action_floating_view) {
            toggleFloatingView();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isMonitoring) {
            stopMonitoring();
        }
        
        // Deregistrar el BroadcastReceiver
        if (serviceReceiver != null) {
            unregisterReceiver(serviceReceiver);
            serviceReceiver = null;
        }
        
        bluetoothHandler.close();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionHandler.handlePermissionResult(requestCode, permissions, grantResults);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                // Bluetooth activado, continuar
                checkBluetoothAndScan();
            } else {
                // Bluetooth no activado
                Toast.makeText(this, R.string.error_bluetooth_disabled, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingViewService();
            } else {
                Toast.makeText(this, "Permiso de superposición denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    // PermissionHandler.PermissionListener
    @Override
    public void onPermissionGranted(String permission) {
        if (permission.equals(PermissionHandler.BLUETOOTH_PERMISSION)) {
            startMonitoring();
        }
    }
    
    @Override
    public void onPermissionDenied(String permission) {
        Toast.makeText(this, R.string.bluetooth_permission_denied, Toast.LENGTH_LONG).show();
    }
    
    // BluetoothListener
    @Override
    public void onStatusUpdate(String status) {
        if (monitorFragment != null && monitorFragment.isAdded()) {
            monitorFragment.updateConnectionStatus(status);
        }
    }
    
    @Override
    public void onBluetoothError(int errorCode) {
        String errorMessage;
        switch (errorCode) {
            case BluetoothHandler.ERROR_SCAN_FAILED:
                errorMessage = getString(R.string.error_scan_failed_simple);
                break;
            case BluetoothHandler.ERROR_CONNECT_FAILED:
                errorMessage = getString(R.string.error_connect_failed);
                break;
            case BluetoothHandler.ERROR_SERVICE_NOT_FOUND:
                errorMessage = getString(R.string.error_service_not_found);
                break;
            default:
                errorMessage = getString(R.string.error_unknown);
                break;
        }
        
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
        stopMonitoring();
    }
    
    @Override
    public void onHeartRateUpdate(int heartRate) {
        if (monitorFragment != null && monitorFragment.isAdded()) {
            monitorFragment.updateHeartRate(heartRate);
        }
        
        // Guardar datos en la base de datos
        if (currentSessionId != -1) {
            HeartRateData data = new HeartRateData();
            data.setSessionId(currentSessionId);
            data.setHeartRate(heartRate);
            data.setTimestamp(System.currentTimeMillis());
            
            // Usar un hilo para insertar los datos, para no bloquear la UI
            new Thread(() -> dbHelper.insertHeartRateData(data)).start();
        }
    }
    
    @Override
    public void onRRIntervalsUpdate(List<Integer> intervals) {
        // Guardar intervalo RR para análisis de HRV
        rrIntervals.addAll(intervals);
        
        if (monitorFragment != null && monitorFragment.isAdded() && !intervals.isEmpty()) {
            monitorFragment.updateRRInterval(intervals.get(intervals.size() - 1));
        }
    }
    
    @Override
    public void onScanComplete(List<BluetoothHandler.BluetoothDeviceInfo> devices) {
        if (devices.isEmpty()) {
            Toast.makeText(this, R.string.no_heart_rate_devices, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Obtener nombres de dispositivos
        final String[] deviceNames = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            deviceNames[i] = devices.get(i).getName();
        }
        
        // Mostrar diálogo con dispositivos disponibles
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_select_device)
                .setItems(deviceNames, (dialog, which) -> {
                    // Conectar con el dispositivo seleccionado
                    bluetoothHandler.connectToDevice(devices.get(which).getAddress());
                    
                    // Iniciar el servicio en primer plano
                    startHeartRateService(devices.get(which).getAddress());
                })
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> {
                    stopMonitoring();
                })
                .show();
    }
    
    public void onDeviceConnected(String deviceName) {
        Toast.makeText(this, getString(R.string.connected_to, deviceName), Toast.LENGTH_SHORT).show();
        if (monitorFragment != null && monitorFragment.isAdded()) {
            monitorFragment.updateConnectionStatus(getString(R.string.device_connected_start_measurement));
        }
        
        // Actualizar opciones del menú para mostrar botón de desconectar
        invalidateOptionsMenu();
    }
    
    public void onDeviceDisconnected() {
        Toast.makeText(this, R.string.device_disconnected, Toast.LENGTH_SHORT).show();
        if (monitorFragment != null && monitorFragment.isAdded()) {
            monitorFragment.updateConnectionStatus(getString(R.string.status_disconnected));
        }
    }
    
    // MonitorFragmentListener
    public boolean isMonitoringActive() {
        return isMonitoring;
    }
    
    @Override
    public void onHeartRateUpdated(int heartRate) {
        // Implementar lógica para manejar actualizaciones de frecuencia cardíaca
        // Por ejemplo, guardar el valor para análisis posterior
        if (hrvAnalyzer != null) {
            // También podríamos actualizar otros componentes de la UI aquí
        }
    }
    
    @Override
    public void onRRIntervalUpdated(int rrInterval) {
        // Implementar lógica para manejar actualizaciones de intervalos RR
        if (hrvAnalyzer != null) {
            hrvAnalyzer.addRRInterval(rrInterval);
        }
    }
    
    @Override
    public void onConnectionStatusChanged(String status) {
        // Implementar lógica para manejar cambios de estado de conexión
        // Por ejemplo, actualizar la UI o tomar acciones basadas en el estado
        runOnUiThread(() -> {
            // Actualizar alguna UI si es necesario
        });
    }
    
    // HistoryFragmentListener
    @Override
    public void onSessionClicked(long sessionId) {
        HRVFragment hrvFragment = HRVFragment.newInstance(sessionId);
        showFragment(hrvFragment, "hrv");
    }
    
    @Override
    public void onSessionExportRequested(long sessionId, List<HeartRateData> heartRateDataList) {
        exportHeartRateData(sessionId, heartRateDataList);
    }
    
    @Override
    public void onSessionDeleteRequested(long sessionId) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_confirm_delete_title)
                .setMessage(R.string.dialog_confirm_delete_message)
                .setPositiveButton(R.string.dialog_delete, (dialog, which) -> {
                    if (dbHelper.deleteSession(sessionId)) {
                        Toast.makeText(this, R.string.msg_session_deleted, Toast.LENGTH_SHORT).show();
                        
                        // Actualizar la lista de sesiones
                        if (getSupportFragmentManager().findFragmentByTag("history") instanceof HistoryFragment) {
                            ((HistoryFragment) getSupportFragmentManager().findFragmentByTag("history")).refreshSessionList();
                        }
                    } else {
                        Toast.makeText(this, R.string.error_deleting_session, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }
    
    // HRVFragmentListener
    @Override
    public void onHRVFragmentExportRequested(long sessionId, List<Integer> rrIntervals) {
        exportHRVData(sessionId, rrIntervals);
    }
    
    // SettingsFragmentListener
    @Override
    public void onSettingsSaved() {
        // Actualizar la UI con los nuevos ajustes
        if (monitorFragment != null && monitorFragment.isAdded()) {
            monitorFragment.updateHeartRateZones();
        }
    }
    
    private void showFragment(Fragment fragment, String tag) {
        if (isMonitoring) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_session_active_title)
                    .setMessage(R.string.dialog_session_active_message)
                    .setPositiveButton(R.string.dialog_stop_session, (dialog, which) -> {
                        stopMonitoring();
                        navigateToFragment(fragment, tag);
                    })
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
        } else {
            navigateToFragment(fragment, tag);
        }
    }
    
    private void navigateToFragment(Fragment fragment, String tag) {
        // Ocultar FABs si no estamos en la pantalla de monitoreo
        if (!"monitor".equals(tag)) {
            fab.hide();
            if (fabStop != null) {
                fabStop.hide();
            }
        } else {
            fab.show();
            // Solo mostrar el botón de detener si estamos monitoreando
            if (fabStop != null) {
                if (monitoringState == STATE_MONITORING || monitoringState == STATE_PAUSED) {
                    fabStop.show();
                } else {
                    fabStop.hide();
                }
            }
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragmentContainer, fragment, tag);
        
        // Si no estamos yendo al fragmento inicial (monitor), añadir a la pila trasera
        if (!"monitor".equals(tag)) {
            transaction.addToBackStack(null);
        }
        
        transaction.commit();
    }
    
    private void showSessionSelectionDialog() {
        List<WorkoutSession> sessions = dbHelper.getAllSessions();
        
        if (sessions.isEmpty()) {
            Toast.makeText(this, R.string.error_no_sessions, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Obtener títulos de las sesiones
        final String[] sessionTitles = new String[sessions.size()];
        final long[] sessionIds = new long[sessions.size()];
        
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        
        for (int i = 0; i < sessions.size(); i++) {
            WorkoutSession session = sessions.get(i);
            sessionTitles[i] = session.getTitle() + " - " + sdf.format(new Date(session.getStartTime()));
            sessionIds[i] = session.getId();
        }
        
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_select_session_title)
                .setItems(sessionTitles, (dialog, which) -> {
                    long sessionId = sessionIds[which];
                    HRVFragment hrvFragment = HRVFragment.newInstance(sessionId);
                    showFragment(hrvFragment, "hrv");
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }
    
    private void startMonitoring() {
        // Verificar si hay un dispositivo conectado
        if (bluetoothHandler != null && !bluetoothHandler.isDeviceConnected()) {
            Toast.makeText(this, R.string.no_device_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Crear nueva sesión
        currentSessionId = createNewSession();
        rrIntervals.clear(); // Limpiar datos anteriores
        
        // Actualizar UI
        updateFabIcon(STATE_MONITORING);
        monitoringState = STATE_MONITORING;
        isMonitoring = true;
        
        // Mostrar botón de detener
        if (fabStop != null) {
            fabStop.show();
        }
        
        if (monitorFragment != null && monitorFragment.isAdded()) {
            monitorFragment.updateMonitoringState(true);
            monitorFragment.updateConnectionStatus(getString(R.string.measuring));
        }
        
        Toast.makeText(this, R.string.measurement_in_progress, Toast.LENGTH_SHORT).show();
    }
    
    private void pauseMonitoring() {
        // Actualizar UI
        updateFabIcon(STATE_PAUSED);
        monitoringState = STATE_PAUSED;
        
        if (monitorFragment != null && monitorFragment.isAdded()) {
            monitorFragment.pauseMonitoring();
            monitorFragment.updateConnectionStatus(getString(R.string.measurement_paused));
        }
        
        Toast.makeText(this, R.string.measurement_paused, Toast.LENGTH_SHORT).show();
        
        // Pausar servicio de monitoreo
        pauseHeartRateService();
    }
    
    private void resumeMonitoring() {
        // Actualizar UI
        updateFabIcon(STATE_MONITORING);
        monitoringState = STATE_MONITORING;
        
        if (monitorFragment != null && monitorFragment.isAdded()) {
            monitorFragment.resumeMonitoring();
            monitorFragment.updateConnectionStatus(getString(R.string.measuring));
        }
        
        Toast.makeText(this, R.string.measurement_in_progress, Toast.LENGTH_SHORT).show();
        
        // Reanudar servicio de monitoreo
        resumeHeartRateService();
    }
    
    private void stopMonitoring() {
        // Finalizar la sesión en la base de datos
        if (currentSessionId != -1) {
            finalizeSession(currentSessionId);
        }
        
        // Actualizar UI
        updateFabIcon(STATE_IDLE);
        monitoringState = STATE_IDLE;
        isMonitoring = false;
        
        // Ocultar botón de detener
        if (fabStop != null) {
            fabStop.hide();
        }
        
        if (monitorFragment != null && monitorFragment.isAdded()) {
            monitorFragment.stopMonitoring();
            monitorFragment.updateConnectionStatus(getString(R.string.measurement_stopped));
        }
        
        Toast.makeText(this, R.string.measurement_stopped, Toast.LENGTH_SHORT).show();
        
        // Detener servicios si están activos
        stopHeartRateService();
        if (isFloatingServiceRunning) {
            stopFloatingViewService();
        }
    }
    
    private void checkBluetoothAndScan() {
        // Iniciar escaneo si Bluetooth está habilitado y tenemos permisos
        if (bluetoothHandler.isBluetoothEnabled() && permissionHandler.checkBluetoothPermissions()) {
            bluetoothHandler.startScan();
            
            // Crear nueva sesión
            currentSessionId = createNewSession();
            rrIntervals.clear(); // Limpiar datos anteriores
            
            // Actualizar UI
            updateFabIcon(STATE_MONITORING);
            monitoringState = STATE_MONITORING;
            isMonitoring = true;
            
            // Mostrar botón de detener
            if (fabStop != null) {
                fabStop.show();
            }
            
            if (monitorFragment != null && monitorFragment.isAdded()) {
                monitorFragment.updateMonitoringState(true);
            }
        } else {
            // Si no hay Bluetooth, detener monitoreo
            stopMonitoring();
        }
    }
    
    private void updateFabIcon(int state) {
        switch (state) {
            case STATE_MONITORING:
                fab.setImageResource(R.drawable.ic_pause);
                fab.setContentDescription(getString(R.string.fab_pause_monitoring));
                break;
            case STATE_PAUSED:
                fab.setImageResource(R.drawable.ic_play);
                fab.setContentDescription(getString(R.string.fab_resume_monitoring));
                break;
            case STATE_IDLE:
            default:
                fab.setImageResource(R.drawable.ic_play);
                fab.setContentDescription(getString(R.string.fab_start_monitoring));
                break;
        }
    }
    
    private long createNewSession() {
        WorkoutSession session = new WorkoutSession();
        session.setTitle(getString(R.string.session_default_title, 
                new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date())));
        session.setStartTime(System.currentTimeMillis());
        session.setCaloriesBurned(0); // Se actualizará al finalizar
        return dbHelper.insertSession(session);
    }
    
    private void finalizeSession(long sessionId) {
        // Obtener la sesión
        WorkoutSession session = dbHelper.getSessionById(sessionId);
        if (session == null) return;
        
        // Actualizar hora de finalización
        session.setEndTime(System.currentTimeMillis());
        
        // Calcular calorías quemadas (ejemplo simple, mejorar con algoritmos reales)
        long durationMinutes = (session.getEndTime() - session.getStartTime()) / 60000;
        double calories = durationMinutes * 5; // Valor arbitrario para ejemplo
        session.setCaloriesBurned((int)calories);
        
        // Calcular el promedio de la frecuencia cardíaca
        List<HeartRateData> heartRateDataList = dbHelper.getHeartRateDataForSession(sessionId);
        if (!heartRateDataList.isEmpty()) {
            int totalHeartRate = 0;
            int maxHeartRate = 0;
            
            for (HeartRateData data : heartRateDataList) {
                int heartRate = data.getHeartRate();
                totalHeartRate += heartRate;
                
                // Actualizar el máximo si es necesario
                if (heartRate > maxHeartRate) {
                    maxHeartRate = heartRate;
                }
            }
            
            int avgHeartRate = totalHeartRate / heartRateDataList.size();
            session.setAverageHeartRate(avgHeartRate);
            session.setMaxHeartRate(maxHeartRate);
        }
        
        // Guardar los datos de HRV
        hrvAnalyzer = new HRVAnalyzer();
        double[] rrData = new double[rrIntervals.size()];
        for (int i = 0; i < rrIntervals.size(); i++) {
            rrData[i] = rrIntervals.get(i);
        }
        
        session.setSdnn(hrvAnalyzer.calculateSDNN(rrData));
        session.setRmssd(hrvAnalyzer.calculateRMSSD(rrData));
        session.setPnn50(hrvAnalyzer.calculatePNN50(rrData));
        
        // Calcular la relación LF/HF
        double lfhfRatio = hrvAnalyzer.calculateLFHFRatio(rrData);
        session.setLfhfRatio(lfhfRatio);
        
        // Calcular puntuación HRV
        int hrvScore = hrvAnalyzer.calculateHRVScore(rrData);
        session.setHrvScore(hrvScore);
        
        // Actualizar la sesión
        dbHelper.updateSession(session);
        
        // Notificar que la sesión ha sido actualizada
        if (getSupportFragmentManager().findFragmentByTag("history") instanceof HistoryFragment) {
            ((HistoryFragment) getSupportFragmentManager().findFragmentByTag("history")).refreshSessionList();
        }
    }
    
    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            fab.show(); // Mostrar FAB cuando volvemos al fragmento de monitoreo
            // Solo mostrar el botón de detener si estamos monitoreando
            if (fabStop != null && (monitoringState == STATE_MONITORING || monitoringState == STATE_PAUSED)) {
                fabStop.show();
            }
        } else {
            super.onBackPressed();
        }
    }

    private void exportHeartRateData(long sessionId, List<HeartRateData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            Toast.makeText(this, R.string.error_no_data_to_export, Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Crear archivo CSV en almacenamiento externo
            File directory = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "HeartRateMonitor");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            
            WorkoutSession session = dbHelper.getSessionById(sessionId);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String fileName = "heartrate_" + dateFormat.format(new Date(session.getStartTime())) + ".csv";
            File file = new File(directory, fileName);
            
            // Escribir datos
            FileWriter writer = new FileWriter(file);
            writer.append("Timestamp,HeartRate(BPM)\n");
            
            SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            for (HeartRateData data : dataList) {
                writer.append(timestampFormat.format(new Date(data.getTimestamp())))
                      .append(",")
                      .append(String.valueOf(data.getHeartRate()))
                      .append("\n");
            }
            
            writer.flush();
            writer.close();
            
            // Compartir archivo
            shareFile(file, "text/csv");
            
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.error_exporting_data, Toast.LENGTH_LONG).show();
        }
    }

    private void exportHRVData(long sessionId, List<Integer> rrIntervals) {
        if (rrIntervals == null || rrIntervals.isEmpty()) {
            Toast.makeText(this, R.string.error_no_data_to_export, Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Crear archivo CSV en almacenamiento externo
            File directory = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "HeartRateMonitor");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            
            WorkoutSession session = dbHelper.getSessionById(sessionId);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String fileName = "hrv_" + dateFormat.format(new Date(session.getStartTime())) + ".csv";
            File file = new File(directory, fileName);
            
            // Escribir datos
            FileWriter writer = new FileWriter(file);
            writer.append("Index,RR_Interval(ms)\n");
            
            for (int i = 0; i < rrIntervals.size(); i++) {
                writer.append(String.valueOf(i + 1))
                      .append(",")
                      .append(String.valueOf(rrIntervals.get(i)))
                      .append("\n");
            }
            
            writer.flush();
            writer.close();
            
            // Compartir archivo
            shareFile(file, "text/csv");
            
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.error_exporting_data, Toast.LENGTH_LONG).show();
        }
    }

    private void shareFile(File file, String mimeType) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        
        // Generar URI usando FileProvider
        Uri fileUri = FileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() + ".provider", file);
        
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_file)));
    }

    // Método para obtener la edad del usuario desde las preferencias
    private int getUserAgeFromPreferences() {
        // Obtener preferencias compartidas
        android.content.SharedPreferences prefs = getSharedPreferences("UserSettings", MODE_PRIVATE);
        
        // Obtener la edad del usuario (por defecto 30 si no está configurada)
        return prefs.getInt("user_age", 30);
    }

    // Métodos para el manejo de la vista flotante y servicio en primer plano
    
    private void toggleFloatingView() {
        if (isFloatingServiceRunning) {
            stopFloatingViewService();
        } else {
            if (checkOverlayPermission()) {
                startFloatingViewService();
            }
        }
    }
    
    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // Mostrar diálogo explicando por qué se necesita el permiso
            new AlertDialog.Builder(this)
                    .setTitle(R.string.overlay_permission_required)
                    .setMessage(R.string.overlay_permission_message)
                    .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                    })
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
            return false;
        }
        return true;
    }
    
    private void startFloatingViewService() {
        if (!isMonitoring) {
            Toast.makeText(this, R.string.monitoring_required, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Iniciar el servicio de vista flotante
        Intent intent = new Intent(this, FloatingViewService.class);
        startService(intent);
        isFloatingServiceRunning = true;
        
        Toast.makeText(this, R.string.floating_view_enabled, Toast.LENGTH_SHORT).show();
    }
    
    private void stopFloatingViewService() {
        Intent intent = new Intent(this, FloatingViewService.class);
        intent.putExtra("stop_service", true);
        startService(intent);
        isFloatingServiceRunning = false;
        
        Toast.makeText(this, R.string.floating_view_disabled, Toast.LENGTH_SHORT).show();
    }
    
    private void startHeartRateService(String deviceAddress) {
        // Iniciar el servicio en primer plano para monitorear la frecuencia cardíaca
        Intent intent = new Intent(this, HeartRateService.class);
        intent.putExtra("deviceAddress", deviceAddress);
        intent.putExtra("maxHeartRate", getUserMaxHeartRate());
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
    
    private void pauseHeartRateService() {
        Intent intent = new Intent(this, HeartRateService.class);
        intent.setAction("com.heartratemonitor.heartratemonitor.PAUSE_SERVICE");
        startService(intent);
    }
    
    private void resumeHeartRateService() {
        Intent intent = new Intent(this, HeartRateService.class);
        intent.setAction("com.heartratemonitor.heartratemonitor.RESUME_SERVICE");
        startService(intent);
    }
    
    private void stopHeartRateService() {
        Intent intent = new Intent(this, HeartRateService.class);
        intent.setAction("com.heartratemonitor.heartratemonitor.STOP_SERVICE");
        startService(intent);
    }
    
    private int getUserMaxHeartRate() {
        // Obtener la frecuencia cardíaca máxima del usuario
        return 220 - getUserAgeFromPreferences();
    }

    // Nuevo método para iniciar la búsqueda de dispositivos
    private void startDeviceSearch() {
        // Verificar permisos Bluetooth
        if (!permissionHandler.checkBluetoothPermissions()) {
            permissionHandler.requestBluetoothPermissions();
            return;
        }
        
        // Verificar si Bluetooth está habilitado
        if (!bluetoothHandler.isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, BluetoothHandler.REQUEST_ENABLE_BT);
            return;
        }
        
        // Iniciar escaneo de dispositivos
        bluetoothHandler.startScan();
        
        // Actualizar UI para mostrar que estamos buscando
        if (monitorFragment != null && monitorFragment.isAdded()) {
            monitorFragment.updateConnectionStatus(getString(R.string.searching_devices));
        }
    }
    
    // Nuevo método para desconectar el dispositivo
    private void disconnectDevice() {
        // Detener monitoreo si está activo
        if (isMonitoring) {
            stopMonitoring();
        } else {
            // Solo desconectar el dispositivo
            bluetoothHandler.disconnect();
            
            // Actualizar la UI
            if (monitorFragment != null && monitorFragment.isAdded()) {
                monitorFragment.updateConnectionStatus(getString(R.string.device_disconnected_message));
            }
            
            Toast.makeText(this, R.string.device_disconnected_message, Toast.LENGTH_SHORT).show();
        }
        
        // Ocultar el botón de desconexión
        invalidateOptionsMenu();
    }
}
