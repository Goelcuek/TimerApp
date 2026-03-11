package com.example.timerapp;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TimerService.TimerCallback {

    private static final String PREFS_NAME = "TimerPrefs";
    private static final String KEY_REMAINING_MS = "remainingMs";
    private static final String KEY_IS_CLOCKED_IN = "isClockedIn";
    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_TIME_USED_MS = "timeUsedMs";
    private static final String KEY_LAST_RESET_DATE = "lastResetDate";
    private static final String KEY_LAST_ACTION_TIME = "lastActionTime";

    private TimerService timerService;
    private boolean serviceBound = false;

    private TextView tvDate, tvTimeRemaining, tvStatus, tvSessionsCount, tvTimeUsed, tvLastAction;
    private Button btnClockInOut;
    private CircularTimerView circularTimer;

    private boolean isClockedIn = false;
    private long remainingMs;
    private int sessionsCount = 0;
    private long timeUsedMs = 0;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TimerService.TimerBinder binder = (TimerService.TimerBinder) service;
            timerService = binder.getService();
            timerService.setCallback(MainActivity.this);
            serviceBound = true;

            // Sync UI with service state
            if (timerService.isRunning()) {
                remainingMs = timerService.getRemainingMillis();
                updateTimerUI(remainingMs);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        requestPermissions();
        loadState();
        setupDateDisplay();
        setupButton();
    }

    private void initViews() {
        tvDate = findViewById(R.id.tvDate);
        tvTimeRemaining = findViewById(R.id.tvTimeRemaining);
        tvStatus = findViewById(R.id.tvStatus);
        tvSessionsCount = findViewById(R.id.tvSessionsCount);
        tvTimeUsed = findViewById(R.id.tvTimeUsed);
        tvLastAction = findViewById(R.id.tvLastAction);
        btnClockInOut = findViewById(R.id.btnClockInOut);
        circularTimer = findViewById(R.id.circularTimer);
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Check if we need to reset (new day)
        String today = getTodayDate();
        String lastResetDate = prefs.getString(KEY_LAST_RESET_DATE, "");

        if (!today.equals(lastResetDate)) {
            // New day - reset everything
            resetForNewDay(prefs, today);
        } else {
            // Same day - restore state
            remainingMs = prefs.getLong(KEY_REMAINING_MS, TimerService.TOTAL_DURATION_MS);
            isClockedIn = prefs.getBoolean(KEY_IS_CLOCKED_IN, false);
            sessionsCount = prefs.getInt(KEY_SESSIONS, 0);
            timeUsedMs = prefs.getLong(KEY_TIME_USED_MS, 0);

            // If was clocked in when app closed, clock out automatically
            if (isClockedIn) {
                long lastActionTime = prefs.getLong(KEY_LAST_ACTION_TIME, System.currentTimeMillis());
                long elapsed = System.currentTimeMillis() - lastActionTime;
                remainingMs = Math.max(0, remainingMs - elapsed);
                timeUsedMs += elapsed;
                isClockedIn = false;
                saveState();
            }
        }

        updateAllUI();
    }

    private void resetForNewDay(SharedPreferences prefs, String today) {
        remainingMs = TimerService.TOTAL_DURATION_MS;
        isClockedIn = false;
        sessionsCount = 0;
        timeUsedMs = 0;

        prefs.edit()
                .putString(KEY_LAST_RESET_DATE, today)
                .putLong(KEY_REMAINING_MS, remainingMs)
                .putBoolean(KEY_IS_CLOCKED_IN, false)
                .putInt(KEY_SESSIONS, 0)
                .putLong(KEY_TIME_USED_MS, 0)
                .apply();
    }

    private void saveState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putLong(KEY_REMAINING_MS, remainingMs)
                .putBoolean(KEY_IS_CLOCKED_IN, isClockedIn)
                .putInt(KEY_SESSIONS, sessionsCount)
                .putLong(KEY_TIME_USED_MS, timeUsedMs)
                .putLong(KEY_LAST_ACTION_TIME, System.currentTimeMillis())
                .apply();
    }

    private void setupDateDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(new Date()));
    }

    private void setupButton() {
        btnClockInOut.setOnClickListener(v -> {
            if (remainingMs <= 0) return; // No time left

            if (!isClockedIn) {
                clockIn();
            } else {
                clockOut();
            }
        });
    }

    private void clockIn() {
        isClockedIn = true;
        sessionsCount++;

        // Start/bind service
        Intent serviceIntent = new Intent(this, TimerService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Start timer in service once bound (handled in onServiceConnected via flag)
        // We use a small delay to ensure service is connected
        btnClockInOut.postDelayed(() -> {
            if (serviceBound && timerService != null) {
                timerService.startTimer(remainingMs);
            }
        }, 200);

        String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
        tvLastAction.setText("Clocked in at " + time);

        updateButtonUI();
        saveState();
    }

    private void clockOut() {
        isClockedIn = false;

        if (serviceBound && timerService != null) {
            remainingMs = timerService.getRemainingMillis();
            timerService.stopTimer();
            unbindService(serviceConnection);
            serviceBound = false;

            // Stop service
            Intent serviceIntent = new Intent(this, TimerService.class);
            stopService(serviceIntent);
        }

        timeUsedMs = TimerService.TOTAL_DURATION_MS - remainingMs;

        String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
        tvLastAction.setText("Clocked out at " + time);

        updateButtonUI();
        updateStatsUI();
        saveState();
    }

    // TimerService.TimerCallback implementation
    @Override
    public void onTick(long remainingMs) {
        this.remainingMs = remainingMs;
        updateTimerUI(remainingMs);
    }

    @Override
    public void onWarning() {
        runOnUiThread(() -> {
            circularTimer.setWarning(true);
            tvStatus.setText("⚠️ 2 min left!");
            tvStatus.setTextColor(0xFFE74C3C);
        });
    }

    @Override
    public void onFinished() {
        runOnUiThread(() -> {
            isClockedIn = false;
            remainingMs = 0;
            timeUsedMs = TimerService.TOTAL_DURATION_MS;

            if (serviceBound) {
                unbindService(serviceConnection);
                serviceBound = false;
            }

            updateAllUI();
            tvLastAction.setText("Time's up for today!");
            saveState();
        });
    }

    private void updateTimerUI(long millis) {
        runOnUiThread(() -> {
            tvTimeRemaining.setText(TimerService.formatTime(millis));
            float progress = (float) millis / TimerService.TOTAL_DURATION_MS;
            circularTimer.setProgress(progress);
            timeUsedMs = TimerService.TOTAL_DURATION_MS - millis;
            updateStatsUI();
        });
    }

    private void updateAllUI() {
        updateTimerUI(remainingMs);
        updateButtonUI();
        updateStatsUI();
    }

    private void updateButtonUI() {
        if (remainingMs <= 0) {
            btnClockInOut.setText("NO TIME LEFT");
            btnClockInOut.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF999999));
            btnClockInOut.setEnabled(false);
            tvStatus.setText("Done for today");
        } else if (isClockedIn) {
            btnClockInOut.setText("CLOCK OUT");
            btnClockInOut.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFE74C3C));
            tvStatus.setText("Running...");
        } else {
            btnClockInOut.setText("CLOCK IN");
            btnClockInOut.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF4A90D9));
            tvStatus.setText("Paused");
            if (sessionsCount == 0) tvStatus.setText("Ready");
        }
    }

    private void updateStatsUI() {
        tvSessionsCount.setText(String.valueOf(sessionsCount));
        tvTimeUsed.setText(TimerService.formatTime(timeUsedMs));
    }

    private String getTodayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }
}
