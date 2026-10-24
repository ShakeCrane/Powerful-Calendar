package dev.powerfulcalendar.reminders;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import java.io.File;
import java.util.regex.Pattern;

/** Visible task used only to classify Android's remove-from-recents behavior on an AVD. */
public final class D05ProbeActivity extends Activity {
    public static final String EXTRA_TOKEN = "d05_probe_token";
    public static final String EXTRA_ARM_ALARM = "d05_arm_alarm";
    public static final String EXTRA_REARM_ALARM = "d05_rearm_alarm";
    public static final String EXTRA_CANCEL_ALARM = "d05_cancel_alarm";
    public static final String EXTRA_DELAY_MS = "d05_delay_ms";
    private static final Pattern TOKEN = Pattern.compile("[a-f0-9]{32}");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String token = getIntent().getStringExtra(EXTRA_TOKEN);
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new IllegalArgumentException("Invalid D05 probe token");
        }
        setTitle("D05 PROBE " + token);
        if (getIntent().getBooleanExtra(EXTRA_CANCEL_ALARM, false)) {
            PendingIntent pendingIntent = createPendingIntent(token);
            getSystemService(AlarmManager.class).cancel(pendingIntent);
            pendingIntent.cancel();
            deleteMarkerFiles(token);
        } else if (getIntent().getBooleanExtra(EXTRA_REARM_ALARM, false)) {
            long triggerAt = readTriggerAt(token);
            PendingIntent pendingIntent = createPendingIntent(token);
            getSystemService(AlarmManager.class).setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                    new File(getCacheDir(), "pc-d05-" + token + ".rearmed"))) {
                output.write(("triggerAt=" + triggerAt + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Could not record D05 rearm probe", failure);
            }
        } else if (getIntent().getBooleanExtra(EXTRA_ARM_ALARM, true)) {
            PendingIntent pendingIntent = createPendingIntent(token);
            long triggerAt = System.currentTimeMillis() + getIntent().getLongExtra(EXTRA_DELAY_MS, 60_000L);
            getSystemService(AlarmManager.class).setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                    new File(getCacheDir(), "pc-d05-" + token + ".armed"))) {
                output.write(("triggerAt=" + triggerAt + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Could not record isolated D05 probe", failure);
            }
        }
        TextView content = new TextView(this);
        content.setText("Isolated background alarm probe\n" + token);
        content.setTextSize(20f);
        content.setPadding(32, 32, 32, 32);
        setContentView(content);
    }

    private PendingIntent createPendingIntent(String token) {
        Intent alarmIntent = new Intent(this, D05ProbeReceiver.class)
                .setAction(D05ProbeReceiver.ACTION)
                .setData(Uri.parse("powercal-d05://" + token))
                .putExtra(EXTRA_TOKEN, token);
        return PendingIntent.getBroadcast(
                this,
                token.hashCode(),
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void deleteMarkerFiles(String token) {
        new File(getCacheDir(), "pc-d05-" + token + ".armed").delete();
        new File(getCacheDir(), "pc-d05-" + token + ".rearmed").delete();
        new File(getCacheDir(), D05ProbeReceiver.markerName(token)).delete();
    }

    private long readTriggerAt(String token) {
        File armed = new File(getCacheDir(), "pc-d05-" + token + ".armed");
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(armed))) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("triggerAt=")) throw new IllegalStateException("Missing trigger time");
            return Long.parseLong(line.substring("triggerAt=".length()));
        } catch (java.io.IOException | NumberFormatException failure) {
            throw new IllegalStateException("Could not read D05 trigger time", failure);
        }
    }
}
