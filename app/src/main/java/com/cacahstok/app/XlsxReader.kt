package com.cacahstok.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.TreeMap
import java.util.zip.ZipInputStream

/**
 * Pembaca .xlsx ringan (tanpa Apache POI). Membaca sheet pertama dan
 * mengembalikan isi sel sebagai teks, baris per baris.
 */
object XlsxReader {

    private val SHEET_RE = Regex("xl/worksheets/sheet(\\d+)\\.xml")

    fun read(input: InputStream): List<List<String>> {
        var shared: ByteArray? = null
        val sheets = TreeMap<Int, ByteArray>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val name = e.name
                if (name == "xl/sharedStrings.xml") {
                    shared = zip.readBytes()
                } else {
                    val m = SHEET_RE.matchEntire(name)
                    if (m != null) sheets[m.groupValues[1].toInt()] = zip.readBytes()
                }
                e = zip.nextEntry
            }
        }
        val sheet = sheets.values.firstOrNull()
            ?: throw IllegalArgumentException("File bukan Excel .xlsx yang valid")
        val strings = shared?.let { parseShared(it) } ?: emptyList()
        return parseSheet(sheet, strings)
    }

    private fun newParser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }

    private fun local(name: String?): String = name?.substringAfter(':') ?: ""

    private fun parseShared(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        val p = newParser(bytes)
        val sb = StringBuilder()
        var inT = false
        var inRph = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "si" -> sb.setLength(0)
                    "t" -> inT = true
                    "rPh" -> inRph = true
                }
                XmlPullParser.TEXT -> if (inT && !inRph) sb.append(p.text)
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "si" -> out.add(sb.toString())
                    "t" -> inT = false
                    "rPh" -> inRph = false
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun parseSheet(bytes: ByteArray, strings: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val p = newParser(bytes)
        var cells = TreeMap<Int, String>()
        var col = 0
        var nextCol = 0
        var type: String? = null
        val sb = StringBuilder()
        var inValue = false

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "row" -> {
                        cells = TreeMap(); nextCol = 0
                    }
                    "c" -> {
                        col = colIndex(p.getAttributeValue(null, "r")) ?: nextCol
                        type = p.getAttributeValue(null, "t")
                        sb.setLength(0)
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) sb.append(p.text)
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = sb.toString()
                        val value = when (type) {
                            "s" -> strings.getOrNull(raw.trim().toIntOrNull() ?: -1) ?: ""
                            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                            else -> raw
                        }
                        cells[col] = value.trim()
                        nextCol = col + 1
                    }
                    "row" -> {
                        val size = if (cells.isEmpty()) 0 else cells.lastKey() + 1
                        rows.add(List(size) { cells[it] ?: "" })
                    }
                }
            }
            ev = p.next()
        }
        return rows
    }

    /** "C12" -> 2 */
    private fun colIndex(ref: String?): Int? {
        if (ref.isNullOrEmpty()) return null
        var n = 0
        var any = false
        for (ch in ref) {
            if (ch in 'A'..'Z') { n = n * 26 + (ch - 'A' + 1); any = true }
            else if (ch in 'a'..'z') { n = n * 26 + (ch - 'a' + 1); any = true }
            else break
        }
        return if (any) n - 1 else null
    }
}
