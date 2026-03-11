package com.example.timerapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;

public class TimerService extends Service {

    public static final String CHANNEL_ID = "TimerServiceChannel";
    public static final String CHANNEL_WARNING_ID = "TimerWarningChannel";
    public static final int NOTIFICATION_ID = 1;

    private final IBinder binder = new TimerBinder();
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private long remainingMillis;
    private boolean isRunning = false;
    private boolean warningFired = false;
    private TimerCallback callback;

    public static final long TOTAL_DURATION_MS = 30 * 60 * 1000L; // 30 minutes
    public static final long WARNING_THRESHOLD_MS = 2 * 60 * 1000L; // 2 minutes

    public interface TimerCallback {
        void onTick(long remainingMs);
        void onWarning();
        void onFinished();
    }

    public class TimerBinder extends Binder {
        TimerService getService() {
            return TimerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("Timer running..."));
        return START_STICKY;
    }

    public void setCallback(TimerCallback callback) {
        this.callback = callback;
    }

    public void startTimer(long remainingMs) {
        this.remainingMillis = remainingMs;
        this.isRunning = true;
        this.warningFired = false;
        scheduleNext();
    }

    public void stopTimer() {
        isRunning = false;
        if (timerRunnable != null) {
            handler.removeCallbacks(timerRunnable);
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public long getRemainingMillis() {
        return remainingMillis;
    }

    private void scheduleNext() {
        timerRunnable = () -> {
            if (!isRunning) return;
            remainingMillis -= 1000;

            if (remainingMillis <= 0) {
                remainingMillis = 0;
                isRunning = false;
                if (callback != null) callback.onFinished();
                fireAlarm("Time's up! Your 30-minute session has ended.");
                stopSelf();
                return;
            }

            if (!warningFired && remainingMillis <= WARNING_THRESHOLD_MS) {
                warningFired = true;
                if (callback != null) callback.onWarning();
                fireAlarm("⚠️ 2 minutes remaining!");
            }

            if (callback != null) callback.onTick(remainingMillis);
            updateNotification(formatTime(remainingMillis) + " remaining");
            scheduleNext();
        };
        handler.postDelayed(timerRunnable, 1000);
    }

    private void fireAlarm(String message) {
        // Vibrate
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 500, 200, 500, 200, 500};
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        }

        // Sound notification
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_WARNING_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Daily Timer")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(soundUri)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 200, 500});

        nm.notify(2, builder.build());
    }

    private void createNotificationChannels() {
        // Foreground service channel
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID, "Timer Service", NotificationManager.IMPORTANCE_LOW);
        serviceChannel.setDescription("Shows timer is running in background");

        // Warning channel
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);

        NotificationChannel warningChannel = new NotificationChannel(
                CHANNEL_WARNING_ID, "Timer Warnings", NotificationManager.IMPORTANCE_HIGH);
        warningChannel.setDescription("Alerts for timer warnings");
        warningChannel.setSound(alarmSound, audioAttributes);
        warningChannel.enableVibration(true);

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
        manager.createNotificationChannel(warningChannel);
    }

    private Notification buildNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Daily Timer")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    public static String formatTime(long millis) {
        long minutes = millis / 60000;
        long seconds = (millis % 60000) / 1000;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTimer();
    }
}
