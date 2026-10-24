package dev.powerfulcalendar.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Writes only a unique marker into the instrumentation package's cache directory. */
public final class D05ProbeReceiver extends BroadcastReceiver {
    public static final String ACTION = "dev.powerfulcalendar.test.action.D05_PROBE";
    private static final Pattern TOKEN = Pattern.compile("[a-f0-9]{32}");

    @Override
    public void onReceive(Context context, Intent intent) {
        String token = intent.getStringExtra(D05ProbeActivity.EXTRA_TOKEN);
        if (token == null || !TOKEN.matcher(token).matches()) return;
        File marker = new File(context.getCacheDir(), markerName(token));
        try (FileOutputStream output = new FileOutputStream(marker)) {
            output.write(("received=" + System.currentTimeMillis() + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not record isolated D05 delivery", failure);
        }
    }

    public static String markerName(String token) {
        return "pc-d05-" + token + ".received";
    }
}
