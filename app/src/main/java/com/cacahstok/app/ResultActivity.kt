package com.cacahstok.app

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Daftar hasil scan + export ke Excel. */
class ResultActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var db: DbHelper
    private lateinit var cbLokasi: CheckBox
    private lateinit var etSearch: EditText
    private lateinit var tvSummary: TextView
    private val adapter = RecordAdapter { r -> CountDialog.showEdit(this, db, r) { refresh() } }
    private lateinit var tvSync: TextView
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> updateSync() }

    private val createDoc = registerForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME)
    ) { uri -> uri?.let { exportTo(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        prefs = Prefs(this)
        db = DbHelper(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Hasil Scan"

        tvSync = findViewById(R.id.tvSync)
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)
        cbLokasi = findViewById(R.id.cbLokasi)
        etSearch = findViewById(R.id.etSearch)
        tvSummary = findViewById(R.id.tvSummary)

        if (prefs.lokasi.isBlank()) {
            cbLokasi.isChecked = false
            cbLokasi.isEnabled = false
        }
        cbLokasi.text = "Hanya lokasi: ${prefs.lokasi.ifBlank { "-" }}"
        cbLokasi.setOnCheckedChangeListener { _, _ -> refresh() }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        })

        findViewById<RecyclerView>(R.id.rvResult).apply {
            layoutManager = LinearLayoutManager(this@ResultActivity)
            addItemDecoration(DividerItemDecoration(this@ResultActivity, DividerItemDecoration.VERTICAL))
            adapter = this@ResultActivity.adapter
        }

        findViewById<Button>(R.id.btnExport).setOnClickListener {
            if (currentRecords().isEmpty()) toast("Belum ada data untuk diexport")
            else createDoc.launch(fileName())
        }
        findViewById<Button>(R.id.btnShare).setOnClickListener { share() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        updateSync()
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }

    private fun updateSync() {
        tvSync.text = SyncManager.statusText(prefs)
    }

    private fun lokasiFilter(): String? = if (cbLokasi.isChecked && prefs.lokasi.isNotBlank()) prefs.lokasi else null

    private fun currentRecords(): List<CountRecord> =
        db.records(lokasi = lokasiFilter(), search = etSearch.text.toString())

    private fun refresh() {
        val list = currentRecords()
        adapter.submit(list)
        val itemUnik = list.map { it.kode }.distinct().size
        tvSummary.text = "${list.size} baris  •  $itemUnik kode item unik"
    }

    /** Data diexport sesuai filter yang sedang aktif, urut dari scan paling awal. */
    private fun exportRecords(lokasi: String?, search: String) =
        db.records(lokasi = lokasi, search = search).sortedWith(compareBy({ it.scannedAt }, { it.id }))

    private fun fileName(): String {
        val loc = lokasiFilter()?.let { it.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_') } ?: "SemuaLokasi"
        val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "CacahStok_${loc}_$ts.xlsx"
    }

    private fun exportTo(uri: Uri) {
        val lokasi = lokasiFilter()
        val search = etSearch.text.toString()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val data = exportRecords(lokasi, search)
                    contentResolver.openOutputStream(uri)!!.use { XlsxWriter.write(data, it) }
                    data.size
                }
            }
            result.onSuccess { n -> toast("Export berhasil: $n baris") }
                .onFailure { toast("Export gagal: ${it.message}") }
        }
    }

    /** Export lalu langsung bagikan (WhatsApp, Email, Drive, dll). */
    private fun share() {
        val lokasi = lokasiFilter()
        val search = etSearch.text.toString()
        val name = fileName()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val data = exportRecords(lokasi, search)
                    require(data.isNotEmpty()) { "Belum ada data untuk diexport" }
                    val dir = File(cacheDir, "export").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val file = File(dir, name)
                    file.outputStream().use { XlsxWriter.write(data, it) }
                    file
                }
            }
            result.onSuccess { file ->
                val uri = FileProvider.getUriForFile(this@ResultActivity, "$packageName.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = XLSX_MIME
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, "Bagikan hasil cacah"))
            }.onFailure { toast(it.message ?: "Gagal membuat file") }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_SYNC, 0, "Kirim sekarang")
        menu.add(0, MENU_SETTINGS, 0, "Pengaturan pengiriman data")
        menu.add(0, MENU_DELETE_ALL, 0, "Hapus semua data")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { finish(); return true }
            MENU_SYNC -> {
                if (prefs.syncMode == SyncMode.NONE) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else {
                    SyncManager.request(this); toast("Mengirim data…")
                }
                return true
            }
            MENU_SETTINGS -> {
                startActivity(Intent(this, SettingsActivity::class.java)); return true
            }
            MENU_DELETE_ALL -> {
                val pending = prefs.syncMode != SyncMode.NONE && prefs.hasPendingChanges
                val extra = when {
                    pending -> "\n\n⚠ PERHATIAN: masih ada perubahan yang BELUM terkirim ke ${prefs.syncMode.label}!"
                    prefs.syncMode != SyncMode.NONE ->
                        "\n\nData yang sudah terkirim ke ${prefs.syncMode.label} tetap aman (tidak ikut terhapus)."
                    else -> ""
                }
                MaterialAlertDialogBuilder(this)
                    .setTitle("Hapus semua data?")
                    .setMessage("Semua hasil cacah (${db.countRecords(null)} baris, semua lokasi) akan dihapus " +
                        "dari HP ini. Pastikan sudah diexport.$extra")
                    .setPositiveButton("Hapus") { _, _ ->
                        db.deleteAll()
                        // Mulai sesi baru: file/baris lama di tujuan tidak ditimpa
                        prefs.newBatch()
                        prefs.syncedSeq = prefs.changeSeq
                        prefs.lastSyncAt = 0
                        prefs.lastSyncError = ""
                        refresh(); updateSync()
                        toast("Semua data dihapus. Sesi cacah baru dimulai.")
                    }
                    .setNegativeButton("Batal", null)
                    .show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        private const val MENU_DELETE_ALL = 1
        private const val MENU_SYNC = 2
        private const val MENU_SETTINGS = 3
    }
}
