package io.github.chrisoo.expy;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Listens to the phone's notifications (once the user grants "notification access") and keeps the ones that look
 * like a payment. Nothing leaves the phone: the payment waits in PayStore until Expy shows it to the user.
 */
public class PayListener extends NotificationListenerService {
    static final String CHANNEL = "expy_pay";
    static final int NOTIF_ID = 4021;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (!PayStore.isOn(this)) return;
            String pkg = sbn.getPackageName();
            if (getPackageName().equals(pkg)) return;
            Notification n = sbn.getNotification();
            if (n == null || n.extras == null) return;
            if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0 || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            Bundle ex = n.extras;
            String title = str(ex.getCharSequence(Notification.EXTRA_TITLE));
            CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
            String text = str(big != null ? big : ex.getCharSequence(Notification.EXTRA_TEXT));
            handle(this, pkg, appLabel(ex, pkg), title, text, sbn.getPostTime());
        } catch (Exception ignored) { }
    }

    /** Shared with the plugin's test button: parse, store, notify. Returns true when a payment was kept. */
    static boolean handle(Context c, String pkg, String app, String title, String text, long when) {
        PayParser.Result r = PayParser.parse(pkg, title, text);
        if (r == null) return false;
        String id = PayStore.add(c, pkg, app, title, text, r, when);
        if (id == null) return false;
        notifyDetected(c, r, app);
        PayNotifPlugin.emit(c);
        return true;
    }

    private String appLabel(Bundle ex, String pkg) {
        try {
            Object ai = ex.get("android.appInfo");
            if (ai instanceof ApplicationInfo) return getPackageManager().getApplicationLabel((ApplicationInfo) ai).toString();
        } catch (Exception ignored) { }
        if (PayParser.WALLET.equals(pkg)) return "Google Wallet";
        return pkg;
    }

    private static String str(CharSequence s) { return s == null ? "" : s.toString(); }

    static void notifyDetected(Context c, PayParser.Result r, String app) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Dépenses détectées", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Une dépense repérée dans une notification de paiement, à ajouter dans Expy.");
            c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        int count = PayStore.list(c).length();
        String amount = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(r.cents / 100.0);
        String title = "Dépense détectée : " + amount + (r.merchant != null ? " · " + r.merchant : "");
        String body = count > 1 ? count + " dépenses à confirmer dans Expy." : "Touche pour l'ajouter dans Expy (" + app + ").";
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_coin)
            .setColor(0xFF2B47D9)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setNumber(count)
            .setCategory(NotificationCompat.CATEGORY_REMINDER);
        try { NotificationManagerCompat.from(c).notify(NOTIF_ID, b.build()); } catch (SecurityException ignored) { }
    }
}
