package com.tuankhai.automaticsystem;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;
import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


import me.itangqi.waveloadingview.WaveLoadingView;

import static com.tuankhai.automaticsystem.SettingsActivity.PREF_AUTH_PASSWORD;
import static com.tuankhai.automaticsystem.SettingsActivity.PREF_AUTH_USERNAME;
import static com.tuankhai.automaticsystem.SettingsActivity.PREF_IPCAM_URL;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, IVLCVout.OnNewVideoLayoutListener {

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

    //StreamVideo
    private static final String TAG = "JavaActivity";
    private static final int SURFACE_BEST_FIT = 0;
    private static final int SURFACE_FIT_SCREEN = 1;
    private static final int SURFACE_FILL = 2;
    private static final int SURFACE_16_9 = 3;
    private static final int SURFACE_4_3 = 4;
    private static final int SURFACE_ORIGINAL = 5;
    private static int CURRENT_SIZE = SURFACE_16_9;

    private FrameLayout mVideoSurfaceFrame = null;
    private SurfaceView mVideoSurface = null;
    private SurfaceView mSubtitlesSurface = null;
    private TextureView mVideoTexture = null;
    private View mVideoView = null;

    private final Handler mHandler = new Handler();
    private View.OnLayoutChangeListener mOnLayoutChangeListener = null;

    private LibVLC mLibVLC = null;
    private MediaPlayer mMediaPlayer = null;
    private int mVideoHeight = 0;
    private int mVideoWidth = 0;
    private int mVideoVisibleHeight = 0;
    private int mVideoVisibleWidth = 0;
    private int mVideoSarNum = 0;
    private int mVideoSarDen = 0;

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
        initCamera();
    }

    private void initCamera() {
        final ArrayList<String> args = new ArrayList<>();
        args.add("-vvv");
        mLibVLC = new LibVLC(this, args);
        mMediaPlayer = new MediaPlayer(mLibVLC);

        mVideoSurfaceFrame = findViewById(R.id.video_surface_frame);
        ViewStub stub = findViewById(R.id.surface_stub);
        mVideoSurface = (SurfaceView) stub.inflate();
        mVideoView = mVideoSurface;
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
                Intent camera = new Intent(this, StreamActivity.class);
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
    protected void onStart() {
        super.onStart();
        final IVLCVout vlcVout = mMediaPlayer.getVLCVout();
        if (mVideoSurface != null) {
            vlcVout.setVideoView(mVideoSurface);
            if (mSubtitlesSurface != null)
                vlcVout.setSubtitlesView(mSubtitlesSurface);
        } else
            vlcVout.setVideoView(mVideoTexture);
        vlcVout.attachViews(this);

        Media media = new Media(mLibVLC, Uri.parse(getURL()));
        mMediaPlayer.setMedia(media);
        media.release();
        mMediaPlayer.play();

        if (mOnLayoutChangeListener == null) {
            mOnLayoutChangeListener = new View.OnLayoutChangeListener() {
                private final Runnable mRunnable = new Runnable() {
                    @Override
                    public void run() {
                        //updateVideoSurfaces();
                    }
                };

                @Override
                public void onLayoutChange(View v, int left, int top, int right,
                                           int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                        mHandler.removeCallbacks(mRunnable);
                        mHandler.post(mRunnable);
                    }
                }
            };
        }
        mVideoSurfaceFrame.addOnLayoutChangeListener(mOnLayoutChangeListener);
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
        if (mOnLayoutChangeListener != null) {
            mVideoSurfaceFrame.removeOnLayoutChangeListener(mOnLayoutChangeListener);
            mOnLayoutChangeListener = null;
        }

        mMediaPlayer.stop();

        mMediaPlayer.getVLCVout().detachViews();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMediaPlayer.release();
        mLibVLC.release();
    }

    private void changeMediaPlayerLayout(int displayW, int displayH) {
        /* Change the video placement using the MediaPlayer API */
        switch (CURRENT_SIZE) {
            case SURFACE_BEST_FIT:
                mMediaPlayer.setAspectRatio(null);
                mMediaPlayer.setScale(0);
                break;
            case SURFACE_FIT_SCREEN:
            case SURFACE_FILL: {
                Media.VideoTrack vtrack = mMediaPlayer.getCurrentVideoTrack();
                if (vtrack == null)
                    return;
                final boolean videoSwapped = vtrack.orientation == Media.VideoTrack.Orientation.LeftBottom
                        || vtrack.orientation == Media.VideoTrack.Orientation.RightTop;
                if (CURRENT_SIZE == SURFACE_FIT_SCREEN) {
                    int videoW = vtrack.width;
                    int videoH = vtrack.height;

                    if (videoSwapped) {
                        int swap = videoW;
                        videoW = videoH;
                        videoH = swap;
                    }
                    if (vtrack.sarNum != vtrack.sarDen)
                        videoW = videoW * vtrack.sarNum / vtrack.sarDen;

                    float ar = videoW / (float) videoH;
                    float dar = displayW / (float) displayH;

                    float scale;
                    if (dar >= ar)
                        scale = displayW / (float) videoW; /* horizontal */
                    else
                        scale = displayH / (float) videoH; /* vertical */
                    mMediaPlayer.setScale(scale);
                    mMediaPlayer.setAspectRatio(null);
                } else {
                    mMediaPlayer.setScale(0);
                    mMediaPlayer.setAspectRatio(!videoSwapped ? "" + displayW + ":" + displayH
                            : "" + displayH + ":" + displayW);
                }
                break;
            }
            case SURFACE_16_9:
                mMediaPlayer.setAspectRatio("16:9");
                mMediaPlayer.setScale(0);
                break;
            case SURFACE_4_3:
                mMediaPlayer.setAspectRatio("4:3");
                mMediaPlayer.setScale(0);
                break;
            case SURFACE_ORIGINAL:
                mMediaPlayer.setAspectRatio(null);
                mMediaPlayer.setScale(1);
                break;
        }
    }

    private int dpToPx(int dp) {
        Resources r = getResources();
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());
        return (int) px;
    }

    private void updateVideoSurfaces() {
        int sw = getWindow().getDecorView().getWidth();
        int sh = getWindow().getDecorView().getHeight();

        // sanity check
        if (sw * sh == 0) {
            Log.e(TAG, "Invalid surface size");
            return;
        }

        mMediaPlayer.getVLCVout().setWindowSize(sw, sh);

        ViewGroup.LayoutParams lp = mVideoView.getLayoutParams();
        if (mVideoWidth * mVideoHeight == 0) {
            /* Case of OpenGL vouts: handles the placement of the video using MediaPlayer API */
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mVideoView.setLayoutParams(lp);
            lp = mVideoSurfaceFrame.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mVideoSurfaceFrame.setLayoutParams(lp);
            changeMediaPlayerLayout(sw, sh);
            return;
        }

        if (lp.width == lp.height && lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
            /* We handle the placement of the video using Android View LayoutParams */
            mMediaPlayer.setAspectRatio(null);
            mMediaPlayer.setScale(0);
        }

        double dw = sw, dh = sh;
        final boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        if (sw > sh && isPortrait || sw < sh && !isPortrait) {
            dw = sh;
            dh = sw;
        }

        // compute the aspect ratio
        double ar, vw;
        if (mVideoSarDen == mVideoSarNum) {
            /* No indication about the density, assuming 1:1 */
            vw = mVideoVisibleWidth;
            ar = (double) mVideoVisibleWidth / (double) mVideoVisibleHeight;
        } else {
            /* Use the specified aspect ratio */
            vw = mVideoVisibleWidth * (double) mVideoSarNum / mVideoSarDen;
            ar = vw / mVideoVisibleHeight;
        }

        // compute the display aspect ratio
        double dar = dw / dh;

        switch (CURRENT_SIZE) {
            case SURFACE_BEST_FIT:
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_FIT_SCREEN:
                if (dar >= ar)
                    dh = dw / ar; /* horizontal */
                else
                    dw = dh * ar; /* vertical */
                break;
            case SURFACE_FILL:
                break;
            case SURFACE_16_9:
                ar = 16.0 / 9.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_4_3:
                ar = 4.0 / 3.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_ORIGINAL:
                dh = mVideoVisibleHeight;
                dw = vw;
                break;
        }

        // set display size
        lp.width = (int) Math.ceil(dw * mVideoWidth / mVideoVisibleWidth);
        lp.height = (int) Math.ceil(dh * mVideoHeight / mVideoVisibleHeight);
        mVideoView.setLayoutParams(lp);
        if (mSubtitlesSurface != null)
            mSubtitlesSurface.setLayoutParams(lp);

        // set frame size (crop if necessary)
        lp = mVideoSurfaceFrame.getLayoutParams();
        lp.width = (int) Math.floor(dw);
        lp.height = (int) Math.floor(dh);
        mVideoSurfaceFrame.setLayoutParams(lp);

        mVideoView.invalidate();
        if (mSubtitlesSurface != null)
            mSubtitlesSurface.invalidate();
    }

    private String getPreference(String key) {
        return PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString(key, "");
    }

    private boolean useAuth() {
        return PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean("authentication", true);
    }

    private String getURL() {
        String result;
        if (useAuth()) {
            result = "rtsp://" + getPreference(PREF_AUTH_USERNAME) + ":" + getPreference(PREF_AUTH_PASSWORD) + "@"
                    + getPreference(PREF_IPCAM_URL);
        } else {
            result = "rtsp://" + getPreference(PREF_IPCAM_URL);
        }
        Log.e("status", result);
        //return "http://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_640x360.m4v";
        return result;
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
                mWaveLoadingView.setCenterTitle(z + "s");
                z--;
            }

            public void onFinish() {
                mWaveLoadingView.setCenterTitle("");
                //stopAnimation();
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
            timer.cancel();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            countDownTimer.onFinish();
            countDownTimer.cancel();
        } catch (Exception e) {
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
        Log.e("status", "motorOff");
        stopAnimation();
        btnOn.setEnabled(true);
        btnOff.setEnabled(false);
        btnAuto.setEnabled(true);
    }

    @Override
    public void onNewVideoLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        mVideoWidth = width;
        mVideoHeight = height;
        mVideoVisibleWidth = visibleWidth;
        mVideoVisibleHeight = visibleHeight;
        mVideoSarNum = sarNum;
        mVideoSarDen = sarDen;
        //updateVideoSurfaces();
    }

    public class CusRunnable implements Runnable {

        @Override
        synchronized public void run() {
            if (flagMotor) {
                progress += 10;
                if (progress > 100) {
                    progress = 0;
                }
                mWaveLoadingView.setProgressValue(progress);
            }
        }
    }
}
