package com.heartratemonitor.heartratemonitor.bluetooth;

import java.util.List;

public interface BluetoothListener {
    void onStatusUpdate(String status);
    void onBluetoothError(int errorCode);
    void onHeartRateUpdate(int heartRate);
    void onRRIntervalsUpdate(List<Integer> rrIntervals);
    void onScanComplete(List<BluetoothHandler.BluetoothDeviceInfo> devices);
}
