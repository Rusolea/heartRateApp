package com.heartratemonitor.heartratemonitor;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 123;

    // Bluetooth related fields
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private final UUID HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    private final UUID HEART_RATE_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");
    private final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // UI Components
    private TextView heartRateTextView;
    private TextView statusTextView;
    private TextView timerTextView;
    private LinearLayout zonesContainer;
    private EditText trainingName;
    private EditText trainingComment;
    private ImageButton btnStart;
    private ImageButton btnPause;
    private ImageButton btnStop;

    // Training state and data
    private enum TrainingState {
        RUNNING,
        PAUSED,
        STOPPED
    }
    private TrainingState trainingState = TrainingState.STOPPED;
    private long startTime;
    private long elapsedTime;
    private int totalHeartRate;
    private int heartRateReadings;
    private final int[] zoneThresholds = new int[5];
    private boolean isScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!initializeViews() || !setupBluetooth()) {
            return;
        }

        setupTrainingControls();
        setupHeartRateZones();
        checkPermissions();
    }

    private boolean initializeViews() {
        heartRateTextView = findViewById(R.id.heartRateTextView);
        statusTextView = findViewById(R.id.statusTextView);
        timerTextView = findViewById(R.id.timerTextView);
        zonesContainer = findViewById(R.id.zonesContainer);
        trainingName = findViewById(R.id.trainingName);
        trainingComment = findViewById(R.id.trainingComment);
        btnStart = findViewById(R.id.btnStart);
        btnPause = findViewById(R.id.btnPause);
        btnStop = findViewById(R.id.btnStop);

        if (anyViewIsNull()) {
            Log.e(TAG, "One or more views were not found in the layout");
            Toast.makeText(this, "Error loading interface", Toast.LENGTH_LONG).show();
            finish();
            return false;
        }

        return true;
    }

    private boolean anyViewIsNull() {
        return heartRateTextView == null || statusTextView == null || timerTextView == null ||
                zonesContainer == null || trainingName == null || trainingComment == null ||
                btnStart == null || btnPause == null || btnStop == null;
    }

    private boolean setupBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }

        return true;
    }

    private void setupTrainingControls() {
        btnStart.setOnClickListener(v -> {
            if (trainingState == TrainingState.STOPPED) {
                startNewTraining();
            } else if (trainingState == TrainingState.PAUSED) {
                resumeTraining();
            }
        });

        btnPause.setOnClickListener(v -> pauseTraining());
        btnStop.setOnClickListener(v -> {
            if (trainingState != TrainingState.STOPPED) {
                showStopConfirmationDialog();
            }
        });

        updateButtonVisibility();
    }

    private void setupHeartRateZones() {
        // Calculate thresholds based on max heart rate (example for age 30)
        int maxHR = 220 - 30;
        for (int i = 0; i < 5; i++) {
            zoneThresholds[i] = (int) ((i + 5) * 0.1 * maxHR);
        }

        createZoneViews();
    }

    private void createZoneViews() {
        String[] zoneNames = {"Zone 1", "Zone 2", "Zone 3", "Zone 4", "Zone 5"};
        int[] zoneColors = {
                R.color.zone1,
                R.color.zone2,
                R.color.zone3,
                R.color.zone4,
                R.color.zone5
        };

        zonesContainer.removeAllViews();

        for (int i = 0; i < 5; i++) {
            TextView zoneView = new TextView(this);
            zoneView.setText(String.format("%s\n(%d-%d BPM)",
                    zoneNames[i],
                    i == 0 ? 0 : zoneThresholds[i-1],
                    zoneThresholds[i]));
            zoneView.setTextSize(16);
            zoneView.setGravity(Gravity.CENTER);
            zoneView.setPadding(16, 16, 16, 16);
            zoneView.setBackgroundColor(getResources().getColor(zoneColors[i]));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 8, 0, 8);
            zonesContainer.addView(zoneView, params);
        }
    }

    private void updateZones(int heartRate) {
        for (int i = 0; i < zonesContainer.getChildCount(); i++) {
            TextView zoneView = (TextView) zonesContainer.getChildAt(i);
            boolean isInZone = (i == 0 && heartRate < zoneThresholds[0]) ||
                    (i == 4 && heartRate >= zoneThresholds[3]) ||
                    (i > 0 && i < 4 && heartRate >= zoneThresholds[i-1] && heartRate < zoneThresholds[i]);

            zoneView.setAlpha(isInZone ? 1.0f : 0.3f);
        }
    }

    private void startNewTraining() {
        trainingState = TrainingState.RUNNING;
        startTime = System.currentTimeMillis();
        elapsedTime = 0;
        totalHeartRate = 0;
        heartRateReadings = 0;
        updateButtonVisibility();
        startTimer();
    }

    private void resumeTraining() {
        trainingState = TrainingState.RUNNING;
        startTime = System.currentTimeMillis() - elapsedTime;
        updateButtonVisibility();
        startTimer();
    }

    private void pauseTraining() {
        trainingState = TrainingState.PAUSED;
        elapsedTime = System.currentTimeMillis() - startTime;
        updateButtonVisibility();
    }

    private void stopTraining() {
        trainingState = TrainingState.STOPPED;
        saveTrainingData();
        resetTrainingData();
        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        switch (trainingState) {
            case RUNNING:
                btnStart.setVisibility(View.GONE);
                btnPause.setVisibility(View.VISIBLE);
                btnStop.setVisibility(View.VISIBLE);
                break;
            case PAUSED:
                btnStart.setVisibility(View.VISIBLE);
                btnPause.setVisibility(View.GONE);
                btnStop.setVisibility(View.VISIBLE);
                break;
            case STOPPED:
                btnStart.setVisibility(View.VISIBLE);
                btnPause.setVisibility(View.GONE);
                btnStop.setVisibility(View.GONE);
                break;
        }
    }

    private void startTimer() {
        new Thread(() -> {
            while (trainingState == TrainingState.RUNNING) {
                updateTimerDisplay();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Timer interrupted", e);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    private void updateTimerDisplay() {
        long currentElapsed = (trainingState == TrainingState.RUNNING)
                ? System.currentTimeMillis() - startTime
                : elapsedTime;

        runOnUiThread(() -> {
            long hours = currentElapsed / 3600000;
            long minutes = (currentElapsed / 60000) % 60;
            long seconds = (currentElapsed / 1000) % 60;
            timerTextView.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        });
    }

    private void saveTrainingData() {
        String name = trainingName.getText().toString();
        String comment = trainingComment.getText().toString();
        double averageHR = heartRateReadings > 0 ? (double) totalHeartRate / heartRateReadings : 0;

        // TODO: Implement actual data saving
        Log.d(TAG, String.format("Training saved - Name: %s, Avg HR: %.1f, Duration: %d sec",
                name, averageHR, elapsedTime / 1000));
    }

    private void resetTrainingData() {
        elapsedTime = 0;
        timerTextView.setText("00:00:00");
        heartRateTextView.setText("-- BPM");
        trainingName.setText("");
        trainingComment.setText("");
        totalHeartRate = 0;
        heartRateReadings = 0;
    }

    private void showStopConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Stop Training")
                .setMessage("Are you sure you want to stop this training session?")
                .setPositiveButton("Stop", (dialog, which) -> stopTraining())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Bluetooth and Permissions Management

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            startBleScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startBleScan();
            } else {
                Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }








    //hasta aca tengo

    private void startBleScan() {
        try {
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                statusTextView.setText(getString(R.string.error_bluetooth_disabled));
                return;
            }

            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner == null) {
                statusTextView.setText(getString(R.string.error_scanner_not_available));
                return;
            }

            // Verificar permiso para Android 12+ (API 31)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, getString(R.string.error_permissions_required), Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            statusTextView.setText(getString(R.string.status_scanning));
            bluetoothLeScanner.startScan(new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    BluetoothDevice device = result.getDevice();
                    Log.d("BLE_SCAN", "Dispositivo encontrado: " + device.getName() + " (" + device.getAddress() + ")");

                    if (result.getScanRecord() != null &&
                            result.getScanRecord().getServiceUuids() != null &&
                            result.getScanRecord().getServiceUuids().contains(new ParcelUuid(HEART_RATE_SERVICE_UUID))) {
                        Log.d("BLE_SCAN", "Servicio de frecuencia cardíaca encontrado en: " + device.getName());
                        stopScan();
                        connectToDevice(device);
                    }
                }


                @Override
                public void onScanFailed(int errorCode) {
                    Log.e("BLE", "Error al escanear: " + errorCode);
                    statusTextView.setText(getString(R.string.error_scan_failed, errorCode));
                }

            });
        } catch (SecurityException e) {
            statusTextView.setText(getString(R.string.error_permissions, e.getMessage()));
        }
    }


    private void stopScan() {
        try {
            if (bluetoothLeScanner != null) {
                bluetoothLeScanner.stopScan(new ScanCallback() {});
            }
        } catch (SecurityException e) {
            statusTextView.setText(getString(R.string.error_stop_scan, e.getMessage()));
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        try {
            // Verificar permiso para Android 12+ (API 31)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, getString(R.string.error_permissions_required), Toast.LENGTH_SHORT).show();
                    return; // Detener si no hay permisos
                }
            }

            // Conectar al dispositivo
            statusTextView.setText(getString(R.string.status_connecting));
            bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        runOnUiThread(() -> statusTextView.setText(getString(R.string.status_discovering_services)));
                        gatt.discoverServices();
                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        runOnUiThread(() -> statusTextView.setText(getString(R.string.status_disconnected)));
                        bluetoothGatt = null;
                    }
                }


                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // Obtener la característica de frecuencia cardíaca
                        BluetoothGattCharacteristic characteristic = null;
                        if (gatt.getService(HEART_RATE_SERVICE_UUID) != null) {
                            characteristic = gatt
                                    .getService(HEART_RATE_SERVICE_UUID)
                                    .getCharacteristic(HEART_RATE_CHARACTERISTIC_UUID);
                        }

                        // Verificar si se encontró la característica
                        if (characteristic != null) {
                            gatt.setCharacteristicNotification(characteristic, true);

                            // Configurar el descriptor para habilitar notificaciones
                            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));

                            if (descriptor != null) {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
                            }

                            runOnUiThread(() -> statusTextView.setText(getString(R.string.status_ready)));
                        } else {
                            // Manejar el caso donde la característica no se encuentra
                            runOnUiThread(() -> statusTextView.setText(getString(R.string.error_characteristic_not_found)));
                        }
                    } else {
                        // Manejar el caso donde no se descubrieron los servicios correctamente
                        runOnUiThread(() -> statusTextView.setText(getString(R.string.error_services_discovery_failed, status)));
                    }
                }



                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    if (characteristic.getUuid().equals(HEART_RATE_CHARACTERISTIC_UUID)) {
                        int heartRate = parseHeartRate(characteristic.getValue());
                        totalHeartRate += heartRate;
                        heartRateReadings++;

                        runOnUiThread(() -> {
                            heartRateTextView.setText(heartRate + " BPM");
                            updateZones(heartRate);
                        });
                    }
                }




                private int parseHeartRate(byte[] data) {
                    int format = (data[0] & 0x01) == 0 ? BluetoothGattCharacteristic.FORMAT_UINT8
                            : BluetoothGattCharacteristic.FORMAT_UINT16;
                    return format == BluetoothGattCharacteristic.FORMAT_UINT8
                            ? data[1] & 0xFF
                            : ((data[2] & 0xFF) << 8) | (data[1] & 0xFF);
                }

            });
        } catch (SecurityException e) {
            statusTextView.setText(getString(R.string.error_permissions, e.getMessage()));
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan(); // Detener el escaneo BLE

        // Verificar y manejar el permiso BLUETOOTH_CONNECT
        if (bluetoothGatt != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // Si no se tiene el permiso, no ejecutar bluetoothGatt.close()
                    return;
                }
            }
            bluetoothGatt.close(); // Cerrar la conexión Bluetooth GATT
        }
    }

}
