package com.heartratemonitor.heartratemonitor;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
    
    private PermissionHandler permissionHandler;
    private BluetoothHandler bluetoothHandler;
    private DatabaseHelper dbHelper;
    
    private FloatingActionButton fab;
    private MonitorFragment monitorFragment;
    
    private boolean isMonitoring = false;
    private long currentSessionId = -1;
    private ArrayList<Integer> rrIntervals = new ArrayList<>();
    private HRVAnalyzer hrvAnalyzer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Configurar la toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        
        // Inicializar FAB
        fab = findViewById(R.id.fab);
        if (fab != null) {
            fab.setOnClickListener(view -> {
                if (isMonitoring) {
                    stopMonitoring();
                } else {
                    startMonitoring();
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
        
        // Verificar permisos
        permissionHandler.checkPermissions();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_history) {
            showFragment(new HistoryFragment(), "history");
            return true;
        } else if (id == R.id.action_hrv) {
            // Mostramos una lista de sesiones para seleccionar
            showSessionSelectionDialog();
            return true;
        } else if (id == R.id.action_settings) {
            showFragment(new SettingsFragment(), "settings");
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
                })
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> {
                    stopMonitoring();
                })
                .show();
    }
    
    public void onDeviceConnected(String deviceName) {
        Toast.makeText(this, getString(R.string.connected_to, deviceName), Toast.LENGTH_SHORT).show();
        if (monitorFragment != null && monitorFragment.isAdded()) {
            monitorFragment.updateConnectionStatus(getString(R.string.connected_to, deviceName));
        }
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
        // Ocultar FAB si no estamos en la pantalla de monitoreo
        if (!"monitor".equals(tag)) {
            fab.hide();
        } else {
            fab.show();
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
        
        // Crear nueva sesión
        currentSessionId = createNewSession();
        rrIntervals.clear(); // Limpiar datos anteriores
        
        // Actualizar UI
        updateFabIcon(true);
        isMonitoring = true;
        
        if (monitorFragment != null && monitorFragment.isAdded()) {
            monitorFragment.updateMonitoringState(true);
        }
    }
    
    private void stopMonitoring() {
        // Detener monitoreo de dispositivo
        bluetoothHandler.disconnect();
        
        // Finalizar la sesión en la base de datos
        if (currentSessionId != -1) {
            finalizeSession(currentSessionId);
        }
        
        // Actualizar UI
        updateFabIcon(false);
        isMonitoring = false;
        
        if (monitorFragment != null && monitorFragment.isAdded()) {
            monitorFragment.updateMonitoringState(false);
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
            updateFabIcon(true);
            isMonitoring = true;
            
            if (monitorFragment != null && monitorFragment.isAdded()) {
                monitorFragment.updateMonitoringState(true);
            }
        } else {
            // Si no hay Bluetooth, detener monitoreo
            stopMonitoring();
        }
    }
    
    private void updateFabIcon(boolean isMonitoring) {
        if (isMonitoring) {
            fab.setImageResource(R.drawable.ic_stop);
            fab.setContentDescription(getString(R.string.fab_stop_monitoring));
        } else {
            fab.setImageResource(R.drawable.ic_play);
            fab.setContentDescription(getString(R.string.fab_start_monitoring));
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
        
        // Guardar los datos de HRV
        hrvAnalyzer = new HRVAnalyzer();
        double[] rrData = new double[rrIntervals.size()];
        for (int i = 0; i < rrIntervals.size(); i++) {
            rrData[i] = rrIntervals.get(i);
        }
        
        session.setSdnn(hrvAnalyzer.calculateSDNN(rrData));
        session.setRmssd(hrvAnalyzer.calculateRMSSD(rrData));
        session.setPnn50(hrvAnalyzer.calculatePNN50(rrData));
        
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
}
