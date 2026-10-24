package dev.powerfulcalendar.core

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** Verified logical backup envelope. Android scheduler handles and AI authority are never payload. */
data class VerifiedBackup(
    val payload: JSONObject,
    val backupFormatVersion: Int,
    val databaseSchemaVersion: Int,
    val appVersionCode: Long,
    val exportedAtEpochMs: Long,
    val storeLineageId: String,
    val legacyUnverified: Boolean,
    val warnings: List<String>
)

/** The caller must get an explicit user confirmation before retrying a legacy un-hashed backup. */
class LegacyBackupConsentRequired : Exception(
    "这是早期 flat backup v2 文件，不包含 SHA-256。确认来源可信后，才可显式继续恢复"
)

/**
 * Stable BackupEnvelope v1 codec. The hash is over canonical UTF-8 payload bytes (not the envelope
 * wrapper), so object key order and pretty-printing cannot change the digest.
 */
object BackupEnvelope {
    const val FORMAT = "powercal-native"
    const val FORMAT_VERSION = 1
    const val MAX_BYTES = 64 * 1024 * 1024

    /**
     * Ordered logical tables carried by a backup. Execution history is deliberately excluded: it
     * records what fired on the OLD device and must not read as "already scheduled" after restore.
     */
    val TABLES = listOf("identities", "objects", "drafts", "commits", "sourceLinks", "migrations")
    /** Optional on pre-schema-v5 envelopes; required by PowerStore when schemaVersion is 5+. */
    const val AI_TASK_RECEIPTS = "aiTaskReceipts"
    val OPTIONAL_TABLES = listOf(AI_TASK_RECEIPTS)

    /**
     * Reads a backup document with a hard byte bound before the caller constructs a JSONObject.
     * The reader consumes at most one byte beyond the limit, so a hostile provider cannot make us
     * buffer the rest of an oversized file just to discover that it must be rejected.
     */
    fun readUtf8Bounded(input: InputStream, maxBytes: Int = MAX_BYTES): String {
        require(maxBytes in 0 until Int.MAX_VALUE) { "备份读取上限无效" }
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val remaining = maxBytes - total
            val count = input.read(buffer, 0, minOf(buffer.size, remaining + 1))
            if (count < 0) break
            if (count == 0) {
                val byte = input.read()
                if (byte < 0) break
                if (remaining == 0) throw oversizedInput(maxBytes)
                output.write(byte)
                total++
                continue
            }
            if (count > remaining) throw oversizedInput(maxBytes)
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray().toString(StandardCharsets.UTF_8)
    }

    private fun oversizedInput(maxBytes: Int): IllegalArgumentException =
        IllegalArgumentException(
            if (maxBytes == MAX_BYTES) "备份文件超过 64MB 上限，已拒绝读取"
            else "备份文件超过 $maxBytes 字节上限，已拒绝读取"
        )

    fun create(
        payload: JSONObject,
        databaseSchemaVersion: Int,
        appVersionCode: Long,
        exportedAtEpochMs: Long,
        storeLineageId: String
    ): JSONObject {
        require(databaseSchemaVersion > 0) { "databaseSchemaVersion 必须为正数" }
        require(appVersionCode >= 0) { "appVersionCode 不可为负数" }
        require(exportedAtEpochMs > 0) { "exportedAtEpochMs 必须为正数" }
        requireValidLineage(storeLineageId, "storeLineageId 必须是 UUID")
        // Detach from the caller's mutable payload: the stored hash must describe the stored bytes.
        val detached = JSONObject(payload.toString())
        val payloadBytes = canonicalPayloadBytes(detached)
        require(payloadBytes.size <= MAX_BYTES) { "备份 payload 超过大小上限" }
        return JSONObject().apply {
            put("format", FORMAT)
            put("backupFormatVersion", FORMAT_VERSION)
            put("databaseSchemaVersion", databaseSchemaVersion)
            put("appVersionCode", appVersionCode)
            put("exportedAtEpochMs", exportedAtEpochMs)
            put("storeLineageId", storeLineageId)
            put("payloadSha256", sha256Hex(payloadBytes))
            put("payload", detached)
        }
    }

