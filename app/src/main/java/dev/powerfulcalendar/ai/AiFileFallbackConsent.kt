package dev.powerfulcalendar.ai

import android.content.Context
import java.security.MessageDigest

/**
 * Distinct from calendar-context sharing. Grants bounded extracted-text analysis only.
 * Provider/model changes invalidate the permission; users can revoke at any time.
 * Image pixels, raw binary files, calendar history and publishing are never covered.
 */
object AiFileFallbackConsent {
    private const val PREFS = "powercal-file-ai-consent"
    private const val KEY = "provider-scope"

    private fun scope(settings: AiSettings): String {
        val source = AiSettings.normalizeEndpoint(settings.endpoint) + "\n" + settings.model.trim()
        return MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun allowed(context: Context, settings: AiSettings): Boolean =
        settings.configured && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) == scope(settings)

    fun grant(context: Context, settings: AiSettings) {
        require(settings.configured) { "AI 服务未启用" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, scope(settings)).apply()
    }

    fun revoke(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
