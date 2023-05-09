package de.fhws.indoor.libsmartphonesensors.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import de.fhws.indoor.libsmartphonesensors.R;
import de.fhws.indoor.libsmartphonesensors.sensors.DecawaveUWB;

public class EventCounterView extends GridLayout {

    public enum UWBState {
        NONE,
        CONNECTING,
        CONNECTED,
        CONNECTING_FAILED;

        public static UWBState from(DecawaveUWB sensorUWB) {
            if(sensorUWB == null) { return EventCounterView.UWBState.NONE; }
            if(sensorUWB.isConnectedToTag()) { return EventCounterView.UWBState.CONNECTED; }
            else if(sensorUWB.isCurrentlyConnecting()) { return EventCounterView.UWBState.CONNECTING; }
            else { return EventCounterView.UWBState.CONNECTING_FAILED; }
        }
    }

    public static class CounterData {
        public long wifiEvtCnt = 0;
        public long wifiScanCnt = 0;

        public long ftmEvtCnt = 0;
        public long bleEvtCnt = 0;
        public long gpsEvtCnt = 0;

        public long uwbEvtCnt = 0;
        public UWBState uwbState = UWBState.NONE;
    }

    public static class EntryActiveData {
        public boolean wifi = true;
        public boolean ftm = true;
        public boolean ble = true;
        public boolean uwb = true;
        public boolean gps = true;
    }

    public interface ConsumeCounterDataFn { void update(CounterData counterData); }
    public interface ConsumeActiveDataFn { void update(EntryActiveData activeData); }



    private CounterData counterData = new CounterData();
    private EntryActiveData activeData = new EntryActiveData();
    private boolean clickable = false;
    private TextView lblWifi;
    private TextView lblFTM;
    private TextView lblBLE;
    private TextView lblGPS;
    private TextView lblUWB;
    private TextView txtWifi;
    private TextView txtFTM;
    private TextView txtBLE;
    private TextView txtGPS;
    private TextView txtUWB;

    private ConsumeActiveDataFn activeDataChangedCallback = null;

    public EventCounterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupUi();
    }

    public void updateCounterData(ConsumeCounterDataFn updateFn) {
        updateFn.update(counterData);
        updateCounterUi();
    }

    public void updateActiveData(boolean raiseEvent, ConsumeActiveDataFn updateFn) {
        updateFn.update(activeData);
        if(raiseEvent && activeDataChangedCallback != null)  {
            activeDataChangedCallback.update(activeData);
        }
        updateActiveUi();
    }

    public void setActiveDataChangedCallback(ConsumeActiveDataFn activeDataChangedCallback) {
        this.activeDataChangedCallback = activeDataChangedCallback;
    }

    public void setClickable(boolean clickable) {
        this.clickable = clickable;
    }



    private void setupUi() {
        View.inflate(getContext(), R.layout.event_counter_view, this);

        lblWifi = findViewById(R.id.lblCntWifi);
        txtWifi = findViewById(R.id.txtEvtCntWifi);
        lblWifi.setOnClickListener(el -> updateActiveData(true, (a) -> { if(clickable) {a.wifi = !a.wifi;} }));
        txtWifi.setOnClickListener(el -> updateActiveData(true, (a) -> { if(clickable) {a.wifi = !a.wifi;} }));

        lblFTM = findViewById(R.id.lblCntWifiRTT);
        txtFTM = findViewById(R.id.txtEvtCntFTM);
        lblFTM.setOnClickListener(el -> updateActiveData(true, (a) -> { if(clickable) {a.ftm = !a.ftm;} }));
        txtFTM.setOnClickListener(el -> updateActiveData(true, (a) -> { if(clickable) {a.ftm = !a.ftm;} }));

        lblBLE = findViewById(R.id.lblCntBeacon);
        txtBLE = findViewById(R.id.txtEvtCntBLE);
        lblBLE.setOnClickListener(el -> updateActiveData(true, (a) -> { if(clickable) {a.ble = !a.ble;} }));
        txtBLE.setOnClickListener(el -> updateActiveData(true, (a) -> { if(clickable) {a.ble = !a.ble;} }));

        lblGPS = findViewById(R.id.lblCntGPS);
        txtGPS = findViewById(R.id.txtEvtCntGPS);
        lblGPS.setOnClickListener(el -> updateActiveData(true, (a) -> { if(clickable) {a.gps = !a.gps;} }));
        txtGPS.setOnClickListener(el -> updateActiveData(true, (a) -> { if(clickable) {a.gps = !a.gps;} }));

        lblUWB = findViewById(R.id.lblCntUWB);
        txtUWB = findViewById(R.id.txtEvtCntUWB);
        lblUWB.setOnClickListener(el -> updateActiveData(true, (a) -> { if(clickable) {a.uwb = !a.uwb;} }));
        txtUWB.setOnClickListener(el -> updateActiveData(true, (a) -> { if(clickable) {a.uwb = !a.uwb;} }));
    }

    private void updateCounterUi() {
        if(counterData.wifiScanCnt > 0) {
            txtWifi.setText(String.format("%d | %d", counterData.wifiEvtCnt, counterData.wifiScanCnt));
        } else {
            txtWifi.setText("-");
        }
        txtFTM.setText(makeStatusString(counterData.ftmEvtCnt));
        txtBLE.setText(makeStatusString(counterData.bleEvtCnt));
        txtGPS.setText(makeStatusString(counterData.gpsEvtCnt));
        switch(counterData.uwbState) {
            case NONE: txtUWB.setText("-"); break;
            case CONNECTED: txtUWB.setText(Long.toString(counterData.uwbEvtCnt)); break;
            case CONNECTING: txtUWB.setText("⌛"); break;
            case CONNECTING_FAILED: txtUWB.setText("✖"); break;
        }
    }

    private void updateActiveUi() {
        lblWifi.setTextColor(boolToColor(activeData.wifi));
        lblFTM.setTextColor(boolToColor(activeData.ftm));
        lblBLE.setTextColor(boolToColor(activeData.ble));
        lblUWB.setTextColor(boolToColor(activeData.uwb));
        lblGPS.setTextColor(boolToColor(activeData.gps));
    }

    private static String makeStatusString(long evtCnt) {
        return (evtCnt == 0) ? "-" : Long.toString(evtCnt);
    }
    private int boolToColor(boolean status) {
        if(status) {
            return getResources().getColor(R.color.event_counter_active);
        } else {
            return getResources().getColor(R.color.event_counter_inactive);
        }
    }
}
