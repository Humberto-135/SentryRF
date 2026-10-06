package com.sentryrf.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.Locale;

final class RadioObserver {
    interface Listener {
        void onBle(String id, String name, int rssi, ScanRecord record);
        void onWifi(android.net.wifi.ScanResult result);
        void onStatus(String message);
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner bleScanner;
    private WifiManager wifiManager;
    private boolean running;
    private boolean wifiReceiverRegistered;

    private final Runnable wifiLoop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            scanWifiOnce();
            handler.postDelayed(this, 30_000L);
        }
    };

    private final ScanCallback bleCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            dispatchBle(result);
        }
        @Override public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult r : results) dispatchBle(r);
        }
        @Override public void onScanFailed(int errorCode) {
            listener.onStatus("BLE scan error " + errorCode);
        }
    };

    private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (!WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) return;
            readWifiResults();
        }
    };

    RadioObserver(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    boolean isRunning() { return running; }

    @SuppressLint("MissingPermission")
    void start() {
        if (running) return;
        running = true;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter != null && adapter.isEnabled()) {
            bleScanner = adapter.getBluetoothLeScanner();
            if (bleScanner != null) {
                try {
                    ScanSettings settings = new ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .build();
                    bleScanner.startScan(null, settings, bleCallback);
                } catch (SecurityException e) {
                    listener.onStatus("Bluetooth permission missing");
                }
            }
        } else {
            listener.onStatus("Bluetooth is off");
        }

        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            if (!wifiReceiverRegistered) {
                IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(wifiReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                else context.registerReceiver(wifiReceiver, filter);
                wifiReceiverRegistered = true;
            }
            handler.post(wifiLoop);
        }
    }

    @SuppressLint("MissingPermission")
    void stop() {
        running = false;
        handler.removeCallbacks(wifiLoop);
        if (bleScanner != null) {
            try { bleScanner.stopScan(bleCallback); } catch (SecurityException ignored) {}
        }
        bleScanner = null;
    }

    void destroy() {
        stop();
        if (wifiReceiverRegistered) {
            try { context.unregisterReceiver(wifiReceiver); } catch (Exception ignored) {}
            wifiReceiverRegistered = false;
        }
    }

    @SuppressLint("MissingPermission")
    private void dispatchBle(ScanResult result) {
        String id;
        try { id = result.getDevice().getAddress(); }
        catch (SecurityException e) { id = "ble:" + Integer.toHexString(result.hashCode()); }
        String name = null;
        ScanRecord record = result.getScanRecord();
        if (record != null) name = record.getDeviceName();
        if (name == null) {
            try { name = result.getDevice().getName(); } catch (SecurityException ignored) {}
        }
        listener.onBle(id, name, result.getRssi(), record);
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("MissingPermission")
    private void scanWifiOnce() {
        if (wifiManager == null) return;
        try {
            boolean started = wifiManager.startScan();
            if (!started) {
                // Android may throttle active scans. Existing cached results are still useful.
                readWifiResults();
            }
        } catch (SecurityException e) {
            listener.onStatus("Wi-Fi scan needs Location permission and Location enabled");
        }
    }

    @SuppressLint("MissingPermission")
    private void readWifiResults() {
        if (wifiManager == null) return;
        try {
            List<android.net.wifi.ScanResult> results = wifiManager.getScanResults();
            if (results != null) for (android.net.wifi.ScanResult r : results) listener.onWifi(r);
        } catch (SecurityException e) {
            listener.onStatus("Wi-Fi results unavailable: permission/location");
        }
    }
}
