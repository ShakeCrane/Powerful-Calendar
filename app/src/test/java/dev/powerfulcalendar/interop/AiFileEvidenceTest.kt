package dev.powerfulcalendar.interop

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiFileEvidenceTest {
    private fun archive(vararg xml: Pair<String, String>): ByteArray {
        val result = ByteArrayOutputStream()
        ZipOutputStream(result).use { zip ->
            xml.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return result.toByteArray()
    }

    @Test fun plainIcsIsEvidenceNotAutomaticallyPublished() {
        val bytes = "BEGIN:VCALENDAR\nRRULE:FREQ=WEEKLY\nEND:VCALENDAR".toByteArray()
        val evidence = AiFileEvidenceReader.extractFromBytes("course.ics", bytes)
        assertEquals("ics", evidence.format)
        assertTrue(evidence.text.contains("RRULE:"))
        assertFalse(evidence.truncated)
    }

    @Test fun docxExtractsTableParagraphsWithoutMacrosOrRawZip() {
        val xml = """<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:tbl><w:tr><w:tc><w:p><w:r><w:t>高等数学</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>周一 08:00</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>"""
        val evidence = AiFileEvidenceReader.extractFromBytes(
            "class.docx", archive("word/document.xml" to xml, "word/vbaProject.bin" to "ignore"))
        assertEquals("docx", evidence.format)
        assertTrue(evidence.text.contains("高等数学"))
        assertTrue(evidence.text.contains("08:00"))
    }

    @Test fun xlsxResolvesSharedStringsAndCellValues() {
        val shared = """<?xml version="1.0"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>物理实验</t></si></sst>"""
        val sheet = """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row><c t="s"><v>0</v></c><c><v>2</v></c></row></sheetData></worksheet>"""
        val evidence = AiFileEvidenceReader.extractFromBytes("sheet.xlsx",
            archive("xl/sharedStrings.xml" to shared, "xl/worksheets/sheet1.xml" to sheet))
        assertTrue(evidence.text.contains("物理实验"))
        assertTrue(evidence.text.contains("2"))
    }

    @Test fun unsupportedAndTooLargeAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AiFileEvidenceReader.extractFromBytes("notes.pdf", "raw".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiFileEvidenceReader.extractFromBytes("huge.txt", ByteArray(3_000_001))
        }
    }

    @Test fun pathTraversalInDocumentIsRejected() {
        val xml = """<document><p>unsafe</p></document>"""
        assertThrows(IllegalArgumentException::class.java) {
            AiFileEvidenceReader.extractFromBytes("bad.docx", archive("../word/document.xml" to xml))
        }
    }
}
