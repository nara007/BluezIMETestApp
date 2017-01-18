package com.hexad.bluezime.testapp;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

public class TestActivity extends Activity {

    //These constants are copied from the BluezService
    public static final String SESSION_ID = "com.hexad.bluezime.sessionid";

    public static final String EVENT_KEYPRESS = "com.hexad.bluezime.keypress";
    public static final String EVENT_KEYPRESS_KEY = "key";
    public static final String EVENT_KEYPRESS_ACTION = "action";

    public static final String EVENT_DIRECTIONALCHANGE = "com.hexad.bluezime.directionalchange";
    public static final String EVENT_DIRECTIONALCHANGE_DIRECTION = "direction";
    public static final String EVENT_DIRECTIONALCHANGE_VALUE = "value";

    public static final String EVENT_CONNECTED = "com.hexad.bluezime.connected";
    public static final String EVENT_CONNECTED_ADDRESS = "address";

    public static final String EVENT_DISCONNECTED = "com.hexad.bluezime.disconnected";
    public static final String EVENT_DISCONNECTED_ADDRESS = "address";

    public static final String EVENT_ERROR = "com.hexad.bluezime.error";
    public static final String EVENT_ERROR_SHORT = "message";
    public static final String EVENT_ERROR_FULL = "stacktrace";

    public static final String REQUEST_STATE = "com.hexad.bluezime.getstate";

    public static final String REQUEST_CONNECT = "com.hexad.bluezime.connect";
    public static final String REQUEST_CONNECT_ADDRESS = "address";
    public static final String REQUEST_CONNECT_DRIVER = "driver";

    public static final String REQUEST_DISCONNECT = "com.hexad.bluezime.disconnect";

    public static final String EVENT_REPORTSTATE = "com.hexad.bluezime.currentstate";
    public static final String EVENT_REPORTSTATE_CONNECTED = "connected";
    public static final String EVENT_REPORTSTATE_DEVICENAME = "devicename";
    public static final String EVENT_REPORTSTATE_DISPLAYNAME = "displayname";
    public static final String EVENT_REPORTSTATE_DRIVERNAME = "drivername";

    public static final String REQUEST_FEATURECHANGE = "com.hexad.bluezime.featurechange";
    public static final String REQUEST_FEATURECHANGE_RUMBLE = "rumble"; //Boolean, true=on, false=off
    public static final String REQUEST_FEATURECHANGE_LEDID = "ledid"; //Integer, LED to use 1-4 for Wiimote
    public static final String REQUEST_FEATURECHANGE_ACCELEROMETER = "accelerometer"; //Boolean, true=on, false=off

    public static final String REQUEST_CONFIG = "com.hexad.bluezime.getconfig";

    public static final String EVENT_REPORT_CONFIG = "com.hexad.bluezime.config";
    public static final String EVENT_REPORT_CONFIG_VERSION = "version";
    public static final String EVENT_REPORT_CONFIG_DRIVER_NAMES = "drivernames";
    public static final String EVENT_REPORT_CONFIG_DRIVER_DISPLAYNAMES = "driverdisplaynames";


    private static final String BLUEZ_IME_PACKAGE = "com.hexad.bluezime";
    private static final String BLUEZ_IME_SERVICE = "com.hexad.bluezime.BluezService";
    

    //A string used to ensure that apps do not interfere with each other
    public static final String SESSION_NAME = "TEST-BLUEZ-IME";


    private String m_selectedDriver;

    private Button m_button;


    private ListView m_logList;
    private ArrayAdapter<String> m_logAdapter;

    private HashMap<Integer, CheckBox> m_buttonMap = new HashMap<Integer, CheckBox>();
    private ArrayList<String> m_logText = new ArrayList<String>();

    private boolean m_connected = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        m_button = (Button) findViewById(R.id.ConnectButton);


        m_logList = (ListView) findViewById(R.id.LogView);


        m_selectedDriver = "wiimote";


        registerReceiver(stateCallback, new IntentFilter(EVENT_REPORT_CONFIG));
        registerReceiver(stateCallback, new IntentFilter(EVENT_REPORTSTATE));
        registerReceiver(stateCallback, new IntentFilter(EVENT_CONNECTED));
        registerReceiver(stateCallback, new IntentFilter(EVENT_DISCONNECTED));
        registerReceiver(stateCallback, new IntentFilter(EVENT_ERROR));

        registerReceiver(statusMonitor, new IntentFilter(EVENT_DIRECTIONALCHANGE));
        registerReceiver(statusMonitor, new IntentFilter(EVENT_KEYPRESS));


        m_logAdapter = new ArrayAdapter<String>(this, R.layout.log_item, m_logText);
        m_logList.setAdapter(m_logAdapter);

