package io.github.chrisoo.expy;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * One backup file the user picks once (Android's file picker, so it can be in Google Drive).
 * Expy keeps the permission to that file and rewrites it; no Google account or key is involved.
 */
@CapacitorPlugin(name = "DriveBackup")
public class DriveBackupPlugin extends Plugin {
    private static final String FILE = "expy_backup", KEY_URI = "uri";

    private SharedPreferences prefs() { return getContext().getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    private String name(Uri u) {
        try (Cursor c = getContext().getContentResolver().query(u, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return null;
    }

    private JSObject state() {
        JSObject o = new JSObject();
        String s = prefs().getString(KEY_URI, null);
        o.put("linked", s != null);
        if (s != null) { String n = name(Uri.parse(s)); if (n != null) o.put("name", n); }
        return o;
    }

    @PluginMethod
    public void status(PluginCall call) { call.resolve(state()); }

    /** Let the user create a new backup file, or (existing = true) choose one already there, which is then rewritten. */
    @PluginMethod
    public void pick(PluginCall call) {
        boolean existing = Boolean.TRUE.equals(call.getBoolean("existing", false));
        Intent i = new Intent(existing ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(existing ? "*/*" : "application/json");
        if (!existing) i.putExtra(Intent.EXTRA_TITLE, call.getString("title", "expy-sauvegarde.json"));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, i, "pickResult");
    }

    @ActivityCallback
    private void pickResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.reject("cancelled", "cancelled");
            return;
        }
        Uri u = result.getData().getData();
        try {
            getContext().getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            call.reject("permission", "permission");
            return;
        }
        releaseOld(u);
        prefs().edit().putString(KEY_URI, u.toString()).apply();
        call.resolve(state());
    }

    private void releaseOld(Uri keep) {
        String s = prefs().getString(KEY_URI, null);
        if (s == null || s.equals(keep.toString())) return;
        try { getContext().getContentResolver().releasePersistableUriPermission(Uri.parse(s), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) { }
    }

    /** Rewrite the chosen file with {data} (UTF-8 text). Rejects with "unlinked" or "write" so the page can say what happened. */
    @PluginMethod
    public void write(PluginCall call) {
        String s = prefs().getString(KEY_URI, null), data = call.getString("data");
        if (s == null) { call.reject("unlinked", "unlinked"); return; }
        if (data == null) { call.reject("nodata", "nodata"); return; }
        final Uri u = Uri.parse(s);
        getBridge().execute(() -> {
            try {
                ContentResolver cr = getContext().getContentResolver();
                try (OutputStream out = cr.openOutputStream(u, "wt")) {
                    if (out == null) throw new java.io.IOException("no stream");
                    out.write(data.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                JSObject o = state(); o.put("bytes", data.length());
                call.resolve(o);
            } catch (SecurityException e) {
                prefs().edit().remove(KEY_URI).apply();
                call.reject("unlinked", "unlinked");
            } catch (Exception e) {
                call.reject("write", "write");
            }
        });
    }

    @PluginMethod
    public void unlink(PluginCall call) {
        String s = prefs().getString(KEY_URI, null);
        if (s != null) {
            try { getContext().getContentResolver().releasePersistableUriPermission(Uri.parse(s), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) { }
        }
        prefs().edit().remove(KEY_URI).apply();
        call.resolve(state());
    }
}
