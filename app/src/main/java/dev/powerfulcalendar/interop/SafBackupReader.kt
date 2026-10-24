package dev.powerfulcalendar.interop

import android.content.ContentResolver
import android.net.Uri
import dev.powerfulcalendar.core.BackupEnvelope

/** Reads SAF backup text through the shared bounded reader; permission failures propagate intact. */
internal object SafBackupReader {
    fun readText(resolver: ContentResolver, uri: Uri): String =
        resolver.openInputStream(uri)?.use { BackupEnvelope.readUtf8Bounded(it) }
            ?: error("无法读取备份文件")
}