        m_button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_connected) {
                    Intent serviceIntent = new Intent(REQUEST_DISCONNECT);
                    serviceIntent.setClassName(BLUEZ_IME_PACKAGE, BLUEZ_IME_SERVICE);
                    serviceIntent.putExtra(SESSION_ID, SESSION_NAME);
                    startService(serviceIntent);
                } else {
                    Intent serviceIntent = new Intent(REQUEST_CONNECT);
                    serviceIntent.setClassName(BLUEZ_IME_PACKAGE, BLUEZ_IME_SERVICE);
                    serviceIntent.putExtra(SESSION_ID, SESSION_NAME);
                    serviceIntent.putExtra(REQUEST_CONNECT_ADDRESS, "00:1E:35:3B:DF:72");
                    serviceIntent.putExtra(REQUEST_CONNECT_DRIVER, m_selectedDriver);
                    startService(serviceIntent);
                }
            }
        });

        //Request config, not present in version < 9
        Intent serviceIntent = new Intent(REQUEST_CONFIG);
        serviceIntent.setClassName(BLUEZ_IME_PACKAGE, BLUEZ_IME_SERVICE);
        serviceIntent.putExtra(SESSION_ID, SESSION_NAME);
        startService(serviceIntent);

        //Request device connection state
        serviceIntent = new Intent(REQUEST_STATE);
        serviceIntent.setClassName(BLUEZ_IME_PACKAGE, BLUEZ_IME_SERVICE);
        serviceIntent.putExtra(SESSION_ID, SESSION_NAME);
        startService(serviceIntent);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(stateCallback);
        unregisterReceiver(statusMonitor);
    }

    private BroadcastReceiver stateCallback = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null)
                return;

            //Filter everything that is not related to this session
            if (!SESSION_NAME.equals(intent.getStringExtra(SESSION_ID)))
                return;

            if (intent.getAction().equals(EVENT_REPORT_CONFIG)) {
                Toast.makeText(TestActivity.this, "Bluez-IME version " + intent.getIntExtra(EVENT_REPORT_CONFIG_VERSION, 0), Toast.LENGTH_SHORT).show();
//				populateDriverBox(intent.getStringArrayExtra(EVENT_REPORT_CONFIG_DRIVER_NAMES), intent.getStringArrayExtra(EVENT_REPORT_CONFIG_DRIVER_DISPLAYNAMES));
            } else if (intent.getAction().equals(EVENT_REPORTSTATE)) {
                m_connected = intent.getBooleanExtra(EVENT_REPORTSTATE_CONNECTED, false);
                m_button.setText(m_connected ? R.string.bluezime_connected : R.string.bluezime_disconnected);

                //After we connect, we rumble the device for a second if it is supported
                if (m_connected) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Intent req = new Intent(REQUEST_FEATURECHANGE);
                            req.putExtra(REQUEST_FEATURECHANGE_LEDID, 2);
                            req.putExtra(REQUEST_FEATURECHANGE_RUMBLE, true);
                            startService(req);
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }
                            req.putExtra(REQUEST_FEATURECHANGE_LEDID, 1);
                            req.putExtra(REQUEST_FEATURECHANGE_RUMBLE, false);
                            startService(req);
                        }
                    });
                }

            } else if (intent.getAction().equals(EVENT_CONNECTED)) {
                m_button.setText(R.string.bluezime_connected);
                m_connected = true;
            } else if (intent.getAction().equals(EVENT_DISCONNECTED)) {
                m_button.setText(R.string.bluezime_disconnected);
                m_connected = false;
            } else if (intent.getAction().equals(EVENT_ERROR)) {
                Toast.makeText(TestActivity.this, "Error: " + intent.getStringExtra(EVENT_ERROR_SHORT), Toast.LENGTH_SHORT).show();
                reportUnmatched("Error: " + intent.getStringExtra(EVENT_ERROR_FULL));
                m_connected = false;
            }

        }
    };

    private BroadcastReceiver statusMonitor = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null)
                return;
            if (!SESSION_NAME.equals(intent.getStringExtra(SESSION_ID)))
                return;

            if (intent.getAction().equals(EVENT_DIRECTIONALCHANGE)) {


            } else if (intent.getAction().equals(EVENT_KEYPRESS)) {
                int key = intent.getIntExtra(EVENT_KEYPRESS_KEY, 0);
                int action = intent.getIntExtra(EVENT_KEYPRESS_ACTION, 100);

                if (m_buttonMap.containsKey(key))
                    m_buttonMap.get(key).setChecked(action == KeyEvent.ACTION_DOWN);
                else {
                    reportUnmatched(String.format(getString(action == KeyEvent.ACTION_DOWN ? R.string.unmatched_key_event_down : R.string.unmatched_key_event_up), key + ""));
                }
            }
        }
    };

    private void reportUnmatched(String entry) {
        m_logAdapter.add(entry);
        while (m_logAdapter.getCount() > 50)
            m_logAdapter.remove(m_logAdapter.getItem(0));
    }

}