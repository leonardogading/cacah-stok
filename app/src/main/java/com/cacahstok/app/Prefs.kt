package com.cacahstok.app

import android.content.Context

/** Menyimpan pilihan terakhir user agar tidak perlu diisi ulang. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("cacah_stok", Context.MODE_PRIVATE)

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(v) = sp.edit().putString("username", v).apply()

    var lokasi: String
        get() = sp.getString("lokasi", "") ?: ""
        set(v) = sp.edit().putString("lokasi", v).apply()

    /** Daftar lokasi hasil impor. Kosong = pakai lokasiscan.xlsx bawaan aplikasi. */
    var lokasiList: List<String>
        get() = (sp.getString("lokasi_list", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) = sp.edit().putString("lokasi_list", v.joinToString("\n") { it.replace("\n", " ") }).apply()

    var lokasiSource: String
        get() = sp.getString("lokasi_source", "") ?: ""
        set(v) = sp.edit().putString("lokasi_source", v).apply()

    var autoRescan: Boolean
        get() = sp.getBoolean("auto_rescan", false)
        set(v) = sp.edit().putBoolean("auto_rescan", v).apply()
}
