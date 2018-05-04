package com.tuankhai.automaticsystem;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.CountDownTimer;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;


import me.itangqi.waveloadingview.WaveLoadingView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button btnOn, btnOff, btnAuto;
    private TextView txtTemp, txtHum, txtStatus;
    private WaveLoadingView mWaveLoadingView;

    protected boolean flagOnline = false;
    protected boolean flagMotor = false;
    protected boolean flagAuto = false;
    protected int z = 0;
    protected int progress = 0;

    private CountDownTimer countDownTimer;
    private Timer timer;
    private CusRunnable task;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (CusApplication.mSocket == null || CusApplication.mSocket.connected()) {
            try {
                CusApplication.mSocket = IO.socket(CusApplication.BASE_URL);
            } catch (Exception e) {
                e.printStackTrace();
            }
            CusApplication.mSocket.connect();
        }

        addControls();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_test:
                Intent test = new Intent(this, TestActivity.class);
                startActivity(test);
                break;
            case R.id.menu_camera:
                Intent camera = new Intent(this, CameraActivity.class);
                startActivity(camera);
                break;
            case R.id.menu_settings:
                Intent settings = new Intent(this, SettingsActivity.class);
                startActivity(settings);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        timer = new Timer();
        if (flagMotor) {
            runAnimation();
        }
        addEvents();
        CusApplication.mSocket.emit("requestmodelonline");
    }

    @Override
    protected void onPause() {
        super.onPause();
        removeEvents();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            timer.cancel();
            timer = null;
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    private void removeEvents() {
        btnOff.setOnClickListener(null);
        btnOn.setOnClickListener(null);
        btnAuto.setOnClickListener(null);

        CusApplication.mSocket.off();
    }

    private void addEvents() {
        btnOff.setOnClickListener(this);
        btnOn.setOnClickListener(this);
        btnAuto.setOnClickListener(this);

        //Message Auto
        CusApplication.mSocket.on("serversendclient_4", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject object = (JSONObject) args[0];
                        Log.e("status", object.toString());
                        try {
                            String time = object.getString("time");
                            String num = object.getString("num");
                            z = Integer.valueOf(time);
                            runTimer();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });

        //Listener Status of Motor
        CusApplication.mSocket.on("serversendclient_3", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void run() {
                        JSONObject object = (JSONObject) args[0];
                        try {
                            String motor = object.getString("motor");
                            String auto = object.getString("auto");
                            String fmotor = flagMotor ? "on" : "off";
                            String fauto = flagAuto ? "on" : "off";
                            flagAuto = auto.equals("on");
                            flagMotor = motor.equals("on");
                            if (!(motor.equals(fmotor) && auto.equals(fauto))) {
                                Log.e("status", "ChangeStatus " + object.toString());
                                changeStatusAuto();
                                if (auto.equals("on")) {
                                    if (motor.equals("on")) {
                                        runAnimation();
                                    } else {
                                        stopAnimation();
                                    }
                                } else {
                                    if (motor.equals("on")) {
                                        changeMotorOn();
                                        runAnimation();
                                    } else {
                                        changeMotorOff();
                                        stopAnimation();
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });

        //Listener Temp and Hum
        CusApplication.mSocket.on("serversendclient_1", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject object = (JSONObject) args[0];
                        Log.e("status", object.toString());
                        try {
                            CusApplication.arrModel.add(new Model(object.getString("temp"), object.getString("hum")));
                            txtTemp.setText(object.getString("temp") + "°C");
                            txtHum.setText(object.getString("hum") + "%");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });

        //Listener model login
        CusApplication.mSocket.on("serversendclient_2", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject obj = (JSONObject) args[0];
                        Log.e("status", obj.toString());
                        try {
                            if (obj.get("status").equals("on")) {
                                flagOnline = true;
                                txtStatus.setText("Online");
                                txtStatus.setBackgroundColor(Color.GREEN);
                            } else {
                                flagOnline = false;
                                txtStatus.setText("Offline");
                                txtStatus.setBackgroundColor(Color.RED);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });

        //Listener model login
        CusApplication.mSocket.on("resultmodelonline", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject obj = (JSONObject) args[0];
                        Log.e("status", obj.toString());
                        try {
                            if (obj.get("status").equals("on")) {
                                flagOnline = true;
                                txtStatus.setText("Online");
                                txtStatus.setBackgroundColor(Color.GREEN);
                            } else {
                                flagOnline = false;
                                txtStatus.setText("Offline");
                                txtStatus.setBackgroundColor(Color.RED);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });

        //Listener message
        CusApplication.mSocket.on("message", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.e("status", args[0].toString());
                        Toast.makeText(getApplicationContext(), args[0].toString(), Toast.LENGTH_LONG);
                    }
                });
            }
        });

    }

    private void runTimer() {
        countDownTimer = new CountDownTimer(z * 1000, 1000) {

            public void onTick(long millisUntilFinished) {
                mWaveLoadingView.setBottomTitle(z + "s");
                z--;
            }

            public void onFinish() {
                mWaveLoadingView.setBottomTitle("");
            }
        };
        countDownTimer.start();
    }

    private void addControls() {
        btnOn = findViewById(R.id.btn_on);
        btnOff = findViewById(R.id.btn_off);
        btnAuto = findViewById(R.id.btn_auto);
        txtHum = findViewById(R.id.txt_hum);
        txtTemp = findViewById(R.id.txt_temp);
        txtStatus = findViewById(R.id.txt_status);

        mWaveLoadingView = findViewById(R.id.waveLoadingView);
//        mWaveLoadingView.setShapeType(WaveLoadingView.ShapeType.CIRCLE);
        //mWaveLoadingView.setTopTitle("Top Title");
//        mWaveLoadingView.setCenterTitleColor(Color.GRAY);
//        mWaveLoadingView.setBottomTitleSize(18);
//        mWaveLoadingView.setProgressValue(80);
//        mWaveLoadingView.setBorderWidth(10);
//        mWaveLoadingView.setAmplitudeRatio(60);
        //mWaveLoadingView.setWaveColor(Color.BLUE);
        //mWaveLoadingView.setBorderColor(Color.BLUE);
//        mWaveLoadingView.setTopTitleStrokeColor(Color.BLUE);
//        mWaveLoadingView.setTopTitleStrokeWidth(1);
        mWaveLoadingView.setAnimDuration(3000);
        mWaveLoadingView.pauseAnimation();
        mWaveLoadingView.resumeAnimation();
        mWaveLoadingView.cancelAnimation();
        mWaveLoadingView.startAnimation();

        task = new CusRunnable();
    }

    synchronized private void runAnimation() {
        Log.e("status", "runAnimation");
        timer.cancel();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(task);
            }
        }, 1000, 1000);
    }

    private void stopAnimation() {
        Log.e("status", "stopAnimation");
        try {
            mWaveLoadingView.setCenterTitle("");
            try {
                countDownTimer.onFinish();
                countDownTimer.cancel();
            } catch (Exception e) {
                e.printStackTrace();
            }
            timer.cancel();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        if (!flagOnline) return;
        switch (view.getId()) {
            case R.id.btn_on:
                flagMotor = true;
                runAnimation();
                JSONObject obj1 = new JSONObject();
                try {
                    obj1.put("status", "on");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                CusApplication.mSocket.emit("clientsenddata_1", obj1);
                changeMotorOn();
                break;
            case R.id.btn_off:
                flagMotor = false;
                stopAnimation();
                JSONObject obj2 = new JSONObject();
                try {
                    obj2.put("status", "off");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                CusApplication.mSocket.emit("clientsenddata_1", obj2);
                changeMotorOff();
                break;
            case R.id.btn_auto:
                btnAuto.setEnabled(false);
                flagAuto = !flagAuto;
                JSONObject obj3 = new JSONObject();
                try {
                    obj3.put("status", flagAuto ? "on" : "off");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                CusApplication.mSocket.emit("clientsenddata_2", obj3);
                changeStatusAuto();
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btnAuto.setEnabled(true);
                            }
                        });
                    }
                }, 5000);
                break;
        }
    }

    private void changeStatusAuto() {
        if (flagAuto) {
            btnAuto.setText("Dừng");
            btnOn.setEnabled(false);
            btnOff.setEnabled(false);
        } else {
            try {
                countDownTimer.onFinish();
                countDownTimer.cancel();
            } catch (Exception e) {
                e.printStackTrace();
            }
            stopAnimation();
            btnAuto.setText("Tự động");
            btnOn.setEnabled(true);
            btnOff.setEnabled(false);
        }
    }

    private void changeMotorOn() {
        btnOff.setEnabled(true);
        btnOn.setEnabled(false);
        btnAuto.setEnabled(false);
    }

    private void changeMotorOff() {
        mWaveLoadingView.setCenterTitle("");
        stopAnimation();
        try {
            countDownTimer.onFinish();
            countDownTimer.cancel();
        } catch (Exception e) {
            e.printStackTrace();
        }
        btnOn.setEnabled(true);
        btnOff.setEnabled(false);
        btnAuto.setEnabled(true);
    }

    public class CusRunnable implements Runnable {

        @Override
        synchronized public void run() {
            if (flagMotor || flagAuto) {
                progress += 10;
                if (progress > 100) {
                    progress = 0;
                }
                mWaveLoadingView.setProgressValue(progress);
                mWaveLoadingView.setCenterTitle("đang bơm");
            }
        }
    }
}
