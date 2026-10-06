package com.sentryrf.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity implements RadioObserver.Listener {
    private static final int REQ_PERMISSIONS = 40;
    private static final int REQ_EXPORT = 41;

    private final Map<String, Observation> observations = new HashMap<>();
    private final SimpleDateFormat csvTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private SignatureEngine signatures;
    private ObservationStore store;
    private RadioObserver observer;
    private ObservationAdapter adapter;

    private TextView statusBadge, bleCount, wifiCount, alertCount, helpText;
    private Button startStopButton, baselineButton, exportButton, clearButton;
    private Spinner filterSpinner;
    private ListView resultList;
    private String lastStatus = "IDLE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        applyInsets();
        try {
            signatures = new SignatureEngine(this);
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Signature database error").setMessage(e.toString()).setPositiveButton("Close", null).show();
            return;
        }
        store = new ObservationStore(this);
        observer = new RadioObserver(this, this);
        adapter = new ObservationAdapter(this);
        resultList.setAdapter(adapter);
        setupFilter();
        helpText.setText("" + signatures.ruleCount() + " local signatures · baseline " + store.baselineSize() + " sources · matches are hypotheses");

        startStopButton.setOnClickListener(v -> toggleWatch());
        baselineButton.setOnClickListener(v -> saveBaseline());
        exportButton.setOnClickListener(v -> exportCsv());
        clearButton.setOnClickListener(v -> clearSession());
        filterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { refreshList(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        resultList.setOnItemClickListener((parent, view, position, id) -> showDetails(adapter.getObservation(position)));
    }

    private void bindViews() {
        statusBadge = findViewById(R.id.statusBadge);
        bleCount = findViewById(R.id.bleCount);
        wifiCount = findViewById(R.id.wifiCount);
        alertCount = findViewById(R.id.alertCount);
        helpText = findViewById(R.id.helpText);
        startStopButton = findViewById(R.id.startStopButton);
        baselineButton = findViewById(R.id.baselineButton);
        exportButton = findViewById(R.id.exportButton);
        clearButton = findViewById(R.id.clearButton);
        filterSpinner = findViewById(R.id.filterSpinner);
        resultList = findViewById(R.id.resultList);
    }

    private void applyInsets() {
        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), bars.top + 6, v.getPaddingRight(), bars.bottom + 8);
            } else {
                v.setPadding(v.getPaddingLeft(), insets.getSystemWindowInsetTop() + 6, v.getPaddingRight(), insets.getSystemWindowInsetBottom() + 8);
            }
            return insets;
        });
    }

    private void setupFilter() {
        String[] filters = {"All sources", "Attention only", "Trackers", "Drones", "Cameras", "Body cams", "Audio", "Smart glasses", "Other"};
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, filters) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(Color.WHITE);
                v.setPadding(14, 0, 8, 0);
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(Color.BLACK);
                return v;
            }
        };
        filterSpinner.setAdapter(a);
    }

    private void toggleWatch() {
        if (observer != null && observer.isRunning()) {
            observer.stop();
            startStopButton.setText("START WATCH");
            setStatus("PAUSED", false);
        } else {
            if (hasAllPermissions()) startWatch();
            else requestNeededPermissions();
        }
    }

    private void startWatch() {
        if (observer == null) return;
        observer.start();
        startStopButton.setText("STOP WATCH");
        setStatus("WATCHING", true);
    }

    private boolean hasAllPermissions() {
        List<String> needed = neededPermissions();
        for (String p : needed) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    private List<String> neededPermissions() {
        List<String> p = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            p.add(Manifest.permission.BLUETOOTH_SCAN);
            p.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        return p;
    }

    private void requestNeededPermissions() {
        List<String> needed = neededPermissions();
        List<String> missing = new ArrayList<>();
        for (String p : needed) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        requestPermissions(missing.toArray(new String[0]), REQ_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMISSIONS) return;
        if (hasAllPermissions()) startWatch();
        else new AlertDialog.Builder(this)
                .setTitle("Permissions required")
                .setMessage("Sentry RF needs Nearby devices and precise Location for Android's Bluetooth/Wi-Fi scan APIs. No location is stored by this build.")
                .setPositiveButton("Settings", (d, w) -> openAppSettings())
                .setNegativeButton("Cancel", null).show();
    }

    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    @Override
    public void onBle(String id, String name, int rssi, android.bluetooth.le.ScanRecord record) {
        runOnUiThread(() -> {
            SignatureMatch match = signatures.matchBle(name, record);
            String key = "BLE|" + id;
            upsert(key, "BLE", id, name, rssi, match, SignatureEngine.blePayloadSummary(record));
        });
    }

    @Override
    public void onWifi(ScanResult result) {
        runOnUiThread(() -> {
            String id = result.BSSID == null ? "<unknown-bssid>" : result.BSSID;
            String ssid = result.SSID == null || result.SSID.isEmpty() ? "<hidden SSID>" : result.SSID;
            SignatureMatch match = signatures.matchWifi(result);
            String raw = "freq=" + result.frequency + "MHz capabilities=" + result.capabilities;
            upsert("WIFI|" + id, "WIFI", id, ssid, result.level, match, raw);
        });
    }

    private void upsert(String key, String radio, String id, String name, int rssi, SignatureMatch match, String raw) {
        long now = System.currentTimeMillis();
        boolean known = store.isKnown(key);
        Observation o = observations.get(key);
        if (o == null) observations.put(key, new Observation(key, radio, id, name, rssi, match, raw, now, known));
        else o.update(name, rssi, match, raw, now, known);
        refreshList();
    }

    private void refreshList() {
        int ble = 0, wifi = 0, alerts = 0;
        List<Observation> values = new ArrayList<>();
        String filter = filterSpinner.getSelectedItem() == null ? "All sources" : filterSpinner.getSelectedItem().toString();
        for (Observation o : observations.values()) {
            if ("BLE".equals(o.radio)) ble++; else if ("WIFI".equals(o.radio)) wifi++;
            if (o.effectiveRisk() >= 60) alerts++;
            if (matchesFilter(o, filter)) values.add(o);
        }
        Collections.sort(values, Comparator
                .comparingInt(Observation::effectiveRisk).reversed()
                .thenComparingLong(o -> -o.lastSeen));
        adapter.setItems(values);
        bleCount.setText(String.valueOf(ble));
        wifiCount.setText(String.valueOf(wifi));
        alertCount.setText(String.valueOf(alerts));
    }

    private boolean matchesFilter(Observation o, String filter) {
        switch (filter) {
            case "Attention only": return o.effectiveRisk() >= 60;
            case "Trackers": return "TRACKER".equals(o.category);
            case "Drones": return "DRONE".equals(o.category);
            case "Cameras": return "CAMERA".equals(o.category);
            case "Body cams": return "BODYCAM".equals(o.category);
            case "Audio": return "AUDIO".equals(o.category);
            case "Smart glasses": return "GLASSES".equals(o.category);
            case "Other": return "OTHER".equals(o.category);
            default: return true;
        }
    }

    private void saveBaseline() {
        if (observations.isEmpty()) {
            Toast.makeText(this, "Nothing observed yet", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Save current baseline?")
                .setMessage("All " + observations.size() + " currently observed radio IDs will be marked KNOWN. This replaces the previous baseline.")
                .setPositiveButton("Save", (d, w) -> {
                    Set<String> keys = new HashSet<>(observations.keySet());
                    store.replaceBaseline(keys);
                    for (Observation o : observations.values()) o.baselineKnown = true;
                    helpText.setText(signatures.ruleCount() + " local signatures · baseline " + store.baselineSize() + " sources · matches are hypotheses");
                    refreshList();
                    Toast.makeText(this, "Baseline saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void clearSession() {
        observations.clear();
        refreshList();
    }

    private void showDetails(Observation o) {
        String msg = "Radio: " + o.radio +
                "\nCategory: " + o.category +
                "\nMatch: " + o.label +
                "\nConfidence: " + o.confidence +
                "\nBaseline: " + (o.baselineKnown ? "KNOWN" : "NEW") +
                "\nRisk score: " + o.effectiveRisk() +
                "\nRSSI: " + o.rssi + " dBm" +
                "\nFirst seen: " + csvTime.format(new Date(o.firstSeen)) +
                "\nLast seen: " + csvTime.format(new Date(o.lastSeen)) +
                "\nHits: " + o.seenCount +
                "\nID: " + o.id +
                "\n\nRaw: " + (o.raw.isEmpty() ? "n/a" : o.raw);
        new AlertDialog.Builder(this).setTitle(o.displayName()).setMessage(msg).setPositiveButton("Close", null).show();
    }

    private void exportCsv() {
        if (observations.isEmpty()) {
            Toast.makeText(this, "Nothing to export", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "sentryrf-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".csv");
        startActivityForResult(intent, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Could not open output");
            StringBuilder csv = new StringBuilder("radio,category,name,id,rssi,risk,confidence,baseline,first_seen,last_seen,hits,label,raw\n");
            List<Observation> sorted = new ArrayList<>(observations.values());
            Collections.sort(sorted, Comparator.comparingInt(Observation::effectiveRisk).reversed());
            for (Observation o : sorted) {
                csv.append(q(o.radio)).append(',').append(q(o.category)).append(',').append(q(o.name)).append(',').append(q(o.id)).append(',')
                        .append(o.rssi).append(',').append(o.effectiveRisk()).append(',').append(q(o.confidence)).append(',').append(q(o.baselineKnown ? "KNOWN" : "NEW")).append(',')
                        .append(q(csvTime.format(new Date(o.firstSeen)))).append(',').append(q(csvTime.format(new Date(o.lastSeen)))).append(',').append(o.seenCount).append(',')
                        .append(q(o.label)).append(',').append(q(o.raw)).append('\n');
            }
            out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "CSV exported", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String q(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void onStatus(String message) {
        runOnUiThread(() -> {
            lastStatus = message;
            helpText.setText(message + " · " + signatures.ruleCount() + " local signatures");
        });
    }

    private void setStatus(String text, boolean active) {
        lastStatus = text;
        statusBadge.setText(text);
        statusBadge.setTextColor(active ? Color.rgb(103,232,165) : Color.rgb(255,200,87));
    }

    @Override
    protected void onDestroy() {
        if (observer != null) observer.destroy();
        super.onDestroy();
    }
}
