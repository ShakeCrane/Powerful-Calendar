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
import java.util.zip.ZipInputStream

data class InstalledSkin(val id: String, val name: String, val builtIn: Boolean = false)

/**
 * PowerCal skin ZIP v1/v2/v3: v1 resources, v2 bounded declarative UI,
 * v3 sandboxed HTML/CSS/JS rendered in an isolated WebView presentation surface.
 * Skin code cannot access PowerStore or host permissions directly; every operation
 * passes through the strictly limited host action bridge.
 * Compressed and inflated ZIP sizes, contents and resource hashes are bounded.
 */
class SkinPackageManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
    private val folder = File(context.filesDir, "skin-packs")
    private val original = InstalledSkin("powercal-default", "PowerCal 原生玻璃（安全回退）", true)
    private val clearCandidate = InstalledSkin("powercal-clear-default",
        "Clear · 迁移预览（部分业务仍待适配）", true)
    private val clearAssetName = "powercal-clear-default.pcskin"

    /** Same checked-in bytes that Skin Studio offers at startup. */
    private fun readBundledClear(loadImages: Boolean = true): Verified {
        val bytes = context.assets.open(clearAssetName).use { readBounded(it) }
        // The catalogue does metadata-only validation for every origin. Activation
        // always performs the exact same full resource decode for both origins.
        return decode(bytes, loadImages).also {
            require(it.pack.key == clearCandidate.id && it.pack.web?.fullCanvas == true) {
                "Bundled Clear skin identity/canvas mismatch"
            }
        }
    }

    fun exportBundledClear(uri: Uri) {
        val verified = readBundledClear(loadImages = false)
        require(verified.pack.web != null)
        context.contentResolver.openOutputStream(uri)?.use { destination ->
            context.assets.open(clearAssetName).use { input -> input.copyTo(destination) }
        } ?: error("无法写入目标文件")
    }

    /** Origin is only a source/retention choice, never a privilege or runtime branch. */
    private fun loadPackage(id: String): Verified =
        if (id == clearCandidate.id) readBundledClear() else readInstalled(id)

    fun currentId(): String = prefs.getString("selected", original.id) ?: original.id

    fun installed(): List<InstalledSkin> = buildList {
        add(original)
        if (runCatching { readBundledClear(loadImages = false) }.isSuccess) add(clearCandidate)
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
            return
        }
        runCatching { loadPackage(id) }.onSuccess {
            PowerCalSkinRegistry.activate(it.pack)
        }.onFailure {
            prefs.edit().putString("selected", original.id).apply()
            PowerCalSkinRegistry.reset()
        }
    }

    fun select(id: String) {
        if (id == original.id) {
            prefs.edit().putString("selected", id).apply()
            PowerCalSkinRegistry.reset()
            return
        }
        // A factory-installed skin and a user-imported skin have precisely one
        // activation, WebView and Host Bridge path; neither gets privileged actions.
        val verified = loadPackage(id)
        PowerCalSkinRegistry.activate(verified.pack)
        prefs.edit().putString("selected", id).apply()
    }

    fun install(uri: Uri): InstalledSkin {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readBounded(it) }
            ?: error("无法读取皮肤包")
        val result = decode(bytes, true)
        val id = result.pack.key
        require(id != original.id && id != clearCandidate.id) {
            "不能覆盖受保护的内置皮肤"
        }
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
            val readBack = readBounded(temp.inputStream())
            require(digest(readBack) == digest(bytes)) { "Skin install readback hash mismatch" }
            decode(readBack, false)
            require(temp.renameTo(target)) { "存储皮肤失败" }
        } finally {
            if (temp.exists()) temp.delete()
        }
        return InstalledSkin(id, result.name)
    }

    fun delete(id: String) {
        require(id != original.id && id != clearCandidate.id && ID.matches(id)) {
            "不能删除内置皮肤"
        }
        val target = File(folder, id + ".pcskin")
        require(target.isFile) { "皮肤包不存在" }
        if (currentId() == id) select(original.id)
        require(target.delete()) { "删除失败" }
    }

    internal data class Verified(val name: String, val pack: PowerCalSkinPack)

    private fun readInstalled(id: String): Verified {
        require(ID.matches(id) && id != original.id && id != clearCandidate.id) {
            "皮肤 ID 无效或已保留"
        }
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
                require(entries.size < 97) { "压缩包条目过多" }
                require(!e.isDirectory && (e.name == "manifest.json" || e.name == UI_FILE ||
                    IMAGE.matches(e.name) || V3_RESOURCE.matches(e.name))) {
                    "皮肤包包含非法文件名：" + e.name.take(70)
                }
                require('\\' !in e.name && e.name.split('/').none { it == ".." }) {
                    "Skin resource path traversal is forbidden"
                }
                require(!entries.containsKey(e.name)) { "压缩包路径重复" }
                val dest = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = zip.read(buf)
                    if (n < 0) break
                    inflated += n
                    require(inflated <= 16_000_000 && dest.size() + n <= when {
                        e.name == "manifest.json" -> 80_000
                        e.name == UI_FILE -> 120_000
                        e.name.endsWith(".html") || e.name.endsWith(".css") ||
                            e.name.endsWith(".js") || e.name.endsWith(".json") -> 600_000
                        else -> 3_000_000
                    }) {
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
        val version = json.optInt("schemaVersion")
        require(json.optString("format") == FORMAT && version in 1..3) {
            "Unsupported skin package version"
        }
        val runtime = if (version == 2) {
            require(json.optString("runtimeUi") == UI_FILE) { "v2 must declare ui.json" }
            RuntimeSkinSchema.parse(entries[UI_FILE] ?: error("Missing ui.json"))
        } else if (version == 1) {
            require(!entries.containsKey(UI_FILE) && !json.has("runtimeUi")) {
                "v1 does not accept UI declarations"
            }
            null
        } else null
        val web = if (version == 3) parseWebBundle(json, entries) else null
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
        val webPaths = web?.files?.keys ?: emptySet()
        val declared = paths + webPaths + "manifest.json" + (if (version == 2) setOf(UI_FILE) else emptySet())
        require(entries.keys == declared) { "Undeclared or missing skin resources" }
        val secured = paths + webPaths + (if (version == 2) setOf(UI_FILE) else emptySet())
        val hashes = json.optJSONObject("sha256") ?: JSONObject()
        require(hashes.length() == secured.size) { "Incomplete SHA256 manifest" }
        for (path in secured) {
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
            key = id, visual = tokens, runtime = runtime, web = web,
            lightBitmaps = buildImages(light), darkBitmaps = buildImages(dark)
        ))
    }

    private fun parseWebBundle(json: JSONObject, entries: Map<String, ByteArray>): WebSkinBundle {
        val entrypoint = json.optString("entrypoint")
        val project = json.optString("project")
        require(entrypoint == "web/index.html" && project == "project.json") {
            "v3 requires packaged web/index.html and project.json"
        }
        val files = entries.filterKeys { V3_RESOURCE.matches(it) }
        require(files.keys.containsAll(setOf("web/index.html", "web/style.css", "web/app.js", "project.json"))) {
            "v3 package is not self-contained"
        }
        val projectJson = JSONObject(String(files.getValue(project), Charsets.UTF_8))
        require(projectJson.optInt("projectVersion") == 3 &&
            projectJson.optString("mode") in setOf("simple", "advanced")) { "Invalid v3 project" }
        val html = String(files.getValue(entrypoint), Charsets.UTF_8).lowercase()
        require("http://" !in html && "https://" !in html) { "Remote Web skin resources are forbidden" }
        require("<iframe" !in html && "<object" !in html) { "Embedded frames/objects are forbidden" }
        val capsJson = json.optJSONArray("capabilities") ?: org.json.JSONArray()
        val capabilities = (0 until capsJson.length()).map { capsJson.getString(it) }.toSet()
        // Unknown top-level v3 fields remain opaque package metadata. Only this
        // closed capability list can affect Host access; install retains the ZIP bytes.
        // host.edit is retained as inert metadata so existing v3 packages round-trip.
        require(capabilities.all { it in setOf("host.read", "host.action", "host.edit") }) {
            "Unsupported Web skin capability"
        }
        return WebSkinBundle(entrypoint, files, capabilities, json.optBoolean("fullCanvas", false))
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
        private const val UI_FILE = "ui.json"
        private const val MAX_ARCHIVE = 10_000_000
        private val ID = Regex("[a-z0-9][a-z0-9_-]{2,47}")
        private val IMAGE = Regex("images/[a-z0-9_-]+[.](png|webp)")
        private val V3_RESOURCE = Regex("(?:web|assets)/[A-Za-z0-9][A-Za-z0-9_./-]{0,119}|project[.]json")
    }
}
