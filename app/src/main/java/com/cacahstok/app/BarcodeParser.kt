package com.cacahstok.app

/**
 * Mengambil kode item & nama item dari isi barcode.
 *
 *  - "KODE|NAMA ITEM"  / "KODE;NAMA ITEM" / "KODE<TAB>NAMA" / "KODE<ENTER>NAMA"
 *      -> kode = KODE, nama = NAMA ITEM
 *  - "KODE" saja -> kode = KODE, nama dicari dari master item / riwayat cacah
 */
object BarcodeParser {
    data class Parsed(val kode: String, val nama: String)

    private val SEP = Regex("[|;\\t\\n]")

    fun parse(raw: String): Parsed {
        val text = raw.replace("\r", "").trim()
        val parts = text.split(SEP, limit = 2)
        return if (parts.size == 2) Parsed(parts[0].trim(), parts[1].replace("\n", " ").trim())
        else Parsed(text, "")
    }
}
