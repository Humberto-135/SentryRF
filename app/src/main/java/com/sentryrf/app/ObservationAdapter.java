package com.sentryrf.app;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class ObservationAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<Observation> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    ObservationAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    void setItems(List<Observation> values) {
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    Observation getObservation(int position) {
        return items.get(position);
    }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.row_observation, parent, false);
            h = new ViewHolder();
            h.title = convertView.findViewById(R.id.rowTitle);
            h.risk = convertView.findViewById(R.id.rowRisk);
            h.subtitle = convertView.findViewById(R.id.rowSubtitle);
            h.radio = convertView.findViewById(R.id.rowRadio);
            h.rssi = convertView.findViewById(R.id.rowRssi);
            h.seen = convertView.findViewById(R.id.rowSeen);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }
        Observation o = items.get(position);
        int risk = o.effectiveRisk();
        h.title.setText(o.displayName());
        h.risk.setText("RISK " + risk);
        h.risk.setTextColor(risk >= 75 ? Color.rgb(255,107,107) : risk >= 50 ? Color.rgb(255,200,87) : Color.rgb(103,232,165));
        h.subtitle.setText(o.subtitle());
        h.radio.setText(o.radio + " · " + o.category);
        h.rssi.setText(o.rssi + " dBm");
        h.seen.setText(o.seenCount + " hits · " + timeFormat.format(new Date(o.lastSeen)));
        return convertView;
    }

    private static class ViewHolder {
        TextView title, risk, subtitle, radio, rssi, seen;
    }
}