    fun verify(
        envelope: JSONObject,
        currentDatabaseSchemaVersion: Int,
        allowLegacyV2: Boolean = false
    ): VerifiedBackup {
        require(envelope.optString("format") == FORMAT) { "不是 PowerCal 原生备份" }
        if (!envelope.has("backupFormatVersion")) {
            return verifyLegacyV2(envelope, allowLegacyV2)
        }

        val backupVersion = envelope.optInt("backupFormatVersion", -1)
        require(backupVersion == FORMAT_VERSION) {
            "不支持原生备份格式 v$backupVersion；当前支持 v$FORMAT_VERSION"
        }
        val schemaVersion = envelope.optInt("databaseSchemaVersion", -1)
        require(schemaVersion in 1..currentDatabaseSchemaVersion) {
            "备份数据库 schema v$schemaVersion 高于当前支持的 v$currentDatabaseSchemaVersion，已拒绝恢复"
        }
        val appVersionCode = envelope.optLong("appVersionCode", -1L)
        require(appVersionCode >= 0) { "备份缺少有效 appVersionCode" }
        val exportedAt = envelope.optLong("exportedAtEpochMs", -1L)
        require(exportedAt > 0L) { "备份缺少有效 exportedAtEpochMs" }
        val lineage = envelope.optString("storeLineageId")
        requireValidLineage(lineage, "备份 lineageId 无效")
        val inputPayload = envelope.optJSONObject("payload")
            ?: throw IllegalArgumentException("备份缺少 payload 对象")
        // Detach from the caller's mutable JSONObject before verifying/using it.
        val payload = JSONObject(inputPayload.toString())
        val expected = envelope.optString("payloadSha256").lowercase(Locale.ROOT)
        require(HEX_SHA256.matches(expected)) { "payloadSha256 格式无效" }
        val actualBytes = canonicalPayloadBytes(payload)
        require(actualBytes.size <= MAX_BYTES) { "备份 payload 超过大小上限" }
        val actual = sha256Hex(actualBytes)
        require(MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.US_ASCII),
            actual.toByteArray(StandardCharsets.US_ASCII)
        )) { "备份 SHA-256 校验失败；文件可能损坏或被修改" }

        return VerifiedBackup(
            payload = payload,
            backupFormatVersion = backupVersion,
            databaseSchemaVersion = schemaVersion,
            appVersionCode = appVersionCode,
            exportedAtEpochMs = exportedAt,
            storeLineageId = lineage,
            legacyUnverified = false,
            warnings = emptyList()
        )
    }

    /**
     * Read-back verification for a just-written file: re-verifies the hash and confirms that every
     * logical table and both high-water marks survived the provider's write path unchanged.
     */
    fun verifyReadback(original: JSONObject, reread: JSONObject, currentDatabaseSchemaVersion: Int): VerifiedBackup {
        val first = verify(original, currentDatabaseSchemaVersion)
        val second = verify(reread, currentDatabaseSchemaVersion)
        require(first.storeLineageId == second.storeLineageId) { "回读的 lineageId 与导出时不一致" }
        require(first.databaseSchemaVersion == second.databaseSchemaVersion) { "回读的 schema 版本不一致" }
        require(first.exportedAtEpochMs == second.exportedAtEpochMs) { "回读的导出时间不一致" }
        (TABLES + OPTIONAL_TABLES).forEach { table ->
            val before = first.payload.optJSONArray(table)
            val after = second.payload.optJSONArray(table)
            require((before == null) == (after == null)) { "回读的 $table 是否存在不一致" }
            if (before != null && after != null) {
                require(canonicalJson(before) == canonicalJson(after)) {
                    "回读的 $table 内容不一致"
                }
            }
        }
        require(first.payload.getLong("nextSeq") == second.payload.getLong("nextSeq")) { "回读的 nextSeq 不一致" }
        require(first.payload.getLong("nextOrder") == second.payload.getLong("nextOrder")) { "回读的 nextOrder 不一致" }
        return second
    }

    private fun verifyLegacyV2(envelope: JSONObject, allowLegacyV2: Boolean): VerifiedBackup {
        val version = envelope.optInt("version", -1)
        if (version == 1) {
            throw IllegalArgumentException(
                "0.1.0 的 flat backup v1 尚未提供受控迁移，已拒绝读取；请保留原文件，不要当作 v2 处理"
            )
        }
        require(version == 2) { "不支持的旧式备份版本：${envelope.opt("version")}" }
        // Refuse ambiguous documents: an Envelope stripped of its version must not silently become
        // an unverified legacy import.
        require(!envelope.has("payload") && !envelope.has("payloadSha256")) {
            "文件同时包含 Envelope 字段与旧式字段，格式模糊，已拒绝"
        }
        val allowed = setOf(
            "format", "version", "exportedMs", "nextSeq", "nextOrder",
            "identities", "objects", "drafts", "commits", "sourceLinks", "migrations", "note"
        )
        require(envelope.keys().asSequence().all { it in allowed }) {
            "旧式备份包含未知字段，已拒绝以免静默丢弃数据"
        }
        if (!allowLegacyV2) throw LegacyBackupConsentRequired()

        val drafts = envelope.optJSONArray("drafts") ?: JSONArray()
        var nextOrder = 1L
        for (index in 0 until drafts.length()) {
            val order = drafts.optJSONObject(index)?.optLong("queueOrder", 0L) ?: 0L
            nextOrder = maxOf(nextOrder, order + 1)
        }
        val payload = JSONObject().apply {
            TABLES.forEach { table ->
                val array = envelope.optJSONArray(table) ?: return@forEach
                // Detach: callers must not be able to mutate arrays after verification.
                put(table, JSONArray(array.toString()))
            }
            put("nextSeq", envelope.optLong("nextSeq", 1L))
            // Older flat files only carried nextSeq; honour nextOrder when a file does have it.
            put("nextOrder", maxOf(nextOrder, envelope.optLong("nextOrder", 1L)))
        }
        return VerifiedBackup(
            payload = payload,
            backupFormatVersion = 0,
            databaseSchemaVersion = 2,
            appVersionCode = 0L,
            exportedAtEpochMs = envelope.optLong("exportedMs", 0L),
            // Flat v2 had no lineage. A new UUID marks this as a new lineage with no source identity.
            storeLineageId = UUID.randomUUID().toString(),
            legacyUnverified = true,
            warnings = listOf(
                "早期 flat backup v2 没有 SHA-256 或 lineageId；恢复后视为新 lineage，请保留原文件。"
            )
        )
    }

    private fun requireValidLineage(value: String, message: String) {
        require(runCatching { UUID.fromString(value) }.isSuccess) { message }
    }

    fun canonicalPayloadBytes(payload: JSONObject): ByteArray =
        canonicalJson(payload).toByteArray(StandardCharsets.UTF_8)

    /** Recursively sorts object keys; array order is meaningful and therefore preserved. */
    fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> {
            val keys = value.keys().asSequence().toList().sorted()
            keys.joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
                JSONObject.quote(key) + ":" + canonicalJson(value.get(key))
            }
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]", separator = ",") {
            canonicalJson(value.get(it))
        }
        is String -> JSONObject.quote(value)
        is Boolean -> value.toString()
        is Number -> {
            val number = BigDecimal(value.toString())
            number.stripTrailingZeros().toPlainString()
        }
        else -> throw IllegalArgumentException("payload contains unsupported JSON value ${value.javaClass.name}")
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }

    private val HEX_SHA256 = Regex("^[0-9a-f]{64}$")
}
