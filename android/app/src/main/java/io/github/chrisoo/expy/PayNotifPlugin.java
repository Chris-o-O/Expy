package io.github.chrisoo.expy;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONException;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/** Bridge between Expy's web code and the payment-notification listener. */
@CapacitorPlugin(name = "PayNotif", permissions = { @Permission(strings = { Manifest.permission.POST_NOTIFICATIONS }, alias = "notify") })
public class PayNotifPlugin extends Plugin {
    private static WeakReference<PayNotifPlugin> live = new WeakReference<>(null);

    @Override
    public void load() { live = new WeakReference<>(this); }

    /** Called by the listener: lets the open app refresh its list right away. */
    static void emit(Context c) {
        PayNotifPlugin p = live.get();
        if (p != null) p.notifyListeners("detected", new JSObject());
    }

    private boolean hasAccess() {
        return NotificationManagerCompat.getEnabledListenerPackages(getContext()).contains(getContext().getPackageName());
    }

    private boolean canNotify() {
        return Build.VERSION.SDK_INT < 33 || getPermissionState("notify") == PermissionState.GRANTED;
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject o = new JSObject();
        o.put("access", hasAccess());
        o.put("notify", canNotify());
        o.put("on", PayStore.isOn(getContext()));
        call.resolve(o);
    }

    @PluginMethod
    public void requestNotify(PluginCall call) {
        if (canNotify()) { status(call); return; }
        requestPermissionForAlias("notify", call, "notifyDone");
    }

    @PermissionCallback
    private void notifyDone(PluginCall call) { status(call); }

    @PluginMethod
    public void openAccessSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    /** App info screen: where Android 13+ hides "Allow restricted settings" for apps installed outside the Play Store. */
    @PluginMethod
    public void openAppSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void setOn(PluginCall call) {
        PayStore.setOn(getContext(), Boolean.TRUE.equals(call.getBoolean("on", true)));
        status(call);
    }

    @PluginMethod
    public void pending(PluginCall call) {
        JSObject o = new JSObject();
        o.put("items", PayStore.list(getContext()));
        call.resolve(o);
    }

    @PluginMethod
    public void dismiss(PluginCall call) {
        Set<String> ids = new HashSet<>();
        JSArray arr = call.getArray("ids");
        try { if (arr != null) for (Object id : arr.toList()) ids.add(String.valueOf(id)); } catch (JSONException ignored) { }
        PayStore.remove(getContext(), ids);
        if (PayStore.list(getContext()).length() == 0) NotificationManagerCompat.from(getContext()).cancel(PayListener.NOTIF_ID);
        call.resolve();
    }

    /** Test button in Expy: runs a sample notification through the same path as a real one. */
    @PluginMethod
    public void simulate(PluginCall call) {
        boolean kept = PayListener.handle(getContext(), call.getString("pkg", PayParser.WALLET), call.getString("app", "Google Wallet"),
            call.getString("title", "Café Olimpico"), call.getString("text", "4,75 $ avec Visa ••1234"), System.currentTimeMillis());
        JSObject o = new JSObject();
        o.put("kept", kept);
        call.resolve(o);
    }
}
