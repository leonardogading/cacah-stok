package com.cacahstok.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Layar awal: isi username & pilih lokasi scan. */
class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var db: DbHelper
    private lateinit var etUser: EditText
    private lateinit var spLokasi: Spinner
    private lateinit var tvLokasiInfo: TextView
    private lateinit var tvItemInfo: TextView
    private var lokasiList: List<String> = emptyList()

    private val pickLokasi =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importLokasi(it) } }
    private val pickItems =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importItems(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        prefs = Prefs(this)
        db = DbHelper(this)

        etUser = findViewById(R.id.etUsername)
        spLokasi = findViewById(R.id.spLokasi)
        tvLokasiInfo = findViewById(R.id.tvLokasiInfo)
        tvItemInfo = findViewById(R.id.tvItemInfo)

        etUser.setText(prefs.username)
        loadLokasi()
        refreshItemInfo()

        findViewById<Button>(R.id.btnMulai).setOnClickListener { mulai() }
        findViewById<Button>(R.id.btnImporLokasi).setOnClickListener { pickLokasi.launch(arrayOf("*/*")) }
        findViewById<Button>(R.id.btnImporItem).setOnClickListener { pickItems.launch(arrayOf("*/*")) }
        findViewById<Button>(R.id.btnPengiriman).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnLihatHasil).setOnClickListener {
            startActivity(Intent(this, ResultActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<Button>(R.id.btnPengiriman).text =
            "Pengiriman data: ${prefs.syncMode.label}"
    }

    private fun mulai() {
        val user = etUser.text.toString().trim()
        val lokasi = spLokasi.selectedItem as? String
        when {
            user.isEmpty() -> { etUser.error = "Username wajib diisi"; etUser.requestFocus() }
            lokasi.isNullOrBlank() -> Toast.makeText(this, "Pilih lokasi scan", Toast.LENGTH_SHORT).show()
            else -> {
                prefs.username = user
                prefs.lokasi = lokasi
                startActivity(Intent(this, ScanActivity::class.java))
            }
        }
    }

    // ---------- Lokasi ----------

    private fun loadLokasi() {
        val imported = prefs.lokasiList
        if (imported.isNotEmpty()) {
            setLokasi(imported, prefs.lokasiSource.ifBlank { "file impor" })
            return
        }
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { assets.open("lokasiscan.xlsx").use { extractLokasi(XlsxReader.read(it)) } }
                    .getOrDefault(emptyList())
            }
            setLokasi(list, "lokasiscan.xlsx (bawaan)")
        }
    }

    private fun setLokasi(list: List<String>, source: String) {
        lokasiList = list
        spLokasi.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, list)
        val idx = list.indexOf(prefs.lokasi)
        if (idx >= 0) spLokasi.setSelection(idx)
        tvLokasiInfo.text = "${list.size} lokasi dari $source"
    }

    /** Ambil kolom A yang tidak kosong; abaikan baris judul "Lokasi". */
    private fun extractLokasi(rows: List<List<String>>): List<String> =
        rows.mapNotNull { it.firstOrNull()?.trim() }
            .filter { it.isNotEmpty() && !it.equals("lokasi", true) && !it.equals("lokasi scan", true) }
            .distinct()

    private fun importLokasi(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)!!.use { extractLokasi(XlsxReader.read(it)) } }
            }
            result.onSuccess { list ->
                if (list.isEmpty()) {
                    toast("Tidak ada lokasi di kolom A file tersebut")
                } else {
                    val name = fileName(uri)
                    prefs.lokasiList = list
                    prefs.lokasiSource = name
                    setLokasi(list, name)
                    toast("${list.size} lokasi berhasil diimpor")
                }
            }.onFailure { toast("Gagal membaca file: ${it.message}") }
        }
    }

    // ---------- Master item (opsional) ----------

    private fun importItems(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val rows = contentResolver.openInputStream(uri)!!.use { XlsxReader.read(it) }
                    val items = rows.mapNotNull { r ->
                        val kode = r.getOrNull(0)?.trim().orEmpty()
                        if (kode.isEmpty() || kode.equals("kode", true) || kode.equals("kode item", true)) null
                        else Item(kode, r.getOrNull(1)?.trim().orEmpty(), r.getOrNull(2)?.trim().orEmpty().uppercase())
                    }
                    db.upsertItems(items)
                    items.size
                }
            }
            result.onSuccess { n ->
                refreshItemInfo()
                MaterialAlertDialogBuilder(this@LoginActivity)
                    .setMessage("$n item berhasil diimpor ke master item.")
                    .setPositiveButton("OK", null).show()
            }.onFailure { toast("Gagal membaca file: ${it.message}") }
        }
    }

    private fun refreshItemInfo() {
        val n = db.itemCount()
        tvItemInfo.text = if (n == 0L) "Master item belum ada (opsional)." else "Master item: $n item"
    }

    private fun fileName(uri: Uri): String =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "file impor"

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
