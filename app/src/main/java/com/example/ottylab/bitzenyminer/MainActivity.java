package com.example.ottylab.bitzenyminer;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.ottylab.bitzenymininglibrary.BitZenyMiningLibrary;

import java.lang.ref.WeakReference;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "BitZenyMiner";
    private static final int LOG_LINES = 1000;

    private BitZenyMiningLibrary miner;

    private EditText editTextUser;
    private EditText editTextNThreads;
    private Button buttonDrive;
    private Spinner poolSelection;
    private TextView textViewLog;

    private boolean running;
    private BlockingQueue<String> logs = new LinkedBlockingQueue<>(LOG_LINES);

    private static class JNICallbackHandler extends Handler {
        private final WeakReference<MainActivity> activity;

        public JNICallbackHandler(MainActivity activity) {
            this.activity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {

            System.out.print(msg);

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

        editTextNThreads = (EditText) findViewById(R.id.editTextNThreads);
        editTextNThreads.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                storeSetting();
            }
        });

        buttonDrive = (Button) findViewById(R.id.buttonDrive);
        buttonDrive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String poolAddress = "";
                if (poolSelection.getSelectedItemPosition() == 0 ){
                    poolAddress = "stratum+tcp://eu1-pool.tidecoin.exchange:3033";
                }
                if (poolSelection.getSelectedItemPosition() == 1 ){
                    poolAddress = "stratum+tcp://178.170.40.44:6243";
                }

                if (running) {
                    Log.d(TAG, "Stop");
                    miner.stopMining();
                } else {
                    Log.d(TAG, "Start");
                    int n_threads = 0;
                    try {
                        n_threads = Integer.parseInt(editTextNThreads.getText().toString());
                    } catch (NumberFormatException e){}

                    BitZenyMiningLibrary.Algorithm algorithm = BitZenyMiningLibrary.Algorithm.YESPOWER;
                    miner.startMining(
                            poolAddress,
                            editTextUser.getText().toString(),
                            "c=TDC",
                            n_threads,
                            algorithm);
                }

                changeState(!running);
                storeSetting();
            }
        });

        poolSelection = (Spinner) findViewById(R.id.poolSelection);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.poolUsed, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        poolSelection.setAdapter(adapter);

        textViewLog = (TextView) findViewById(R.id.textViewLog);
        textViewLog.setMovementMethod(new ScrollingMovementMethod());

        restoreSetting();
        changeState(miner.isMiningRunning());
    }

    private void changeState(boolean running) {
        buttonDrive.setText(running ? "Stop" : "Start");
        disableSetting(running);
        this.running = running;
    }

    private void disableSetting(boolean running) {
        editTextUser.setEnabled(!running);
        editTextNThreads.setEnabled(!running);
        poolSelection.setEnabled(!running);
    }

    private void storeSetting() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("user", editTextUser.getText().toString());
        editor.putString("n_threads", editTextNThreads.getText().toString());
        editor.putInt("pool", poolSelection.getSelectedItemPosition());
        editor.commit();
    }

    private void restoreSetting() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        editTextUser.setText(pref.getString("user", null));
        editTextNThreads.setText(pref.getString("n_threads", null));
        poolSelection.setSelection(pref.getInt("pool", 0));
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
}
