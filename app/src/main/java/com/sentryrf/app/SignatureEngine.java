package com.sentryrf.app;

import android.bluetooth.le.ScanRecord;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.os.ParcelUuid;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class SignatureEngine {
    private final List<SignatureRule> rules = new ArrayList<>();

    SignatureEngine(Context context) throws Exception {
        load(context);
    }

    int ruleCount() {
        return rules.size();
    }

    private void load(Context context) throws Exception {
        try (InputStream in = context.getAssets().open("signatures.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            JSONArray arr = new JSONArray(out.toString(StandardCharsets.UTF_8.name()));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Pattern name = o.has("nameRegex") ? Pattern.compile(o.getString("nameRegex")) : null;
                Pattern ssid = o.has("ssidRegex") ? Pattern.compile(o.getString("ssidRegex")) : null;
                String uuid = o.has("serviceUuid") ? o.getString("serviceUuid").toLowerCase(Locale.US) : null;
                Integer manufacturer = o.has("manufacturerId") ? o.getInt("manufacturerId") : null;
                rules.add(new SignatureRule(
                        o.getString("id"),
                        o.getString("radio"),
                        o.getString("category"),
                        o.getString("label"),
                        o.getInt("risk"),
                        o.optString("confidence", "LOW"),
                        name, ssid, uuid, manufacturer
                ));
            }
        }
    }

    SignatureMatch matchBle(String name, ScanRecord record) {
        SignatureMatch best = SignatureMatch.generic();
        for (SignatureRule r : rules) {
            if (!"BLE".equals(r.radio)) continue;
            boolean matched = false;
            if (r.namePattern != null && name != null && r.namePattern.matcher(name).matches()) matched = true;
            if (!matched && r.serviceUuid != null && hasServiceUuid(record, r.serviceUuid)) matched = true;
            if (!matched && r.manufacturerId != null && hasManufacturer(record, r.manufacturerId)) matched = true;
            if (matched && r.risk > best.risk) {
                best = new SignatureMatch(r.id, r.category, r.label, r.risk, r.confidence);
            }
        }
        return best;
    }

    SignatureMatch matchWifi(ScanResult result) {
        String ssid = result.SSID == null ? "" : result.SSID;
        SignatureMatch best = SignatureMatch.generic();
        for (SignatureRule r : rules) {
            if (!"WIFI".equals(r.radio) || r.ssidPattern == null) continue;
            if (r.ssidPattern.matcher(ssid).matches() && r.risk > best.risk) {
                best = new SignatureMatch(r.id, r.category, r.label, r.risk, r.confidence);
            }
        }
        return best;
    }

    private boolean hasServiceUuid(ScanRecord record, String target) {
        if (record == null) return false;
        List<ParcelUuid> uuids = record.getServiceUuids();
        if (uuids != null) {
            for (ParcelUuid u : uuids) {
                if (u.toString().toLowerCase(Locale.US).equals(target)) return true;
            }
        }
        Map<ParcelUuid, byte[]> serviceData = record.getServiceData();
        if (serviceData != null) {
            for (ParcelUuid u : serviceData.keySet()) {
                if (u.toString().toLowerCase(Locale.US).equals(target)) return true;
            }
        }
        return false;
    }

    private boolean hasManufacturer(ScanRecord record, int id) {
        if (record == null) return false;
        SparseArray<byte[]> data = record.getManufacturerSpecificData();
        return data != null && data.get(id) != null;
    }

    static String blePayloadSummary(ScanRecord record) {
        if (record == null) return "";
        StringBuilder sb = new StringBuilder();
        List<ParcelUuid> uuids = record.getServiceUuids();
        if (uuids != null && !uuids.isEmpty()) sb.append("UUIDs=").append(uuids).append(' ');
        SparseArray<byte[]> m = record.getManufacturerSpecificData();
        if (m != null && m.size() > 0) {
            sb.append("MFG=");
            for (int i = 0; i < m.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(String.format(Locale.US, "0x%04X", m.keyAt(i)));
            }
        }
        byte[] raw = record.getBytes();
        if (raw != null) sb.append(" RAW=").append(hex(raw, 40));
        return sb.toString().trim();
    }

    private static String hex(byte[] data, int maxBytes) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(data.length, maxBytes);
        for (int i = 0; i < n; i++) sb.append(String.format(Locale.US, "%02X", data[i]));
        if (data.length > maxBytes) sb.append("…");
        return sb.toString();
    }
}
