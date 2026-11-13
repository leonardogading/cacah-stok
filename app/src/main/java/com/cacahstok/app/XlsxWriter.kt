package com.cacahstok.app

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Penulis .xlsx ringan (tanpa Apache POI).
 * Kolom: Tanggal & Jam Scan | Username | Lokasi Scan | Kode Item | Deskripsi Item | Qty | Satuan
 */
object XlsxWriter {

    val HEADERS = listOf(
        "Tanggal & Jam Scan", "Username", "Lokasi Scan",
        "Kode Item", "Deskripsi Item", "Qty", "Satuan"
    )
    private val COL_WIDTHS = listOf(20, 16, 40, 18, 45, 10, 10)
    private const val SHEET_NAME = "Cacah Stok"

    fun write(records: List<CountRecord>, out: OutputStream) {
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val lastRow = records.size + 1
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", workbookXml(lastRow))
            put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            put("xl/styles.xml", STYLES)
            put("xl/worksheets/sheet1.xml", sheetXml(records))
        }
    }

    private fun sheetXml(records: List<CountRecord>): String {
        val lastRow = records.size + 1
        val sb = StringBuilder(256 + records.size * 400)
        sb.append(XML_HEAD)
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
        sb.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        sb.append("<dimension ref=\"A1:G").append(lastRow).append("\"/>")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\">")
        sb.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        sb.append("</sheetView></sheetViews>")
        sb.append("<sheetFormatPr defaultRowHeight=\"15\"/>")
        sb.append("<cols>")
        COL_WIDTHS.forEachIndexed { i, w ->
            sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                .append("\" width=\"").append(w).append("\" customWidth=\"1\"/>")
        }
        sb.append("</cols><sheetData>")

        // Header (style 1 = tebal)
        sb.append("<row r=\"1\">")
        HEADERS.forEachIndexed { i, h -> strCell(sb, "${'A' + i}1", h, style = 1) }
        sb.append("</row>")

        val tz = TimeZone.getDefault()
        records.forEachIndexed { idx, r ->
            val row = idx + 2
            sb.append("<row r=\"").append(row).append("\">")
            // Tanggal disimpan sebagai tanggal Excel asli (bisa di-sort/filter), style 2
            val serial = (r.scannedAt + tz.getOffset(r.scannedAt)) / 86_400_000.0 + 25569.0
            sb.append("<c r=\"A").append(row).append("\" s=\"2\"><v>").append(serial).append("</v></c>")
            strCell(sb, "B$row", r.username)
            strCell(sb, "C$row", r.lokasi)
            strCell(sb, "D$row", r.kode)
            strCell(sb, "E$row", r.deskripsi)
            sb.append("<c r=\"F").append(row).append("\"><v>").append(formatQty(r.qty)).append("</v></c>")
            strCell(sb, "G$row", r.satuan)
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        sb.append("<autoFilter ref=\"A1:G").append(lastRow).append("\"/>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun strCell(sb: StringBuilder, ref: String, text: String, style: Int = 0) {
        sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"")
        if (style != 0) sb.append(" s=\"").append(style).append("\"")
        sb.append("><is><t xml:space=\"preserve\">").append(esc(text)).append("</t></is></c>")
    }

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' -> sb.append(ch)
                ch < ' ' -> {} // karakter kontrol tidak valid di XML
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun workbookXml(lastRow: Int) = XML_HEAD +
        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
        "<sheets><sheet name=\"$SHEET_NAME\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
        "<definedNames><definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"0\" hidden=\"1\">" +
        "'$SHEET_NAME'!\$A\$1:\$G\$$lastRow</definedName></definedNames>" +
        "</workbook>"

    private const val XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    private const val CONTENT_TYPES = XML_HEAD +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
        "</Types>"

    private const val ROOT_RELS = XML_HEAD +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private const val WORKBOOK_RELS = XML_HEAD +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
        "</Relationships>"

    // style 0 = normal, 1 = header tebal, 2 = tanggal dd/mm/yyyy hh:mm:ss
    private const val STYLES = XML_HEAD +
        "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
        "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"dd/mm/yyyy hh:mm:ss\"/></numFmts>" +
        "<fonts count=\"2\">" +
        "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "</fonts>" +
        "<fills count=\"3\">" +
        "<fill><patternFill patternType=\"none\"/></fill>" +
        "<fill><patternFill patternType=\"gray125\"/></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDDEBF7\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
        "</fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"3\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>" +
        "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "</cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "</styleSheet>"
}
