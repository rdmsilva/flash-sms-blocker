package com.flashblocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * Fires when a timed pause ({@link FlashSmsAccessibilityService#pause()}) runs
 * out. Android gives no API for an app to re-enable its own accessibility
 * service, so the best we can do is remind the user with a notification that
 * opens the accessibility settings screen directly — one tap away from
 * turning the switch back on.
 */
public class PauseExpiredReceiver extends BroadcastReceiver {
    private static final String TAG = "FlashBlocker/A11y";
    private static final String CHANNEL_ID = "pause_expired";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        BlockStats stats = new BlockStats(
            context.getSharedPreferences(BlockStats.PREFS_NAME, Context.MODE_PRIVATE));
        stats.recordEvent("PAUSA", "Tempo de pausa encerrado — reative a acessibilidade");

        NotificationManager nm =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            Log.w(TAG, "NotificationManager indisponível, não foi possível lembrar o usuário");
            return;
        }

        Intent settingsIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Fim da pausa", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        Notification notification = builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Pausa de 15 min encerrada")
            .setContentText("Toque para reativar o bloqueio de SMS Flash")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build();

        nm.notify(NOTIFICATION_ID, notification);
    }
}
