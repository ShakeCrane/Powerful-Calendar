package dev.powerfulcalendar.interop

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Untrusted external file -> bounded, textual EVIDENCE. It never publishes events or executes
 * document instructions/macros. User-visible preview is required before any remote AI upload.
 */
data class AiFileEvidence(
    val filename: String,
    val format: String,
    val text: String,
    val truncated: Boolean,
    val note: String
)

object AiFileEvidenceReader {
    private const val MAX_FILE = 3_000_000
    private const val MAX_XML_TOTAL = 8_000_000
    private const val MAX_OUTPUT = 24_000

    fun read(context: Context, uri: Uri): AiFileEvidence {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()?.take(180) ?: "untitled"
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                require(total <= MAX_FILE) { "文件超过 3 MB 安全限制，请拆分后重试" }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: error("无法读取文件")
        return extractFromBytes(name, bytes)
    }

    internal fun extractFromBytes(name: String, bytes: ByteArray): AiFileEvidence {
        require(bytes.size <= MAX_FILE) { "文件过大" }
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val body = when (extension) {
            "ics", "txt", "csv", "tsv", "json", "md", "log" -> {
                val bom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
                    bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
                String(bytes, if (bom) 3 else 0, bytes.size - if (bom) 3 else 0, Charsets.UTF_8)
            }
            "docx" -> extractDocx(readZipXml(bytes))
            "xlsx" -> extractXlsx(readZipXml(bytes))
            else -> throw IllegalArgumentException(
                "当前支持 ICS、TXT、CSV/TSV、JSON、Markdown、DOCX、XLSX；其他格式暂未实现安全提取"
            )
        }
        val clean = body.replace('\u0000', ' ').trim()
        require(clean.isNotBlank()) { "没有提取到可读文本，文件可能为空或含不支持的结构" }
        val truncated = clean.length > MAX_OUTPUT
        val note = if (truncated) "已截取前 24000 字符，不能把截断内容当作完整导入结果"
            else "已完整提取文本；模型可能仍需核对日期、列含义和重复规则"
        return AiFileEvidence(name, extension, clean.take(MAX_OUTPUT), truncated, note)
    }

    private fun readZipXml(bytes: ByteArray): Map<String, String> {
        val entries = LinkedHashMap<String, String>()
        var count = 0
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                require(count <= 1024) { "压缩包条目过多" }
                val name = entry.name.replace('\\', '/')
                require(!name.startsWith("/") && ".." !in name.split('/')) { "不安全的文件路径" }
                if (!entry.isDirectory && name.endsWith(".xml") &&
                    (name.startsWith("word/") || name.startsWith("xl/"))) {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_XML_TOTAL && output.size() + n <= MAX_XML_TOTAL) {
                            "文件解压后的 XML 超过安全限制"
                        }
                        output.write(buffer, 0, n)
                    }
                    entries[name] = output.toString(Charsets.UTF_8.name())
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun parser(xml: String): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(StringReader(xml))
    }

    private fun extractDocx(entries: Map<String, String>): String {
        val xml = entries["word/document.xml"] ?: error("DOCX 不包含 word/document.xml")
        val reader = parser(xml)
        val result = StringBuilder()
        var event = reader.eventType
        while (event != XmlPullParser.END_DOCUMENT && result.length < MAX_OUTPUT + 1000) {
            val name = reader.name?.substringAfterLast(':')
            if (event == XmlPullParser.START_TAG && name == "t") {
                result.append(reader.nextText())
            } else if (event == XmlPullParser.END_TAG) {
                when (name) {
                    "p" -> result.append('\n')
                    "tc" -> result.append('\t')
                }
            }
            event = reader.next()
        }
        return result.toString()
    }

    private fun extractXlsx(entries: Map<String, String>): String {
        val strings = ArrayList<String>()
        entries["xl/sharedStrings.xml"]?.let { xml ->
            val reader = parser(xml)
            var event = reader.eventType
            var insideItem = false
            var text = StringBuilder()
            while (event != XmlPullParser.END_DOCUMENT && strings.size < 30_000) {
                val name = reader.name?.substringAfterLast(':')
                if (event == XmlPullParser.START_TAG) {
                    when (name) {
                        "si" -> { insideItem = true; text = StringBuilder() }
                        "t" -> if (insideItem) text.append(reader.nextText())
                    }
                } else if (event == XmlPullParser.END_TAG && name == "si") {
                    strings.add(text.toString())
                    insideItem = false
                }
                event = reader.next()
            }
        }
        val sheets = entries.keys.filter {
            it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml")
        }.sorted().take(8)
        require(sheets.isNotEmpty()) { "XLSX 没有可读取的工作表" }
        val out = StringBuilder()
        for (sheet in sheets) {
            out.append("# ").append(sheet.substringAfterLast('/')).append('\n')
            val reader = parser(entries.getValue(sheet))
            var event = reader.eventType
            var cellType = ""
            var cell = ""
            var rowCount = 0
            while (event != XmlPullParser.END_DOCUMENT && out.length < MAX_OUTPUT + 1000) {
                val name = reader.name?.substringAfterLast(':')
                if (event == XmlPullParser.START_TAG) {
                    when (name) {
                        "c" -> {
                            cellType = reader.getAttributeValue(null, "t").orEmpty()
                            cell = ""
                        }
                        "v" -> {
                            val value = reader.nextText()
                            cell = if (cellType == "s") strings.getOrNull(value.toIntOrNull() ?: -1)
                                ?: "[无效共享字符串]" else value
                        }
                        "t" -> if (cellType == "inlineStr") cell += reader.nextText()
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    when (name) {
                        "c" -> out.append(cell.replace('\t', ' ')).append('\t')
                        "row" -> {
                            out.append('\n')
                            rowCount++
                            if (rowCount >= 1500) {
                                out.append("[工作表行数上限]\n")
                                break
                            }
                        }
                    }
                }
                event = reader.next()
            }
        }
        return out.toString()
    }
}
