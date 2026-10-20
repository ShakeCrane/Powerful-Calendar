package dev.powerfulcalendar.core

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
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
class LegacyBackupConsentRequired : IllegalArgumentException(
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
        require(runCatching { UUID.fromString(storeLineageId) }.isSuccess) { "storeLineageId 必须是 UUID" }
        val payloadBytes = canonicalPayloadBytes(payload)
        require(payloadBytes.size <= MAX_BYTES) { "备份 payload 超过大小上限" }
        return JSONObject().apply {
            put("format", FORMAT)
            put("backupFormatVersion", FORMAT_VERSION)
            put("databaseSchemaVersion", databaseSchemaVersion)
            put("appVersionCode", appVersionCode)
            put("exportedAtEpochMs", exportedAtEpochMs)
            put("storeLineageId", storeLineageId)
            put("payloadSha256", sha256Hex(payloadBytes))
            put("payload", payload)
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
        require(runCatching { UUID.fromString(lineage) }.isSuccess) { "备份 lineageId 无效" }
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

    private fun verifyLegacyV2(envelope: JSONObject, allowLegacyV2: Boolean): VerifiedBackup {
        require(envelope.optInt("version", -1) == 2) {
            "不支持的旧式备份版本：${envelope.opt("version")}"
        }
        if (!allowLegacyV2) throw LegacyBackupConsentRequired()

        val drafts = envelope.optJSONArray("drafts") ?: JSONArray()
        var nextOrder = 1L
        for (index in 0 until drafts.length()) {
            val order = drafts.optJSONObject(index)?.optLong("queueOrder", 0L) ?: 0L
            nextOrder = maxOf(nextOrder, order + 1)
        }
        val payload = JSONObject().apply {
            copyArrayIfPresent(envelope, this, "identities")
            copyArrayIfPresent(envelope, this, "objects")
            copyArrayIfPresent(envelope, this, "drafts")
            copyArrayIfPresent(envelope, this, "commits")
            copyArrayIfPresent(envelope, this, "sourceLinks")
            copyArrayIfPresent(envelope, this, "migrations")
            put("executions", JSONArray())
            put("nextSeq", envelope.optLong("nextSeq", 1L))
            put("nextOrder", nextOrder)
        }
        return VerifiedBackup(
            payload = payload,
            backupFormatVersion = 0,
            databaseSchemaVersion = 2,
            appVersionCode = 0L,
            exportedAtEpochMs = envelope.optLong("exportedMs", 0L),
            // Flat v2 had no lineage. A new UUID explicitly marks this as a restored fork.
            storeLineageId = UUID.randomUUID().toString(),
            legacyUnverified = true,
            warnings = listOf(
                "早期 flat backup v2 没有 SHA-256 或 lineageId；恢复后视为新 lineage，请保留原文件。"
            )
        )
    }

    private fun copyArrayIfPresent(source: JSONObject, target: JSONObject, key: String) {
        val array = source.optJSONArray(key)
        if (array != null) target.put(key, array)
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
