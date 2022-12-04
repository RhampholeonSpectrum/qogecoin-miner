package com.example.ottylab.bitzenyminer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.ottylab.bitzenymininglibrary.BitZenyMiningLibrary;

import com.github.anastr.speedviewlib.TubeSpeedometer;
import com.github.anastr.speedviewlib.components.Section;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.ref.WeakReference;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "BitZenyMiner";
    private static final int LOG_LINES = 1000;

    private BitZenyMiningLibrary miner;

    private EditText editTextUser;
    private Button buttonDrive;
    private TextView textViewLog;

    public static Context contextOfApplication;
    public static Context getContextOfApplication() {
        return contextOfApplication;
    }

    private boolean running;
    private BlockingQueue<String> logs = new LinkedBlockingQueue<>(LOG_LINES);

    private float maxHashrate = 1;
    private int usedCPUs = 1;
    private int numberCpuMax = 1;

    private int BatteryTemp;

    // creating a variable for our button.
    private Button settingsBtn;

    private class JNICallbackHandler extends Handler {
        private final WeakReference<MainActivity> activity;

        public JNICallbackHandler(MainActivity activity) {
            this.activity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {

            // calculate approximated hashes
            if (msg.getData().getString("log").contains("hashes")) {
                String[] subStrings = msg.getData().getString("log").split(",");
                if (subStrings.length == 2) {
                    // set hashrate to speed o meter
                    int end = subStrings[1].indexOf("h");
                    String hashValue = subStrings[1].substring(1, end-1);
                    double d = Double.parseDouble(hashValue);
                    TubeSpeedometer meterHashrate = findViewById(R.id.meter_hashrate);
                    meterHashrate.makeSections(1, getResources().getColor(R.color.c_blue), Section.Style.SQUARE);

                    // multiply hashrate from one core (this hashrate) with cores which are used
                    double hashRateForUsedCores = usedCPUs * d;

                    if (maxHashrate < ((float) numberCpuMax * d)) {
                        maxHashrate = ((float) numberCpuMax * (float) d);
                    }

                    meterHashrate.setMaxSpeed(maxHashrate);
                    meterHashrate.speedTo((float) hashRateForUsedCores);

                    // set hashrate to string
                    TextView tvHashrate = findViewById(R.id.hashrate);
                    tvHashrate.setText(String.valueOf(Math.round(hashRateForUsedCores)));
                }
            }

            IntentFilter intentfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            MainActivity.this.registerReceiver(broadcastreceiver,intentfilter);

            System.out.print("BatteryTemp");
            Math.round(BatteryTemp);
            System.out.print(Math.round(BatteryTemp));

            // show accu temp
            TextView accuTemp = findViewById(R.id.accuTemp);
            accuTemp.setText(String.valueOf(BatteryTemp));

            // get "real" hashes with last accepted share
            if (msg.getData().getString("log").contains("yay!!!")) {
                String[] subStrings = msg.getData().getString("log").split(",");
                if (subStrings.length == 2) {
                    // set hashrate to speed o meter
                    int end = subStrings[1].indexOf("h");
                    String hashValue = subStrings[1].substring(1, end-1);
                    double d = Double.parseDouble(hashValue);
                    TubeSpeedometer meterHashrate = findViewById(R.id.meter_hashrate);
                    meterHashrate.makeSections(1, getResources().getColor(R.color.c_blue), Section.Style.SQUARE);

                    // will set the highest value as maximum hashrate
                    if (maxHashrate < ((float) d)) {
                        maxHashrate = ((float) d);
                        meterHashrate.setMaxSpeed(maxHashrate);
                    }

                    meterHashrate.speedTo((float) d);

                    // set hashrate to string
                    TextView tvHashrate = findViewById(R.id.hashrate);
                    tvHashrate.setText(String.valueOf(Math.round(d)));
                }
            }

            MainActivity activity = this.activity.get();
            if (activity != null) {
                String log = msg.getData().getString("log");
                String logs = Utils.rotateStringQueue(activity.logs, log);
                activity.textViewLog.setText(logs);
                Log.d(TAG, log);
            }
        }
    }

    private static JNICallbackHandler sHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        showDeviceInfo();

        sHandler = new JNICallbackHandler(this);
        miner = new BitZenyMiningLibrary(sHandler);

        editTextUser = (EditText) findViewById(R.id.editTextUser);
        editTextUser.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                storeSetting();
            }
        });

        buttonDrive = (Button) findViewById(R.id.buttonDrive);
        buttonDrive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (running) {
                    Log.d(TAG, "Stop");
                    miner.stopMining();

                    // meter Hashrate
                    TubeSpeedometer meterHashrate = findViewById(R.id.meter_hashrate);
                    maxHashrate = 1;
                    meterHashrate.speedTo(0);

                    // set hashrate to string
                    TextView tvHashrate = findViewById(R.id.hashrate);
                    tvHashrate.setText("-");

                } else {
                    Log.d(TAG, "Start");

                    // get set value for cpu cores
                    SeekBar seekBar = findViewById(R.id.seekBar);
                    seekBar.toString();
                    usedCPUs = seekBar.getProgress();

                    // set value for cpu to speed o meter
                    TubeSpeedometer meterCores = findViewById(R.id.meter_cores);
                    meterCores.makeSections(1, getResources().getColor(R.color.c_yellow), Section.Style.SQUARE);
                    meterCores.setMaxSpeed(numberCpuMax);
                    meterCores.speedTo(usedCPUs, numberCpuMax);


                    BitZenyMiningLibrary.Algorithm algorithm = BitZenyMiningLibrary.Algorithm.YESCRYPT;
                    miner.startMining(
                            "stratum+tcp://yescryptR16.eu.mine.zpool.ca:6333",
                            editTextUser.getText().toString() + ".AndroidMiner",
                            "c=QOGE,zap=QOGE",
                            usedCPUs,
                            algorithm);
                }

                changeState(!running);
                storeSetting();
            }
        });

        textViewLog = (TextView) findViewById(R.id.textViewLog);
        textViewLog.setMovementMethod(new ScrollingMovementMethod());

        restoreSetting();
        changeState(miner.isMiningRunning());

        // get number of cpu cores
        CPUCores cpuCores = new CPUCores();
        cpuCores.main();
        numberCpuMax = cpuCores.numberCPUs;

        // init slider with number of cores
        SeekBar seekBar = findViewById(R.id.seekBar);
        seekBar.setMax(cpuCores.numberCPUs);
        seekBar.setMin(1);

        // enable speed o meter for cores
        TubeSpeedometer meterCores = findViewById(R.id.meter_cores);
        meterCores.makeSections(1, getResources().getColor(R.color.c_yellow), Section.Style.SQUARE);
        meterCores.setMaxSpeed(numberCpuMax);
        meterCores.speedTo(0, 1);

        // Hashrate
        TubeSpeedometer meterHashrate = findViewById(R.id.meter_hashrate);
        meterHashrate.makeSections(1, getResources().getColor(R.color.c_blue), Section.Style.SQUARE);
        meterCores.setMaxSpeed(10);
        meterHashrate.speedTo(0);

        // default hashrate to string
        TextView tvHashrate = findViewById(R.id.hashrate);
        tvHashrate.setText("-");
    }

    private void changeState(boolean running) {
        buttonDrive.setText(running ? "Stop" : "Start");
        disableSetting(running);
        this.running = running;
    }

    private void disableSetting(boolean running) {
        editTextUser.setEnabled(!running);
    }

    private void storeSetting() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("user", editTextUser.getText().toString());
        editor.commit();
    }

    private void restoreSetting() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        editTextUser.setText(pref.getString("user", null));
    }

    private void showDeviceInfo() {
        String[] keys = new String[]{ "os.arch", "os.name", "os.version" };
        for (String key : keys) {
            Log.d(TAG, key + ": " + System.getProperty(key));
        }
        Log.d(TAG, "CODE NAME: " + Build.VERSION.CODENAME);
        Log.d(TAG, "SDK INT: " + Build.VERSION.SDK_INT);
        Log.d(TAG, "MANUFACTURER: " + Build.MANUFACTURER);
        Log.d(TAG, "MODEL: " + Build.MODEL);
        Log.d(TAG, "PRODUCT: " + Build.PRODUCT);
    }

    private BroadcastReceiver broadcastreceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BatteryTemp = (int)(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0))/10; // FIXME avoid casting to float
        }
    };
}

class CPUCores {
    public int numberCPUs = 1;

    public void main() {
        int processors = Runtime.getRuntime().availableProcessors();
        System.out.println("CPU cores: " + processors);
        numberCPUs = processors;
    }
}