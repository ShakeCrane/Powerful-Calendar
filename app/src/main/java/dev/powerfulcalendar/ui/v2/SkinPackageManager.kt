package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class InstalledSkin(val id: String, val name: String, val builtIn: Boolean = false)

/**
 * Data-only PowerCal skin ZIP format v1. No code, scripts, native libraries or fonts are loaded.
 * Image pixels, uncompressed ZIP bytes and theme overrides are independently bounded.
 */
class SkinPackageManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
    private val folder = File(context.filesDir, "skin-packs")
    private val original = InstalledSkin("powercal-default", "PowerCal 原生玻璃", true)

    fun currentId(): String = prefs.getString("selected", original.id) ?: original.id

    fun installed(): List<InstalledSkin> = buildList {
        add(original)
        folder.listFiles()?.filter { it.isFile && it.name.endsWith(".pcskin") }
            ?.sortedBy { it.name }?.forEach { file ->
                runCatching { decode(readBounded(file.inputStream()), false) }
                    .getOrNull()?.let { add(InstalledSkin(it.pack.key, it.name)) }
            }
    }

    fun restoreSelection() {
        val id = currentId()
        if (id == original.id) {
            PowerCalSkinRegistry.reset()
        } else {
            runCatching { readInstalled(id) }.onSuccess {
                PowerCalSkinRegistry.activate(it.pack)
            }.onFailure {
                prefs.edit().putString("selected", original.id).apply()
                PowerCalSkinRegistry.reset()
            }
        }
    }

    fun select(id: String) {
        if (id == original.id) {
            prefs.edit().putString("selected", id).apply()
            PowerCalSkinRegistry.reset()
            return
        }
        val verified = readInstalled(id)
        PowerCalSkinRegistry.activate(verified.pack)
        prefs.edit().putString("selected", id).apply()
    }

    fun install(uri: Uri): InstalledSkin {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readBounded(it) }
            ?: error("无法读取皮肤包")
        val result = decode(bytes, true)
        val id = result.pack.key
        require(id != original.id) { "不能覆盖内置原生皮肤" }
        require(folder.isDirectory || folder.mkdirs()) { "无法创建皮肤目录" }
        require((folder.listFiles()?.count { it.extension == "pcskin" } ?: 0) < 12) {
            "皮肤超过 12 个，请先删除不需要的皮肤"
        }
        val target = File(folder, id + ".pcskin")
        require(!target.exists()) { "皮肤 ID 已存在，请先删除原版本" }
        val temp = File(folder, id + ".part")
        try {
            temp.outputStream().use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            require(temp.renameTo(target)) { "存储皮肤失败" }
        } finally {
            if (temp.exists()) temp.delete()
        }
        return InstalledSkin(id, result.name)
    }

    fun delete(id: String) {
        require(id != original.id && ID.matches(id)) { "不能删除内置皮肤" }
        val target = File(folder, id + ".pcskin")
        require(target.isFile) { "皮肤包不存在" }
        if (currentId() == id) select(original.id)
        require(target.delete()) { "删除失败" }
    }

    /** Exports a complete importable template of the original glass theme. */
    fun exportOriginal(uri: Uri) {
        val target = context.contentResolver.openOutputStream(uri) ?: error("无法写入目标文件")
        target.use { stream ->
            ZipOutputStream(stream).use { zip ->
                val json = JSONObject()
                    .put("format", FORMAT).put("schemaVersion", 1)
                    .put("id", "powercal-original-template")
                    .put("name", "PowerCal 原生玻璃 · 模板")
                    .put("light", JSONObject()).put("dark", JSONObject())
                    .put("layout", JSONObject().put("cardRadiusDp", 28)
                        .put("barRadiusDp", 20).put("pointLineDp", 2)
                        .put("fontScale", 1.0))
                    .put("imagesLight", JSONObject()).put("imagesDark", JSONObject())
                    .put("sha256", JSONObject())
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(json.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    internal data class Verified(val name: String, val pack: PowerCalSkinPack)

    private fun readInstalled(id: String): Verified {
        require(ID.matches(id) && id != original.id) { "皮肤 ID 无效" }
        val file = File(folder, id + ".pcskin")
        require(file.isFile) { "未找到皮肤文件" }
        return decode(readBounded(file.inputStream()), true).also {
            require(it.pack.key == id) { "皮肤文件名与包内 ID 不匹配" }
        }
    }

    internal fun decode(bytes: ByteArray, loadImages: Boolean): Verified {
        require(bytes.size <= MAX_ARCHIVE) { "皮肤 ZIP 超过 10MB" }
        val entries = linkedMapOf<String, ByteArray>()
        var inflated = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                require(entries.size < 24) { "压缩包条目过多" }
                require(!e.isDirectory && (e.name == "manifest.json" || IMAGE.matches(e.name))) {
                    "皮肤包包含非法文件名：" + e.name.take(70)
                }
                require(!entries.containsKey(e.name)) { "压缩包路径重复" }
                val dest = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = zip.read(buf)
                    if (n < 0) break
                    inflated += n
                    require(inflated <= 12_000_000 && dest.size() + n <=
                        if (e.name == "manifest.json") 80_000 else 3_000_000) {
                        "压缩数据超过上限"
                    }
                    dest.write(buf, 0, n)
                }
                entries[e.name] = dest.toByteArray()
                zip.closeEntry()
            }
        }
        val json = entries["manifest.json"]?.let { JSONObject(String(it, Charsets.UTF_8)) }
            ?: error("缺少 manifest.json")
        require(json.optString("format") == FORMAT && json.optInt("schemaVersion") == 1) {
            "皮肤包格式/版本不兼容"
        }
        val id = json.optString("id")
        val name = json.optString("name").trim()
        require(ID.matches(id) && name.length in 1..60) { "皮肤 ID 或名称不合法" }
        val layout = json.optJSONObject("layout") ?: JSONObject()
        val tokens = SkinVisualTokens(
            light = readPalette(json.optJSONObject("light")),
            dark = readPalette(json.optJSONObject("dark")),
            cardRadiusDp = layout.optDouble("cardRadiusDp", 28.0).toFloat(),
            barRadiusDp = layout.optDouble("barRadiusDp", 20.0).toFloat(),
            pointLineDp = layout.optDouble("pointLineDp", 2.0).toFloat(),
            fontScale = layout.optDouble("fontScale", 1.0).toFloat()
        )
        val light = readImageList(json.optJSONObject("imagesLight"))
        val dark = readImageList(json.optJSONObject("imagesDark"))
        val paths = (light.values + dark.values).toSet()
        require(entries.keys == paths + "manifest.json") { "皮肤包存在未声明资源或缺失的素材文件" }
        val hashes = json.optJSONObject("sha256") ?: JSONObject()
        require(hashes.length() == paths.size) { "SHA-256 资源清单不完整" }
        for (path in paths) {
            val hash = hashes.optString(path)
            require(hash.matches(Regex("[a-fA-F0-9]{64}")) &&
                digest(entries.getValue(path)).equals(hash, ignoreCase = true)) {
                "素材校验不通过：" + path
            }
        }
        val decoded = mutableMapOf<String, SkinBitmapArt>()
        fun buildImages(list: Map<SkinSlot, String>): Map<SkinSlot, SkinBitmapArt> =
            if (!loadImages) emptyMap()
            else list.mapValues { (_, path) ->
                decoded.getOrPut(path) { decodeImage(entries.getValue(path)) }
            }
        return Verified(name, PowerCalSkinPack(
            key = id, visual = tokens,
            lightBitmaps = buildImages(light), darkBitmaps = buildImages(dark)
        ))
    }

    private fun readPalette(json: JSONObject?): Map<String, Color> {
        if (json == null) return emptyMap()
        val colors = mutableMapOf<String, Color>()
        val names = json.keys()
        while (names.hasNext()) {
            val name = names.next()
            require(name in SkinVisualTokens.colorNames) { "未知颜色参数：" + name }
            val hex = json.getString(name)
            require(hex.matches(Regex("#(?:[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})"))) {
                "颜色必须采用 #RRGGBB 或 #AARRGGBB"
            }
            colors[name] = Color(android.graphics.Color.parseColor(hex))
        }
        for ((a, b) in listOf("primary" to "onPrimary", "background" to "onBackground",
            "surface" to "onSurface")) {
            require((a in colors) == (b in colors)) { a + " 与 " + b + " 必须成对指定" }
        }
        return colors
    }

    private fun readImageList(json: JSONObject?): Map<SkinSlot, String> {
        if (json == null) return emptyMap()
        require(json.length() <= 12) { "一个主题最多引用 12 个槽位" }
        val results = mutableMapOf<SkinSlot, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val slot = runCatching { SkinSlot.valueOf(name) }.getOrNull()
                ?: error("未知素材槽位：" + name)
            val path = json.getString(name)
            require(IMAGE.matches(path)) { "素材路径不合法：" + path.take(70) }
            results[slot] = path
        }
        return results
    }

    private fun decodeImage(bytes: ByteArray): SkinBitmapArt {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth in 1..4096 && options.outHeight in 1..4096 &&
            options.outWidth.toLong() * options.outHeight <= 8_000_000L) {
            "素材不是 PNG/WebP 或图片尺寸超过限制"
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("图片解码失败")
        return SkinBitmapArt(bitmap.asImageBitmap())
    }

    private fun readBounded(input: InputStream): ByteArray = input.use { source ->
        val dest = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = source.read(buf)
            if (n < 0) break
            require(dest.size() + n <= MAX_ARCHIVE) { "皮肤包超过 10MB" }
            dest.write(buf, 0, n)
        }
        dest.toByteArray()
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val FORMAT = "powercal-skin"
        private const val MAX_ARCHIVE = 10_000_000
        private val ID = Regex("[a-z0-9][a-z0-9_-]{2,47}")
        private val IMAGE = Regex("images/[a-z0-9_-]+[.](png|webp)")
    }
}
