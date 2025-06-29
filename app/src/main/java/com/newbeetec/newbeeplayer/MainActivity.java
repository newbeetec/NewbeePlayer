package com.newbeetec.newbeeplayer;

import android.annotation.SuppressLint;
import android.content.*;
import android.hardware.*;
import android.media.*;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.*;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements VolumeChangeObserver.VolumeChangeListener {

    private static final int REQUEST_PICK_MUSIC = 1;

    private MediaPlayer mediaPlayer;
    private Visualizer visualizer;
    private SensorManager sensorManager;
    private Sensor proximitySensor;
    private PowerManager.WakeLock wakeLock;

    private FFTView fftView;
    private MaterialButton btnPlayPause;
    private SeekBar seekBar, seekVolume;
    private TextView tvCurrentTime, tvTotalTime;
    private Switch switchLoop, switchEarpiece;
    private Timer progressTimer;

    private boolean usingEarpiece = false;
    private AudioManager audioManager;

    private VolumeChangeObserver mVolumeChangeObserver;

    private Uri uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mVolumeChangeObserver = new VolumeChangeObserver(this);
        mVolumeChangeObserver.setVolumeChangeListener(this);
        // 初始化UI
        initViews();
        setupMediaPlayer();
        setupSensors();
        setupVolumeControl();
    }

    private void initViews() {
        fftView = findViewById(R.id.fftView);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        seekBar = findViewById(R.id.seekBar);
        seekVolume = findViewById(R.id.seekVolume);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        switchLoop = findViewById(R.id.switchLoop);
        switchEarpiece = findViewById(R.id.switchEarpiece);

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        findViewById(R.id.btnRewind).setOnClickListener(v -> seek(-1000));
        findViewById(R.id.btnForward).setOnClickListener(v -> seek(1000));
        findViewById(R.id.btnOpen).setOnClickListener(v -> openMusicFile());

        switchEarpiece.setOnCheckedChangeListener((buttonView, isChecked) -> {
            usingEarpiece = isChecked;
            setAudioOutput();
        });

        switchLoop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(isChecked);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void setupMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(mp -> {
            btnPlayPause.setIconResource(R.drawable.ic_play);
        });
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "NewBeePlayer:ProximityLock");
    }

    private void setupVolumeControl() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        int maxVolume = audioManager.getStreamMaxVolume(usingEarpiece ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(usingEarpiece ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);

        seekVolume.setMax(maxVolume);
        seekVolume.setProgress(currentVolume);

        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                audioManager.setStreamVolume(usingEarpiece ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC, progress, 0);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void openMusicFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, REQUEST_PICK_MUSIC);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mVolumeChangeObserver.registerReceiver();
        if (requestCode == REQUEST_PICK_MUSIC && resultCode == RESULT_OK && data != null) {
            uri = data.getData();
            loadMusic(uri);
        }
    }

    private void loadMusic(Uri uri) {
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setAudioStreamType(usingEarpiece ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
            mediaPlayer.setLooping(switchLoop.isChecked());
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                setupVisualizer();
                updateTimeDisplay();
                btnPlayPause.setIconResource(R.drawable.ic_play);
            });
            mediaPlayer.setOnErrorListener((mp, what, where) -> {
                Toast.makeText(this, "播放文件时异常(" + what + "," + where + ")", Toast.LENGTH_LONG).show();
                return true;
            });
        } catch (IOException e) {
            Toast.makeText(this, "加载文件失败", Toast.LENGTH_SHORT).show();
            mediaPlayer.reset();
        }
    }

    private void setupVisualizer() {
        if (visualizer != null) {
            visualizer.release();
        }

        try {
            visualizer = new Visualizer(mediaPlayer.getAudioSessionId());
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    fftView.updateFFT(fft);
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);

        } catch (Exception e) {
            Toast.makeText(this, "可视化加载失败，请检查权限", Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePlayPause() {
        if (!mediaPlayer.isPlaying()) {
            if (mediaPlayer.getDuration() <= 0) return;
            mediaPlayer.start();
            btnPlayPause.setIconResource(R.drawable.ic_pause);
            startProgressUpdates();
            if (visualizer != null) visualizer.setEnabled(true);
        } else {
            mediaPlayer.pause();
            btnPlayPause.setIconResource(R.drawable.ic_play);
            stopProgressUpdates();
            if (visualizer != null) visualizer.setEnabled(false);
        }
        updateTimeDisplay();
    }

    private void seek(int milliseconds) {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            int newPosition = mediaPlayer.getCurrentPosition() + milliseconds;
            newPosition = Math.max(0, Math.min(newPosition, mediaPlayer.getDuration()));
            mediaPlayer.seekTo(newPosition);
        }
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressTimer = new Timer();
        progressTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        seekBar.setProgress(mediaPlayer.getCurrentPosition());
                        updateTimeDisplay();
                    }
                });
            }
        }, 0, 200);
    }

    private void stopProgressUpdates() {
        if (progressTimer != null) {
            progressTimer.cancel();
            progressTimer = null;
        }
    }

    @SuppressLint("DefaultLocale")
    private void updateTimeDisplay() {
        if (mediaPlayer != null) {
            float current = (float) mediaPlayer.getCurrentPosition() / 1000;
            float total = (float) mediaPlayer.getDuration() / 1000;

            tvCurrentTime.setText(String.format("%d:%.3f", (int) current / 60, current % 60));
            tvTotalTime.setText(String.format("%d:%.3f", (int) total / 60, total % 60));
            seekBar.setMax(mediaPlayer.getDuration());
            seekBar.setProgress(mediaPlayer.getCurrentPosition());
        }
    }

    private void setAudioOutput() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        btnPlayPause.setIconResource(R.drawable.ic_play);
        stopProgressUpdates();
        if (visualizer != null) visualizer.setEnabled(false);
        if (usingEarpiece) {
            // 注册距离传感器
            sensorManager.registerListener(proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            // 取消注册距离传感器
            sensorManager.unregisterListener(proximityListener);
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
        int maxVolume = audioManager.getStreamMaxVolume(usingEarpiece ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(usingEarpiece ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);

        seekVolume.setMax(maxVolume);
        seekVolume.setProgress(currentVolume);
        updateTimeDisplay();
        if (uri != null) loadMusic(uri);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mVolumeChangeObserver.registerReceiver();
        if (visualizer != null) visualizer.setEnabled(true);
        if (usingEarpiece && proximitySensor != null) {
            sensorManager.registerListener(proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mVolumeChangeObserver.unregisterReceiver();
        if (visualizer != null) visualizer.setEnabled(false);
        sensorManager.unregisterListener(proximityListener);
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private final SensorEventListener proximityListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                if (event.values[0] < event.sensor.getMaximumRange()) {
                    // 靠近耳朵
                    if (!wakeLock.isHeld()) {
                        wakeLock.acquire();
                    }
                    if (visualizer != null) visualizer.setEnabled(false);
                } else {
                    // 离开耳朵
                    if (wakeLock.isHeld()) {
                        wakeLock.release();
                    }
                    if (visualizer != null) visualizer.setEnabled(true);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressUpdates();
        if (visualizer != null) visualizer.release();
        if (mediaPlayer != null) mediaPlayer.release();
        if (wakeLock.isHeld()) wakeLock.release();
        if (visualizer != null) visualizer.setEnabled(false);
    }

    @Override
    public void onVolumeChanged(int volume) {
        //系统媒体音量改变时的回调
        int currentVolume = audioManager.getStreamVolume(usingEarpiece ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
        seekVolume.setProgress(currentVolume);
    }
}

