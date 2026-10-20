package dev.powerfulcalendar.ui.v2

import android.app.ActivityManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Observe the real setting; toggling Remove animations must not require an app restart. */
@Composable
fun rememberSystemGlassEffects(): GlassEffects {
    val context = LocalContext.current
    fun readScale() = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    var scale by remember { mutableFloatStateOf(readScale()) }
    val lowRam = remember { context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scale = readScale() }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return GlassEffects(blur = !lowRam && scale > 0f, animationScale = scale, reduceMotion = scale <= 0f)
}
