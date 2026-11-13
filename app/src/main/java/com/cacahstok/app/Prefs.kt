package com.cacahstok.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Tujuan pengiriman data hasil cacah. */
enum class SyncMode(val label: String) {
    NONE("Hanya di HP"),
    SHEETS("Google Sheets"),
    FOLDER("Folder jaringan kantor");

    companion object {
        fun of(name: String?): SyncMode = values().firstOrNull { it.name == name } ?: NONE
    }
}

/** Menyimpan pilihan & pengaturan user. */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("cacah_stok", Context.MODE_PRIVATE)

    private fun str(key: String) = sp.getString(key, "") ?: ""
    private fun put(key: String, v: String) = sp.edit().putString(key, v).apply()

    var username: String
        get() = str("username")
        set(v) = put("username", v)

    var lokasi: String
        get() = str("lokasi")
        set(v) = put("lokasi", v)

    /** Daftar lokasi hasil impor. Kosong = pakai lokasiscan.xlsx bawaan aplikasi. */
    var lokasiList: List<String>
        get() = str("lokasi_list").split("\n").filter { it.isNotBlank() }
        set(v) = put("lokasi_list", v.joinToString("\n") { it.replace("\n", " ") })

    var lokasiSource: String
        get() = str("lokasi_source")
        set(v) = put("lokasi_source", v)

    var autoRescan: Boolean
        get() = sp.getBoolean("auto_rescan", false)
        set(v) = sp.edit().putBoolean("auto_rescan", v).apply()

    // ---------- Pengiriman data ----------

    var syncMode: SyncMode
        get() = SyncMode.of(sp.getString("sync_mode", null))
        set(v) = put("sync_mode", v.name)

    var sheetsUrl: String
        get() = str("sheets_url")
        set(v) = put("sheets_url", v)

    var sheetsToken: String
        get() = str("sheets_token")
        set(v) = put("sheets_token", v)

    /** Contoh: \\192.168.1.10\CacahStok */
    var folderPath: String
        get() = str("folder_path")
        set(v) = put("folder_path", v)

    var folderUser: String
        get() = str("folder_user")
        set(v) = put("folder_user", v)

    var folderPass: String
        get() = str("folder_pass")
        set(v) = put("folder_pass", v)

    var folderDomain: String
        get() = str("folder_domain")
        set(v) = put("folder_domain", v)

    /** ID unik HP ini (dibuat sekali). */
    val deviceId: String
        get() {
            var id = str("device_id")
            if (id.isEmpty()) {
                id = UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
                sp.edit().putString("device_id", id).commit()
            }
            return id
        }

    /**
     * Sesi cacah. Berganti setiap "Hapus semua data", supaya data yang sudah terkirim
     * di Google Sheets / folder jaringan tidak ikut terhapus.
     */
    val batchId: String
        get() {
            var id = str("batch_id")
            if (id.isEmpty()) id = newBatch()
            return id
        }

    fun newBatch(): String {
        val id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        sp.edit().putString("batch_id", id).commit()
        return id
    }

    /** Naik setiap ada data baru/diedit/dihapus. */
    var changeSeq: Long
        get() = sp.getLong("change_seq", 0)
        set(v) { sp.edit().putLong("change_seq", v).commit() }

    /** changeSeq terakhir yang sudah berhasil terkirim. */
    var syncedSeq: Long
        get() = sp.getLong("synced_seq", 0)
        set(v) = sp.edit().putLong("synced_seq", v).apply()

    var lastSyncAt: Long
        get() = sp.getLong("last_sync_at", 0)
        set(v) = sp.edit().putLong("last_sync_at", v).apply()

    var lastSyncCount: Int
        get() = sp.getInt("last_sync_count", 0)
        set(v) = sp.edit().putInt("last_sync_count", v).apply()

    var lastSyncError: String
        get() = str("last_sync_error")
        set(v) = put("last_sync_error", v)

    val hasPendingChanges: Boolean
        get() = changeSeq > syncedSeq
}
