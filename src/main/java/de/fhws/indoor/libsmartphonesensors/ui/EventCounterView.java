package de.fhws.indoor.libsmartphonesensors.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import de.fhws.indoor.libsmartphonesensors.R;

public class EventCounterView extends GridLayout {

    public enum UWBState {
        NONE,
        CONNECTING,
        CONNECTED,
        CONNECTING_FAILED
    }

    public static class CounterData {
        public long evtCntWifi = 0;
        public long evtCntFTM = 0;
        public long evtCntBLE = 0;
        public long evtCntGPS = 0;
        public long evtCntUWB = 0;
        public UWBState uwbState = UWBState.NONE;
    }

    private CounterData counterData = new CounterData();
    private TextView txtWifi;
    private TextView txtFTM;
    private TextView txtBLE;
    private TextView txtGPS;
    private TextView txtUWB;

    private String makeStatusString(long evtCnt) {
        return (evtCnt == 0) ? "-" : Long.toString(evtCnt);
    }

    public EventCounterView(Context context, AttributeSet attrs) {
        super(context, attrs);

        View.inflate(context, R.layout.event_counter_view, this);

        txtWifi = findViewById(R.id.txtEvtCntWifi);
        txtFTM = findViewById(R.id.txtEvtCntFTM);
        txtBLE = findViewById(R.id.txtEvtCntBLE);
        txtGPS = findViewById(R.id.txtEvtCntGPS);
        txtUWB = findViewById(R.id.txtEvtCntUWB);
    }

    public interface CounterDataUpdateFn {
        void update(CounterData counterData);
    }
    public void updateCounterData(CounterDataUpdateFn updateFn) {
        updateFn.update(counterData);
        txtWifi.setText(makeStatusString(counterData.evtCntWifi));
        txtFTM.setText(makeStatusString(counterData.evtCntFTM));
        txtBLE.setText(makeStatusString(counterData.evtCntBLE));
        txtGPS.setText(makeStatusString(counterData.evtCntGPS));
        switch(counterData.uwbState) {
            case NONE: txtUWB.setText(""); break;
            case CONNECTED: txtUWB.setText(Long.toString(counterData.evtCntUWB)); break;
            case CONNECTING: txtUWB.setText("⌛"); break;
            case CONNECTING_FAILED: txtUWB.setText("✖"); break;
        }
    }

}
