package com.cacahstok.app

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Satu baris hasil pencacahan. */
data class CountRecord(
    val id: Long = 0,
    val scannedAt: Long,
    val username: String,
    val lokasi: String,
    val kode: String,
    val deskripsi: String,
    val qty: Double,
    val satuan: String,
)

/** Data master item (opsional) untuk mengisi deskripsi & satuan otomatis. */
data class Item(val kode: String, val deskripsi: String, val satuan: String)

/** Daftar satuan yang muncul sebagai pilihan cepat di form. */
val SATUAN_UMUM = listOf("PCS", "ROLL", "KG", "BOX", "PACK", "LBR", "SHEET", "MTR", "BAL", "SAK", "UNIT", "SET")

/** 10 -> "10", 2.50 -> "2.5" */
fun formatQty(q: Double): String =
    try {
        BigDecimal(q.toString()).stripTrailingZeros().toPlainString()
    } catch (e: NumberFormatException) {
        q.toString()
    }

/** Menerima "2,5" maupun "2.5". */
fun parseQty(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

fun formatDateTime(ms: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(ms))
