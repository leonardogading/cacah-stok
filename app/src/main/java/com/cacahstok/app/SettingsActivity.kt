package com.cacahstok.app

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pilih tujuan pengiriman data: hanya HP, Google Sheets, atau folder jaringan kantor. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var rgMode: RadioGroup
    private lateinit var groupSheets: View
    private lateinit var groupFolder: View
    private lateinit var etUrl: EditText
    private lateinit var etToken: EditText
    private lateinit var etPath: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etDomain: EditText
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Pengiriman Data"
        prefs = Prefs(this)

        rgMode = findViewById(R.id.rgMode)
        groupSheets = findViewById(R.id.groupSheets)
        groupFolder = findViewById(R.id.groupFolder)
        etUrl = findViewById(R.id.etSheetsUrl)
        etToken = findViewById(R.id.etSheetsToken)
        etPath = findViewById(R.id.etFolderPath)
        etUser = findViewById(R.id.etFolderUser)
        etPass = findViewById(R.id.etFolderPass)
        etDomain = findViewById(R.id.etFolderDomain)
        tvStatus = findViewById(R.id.tvTestStatus)

        etUrl.setText(prefs.sheetsUrl)
        etToken.setText(prefs.sheetsToken)
        etPath.setText(prefs.folderPath)
        etUser.setText(prefs.folderUser)
        etPass.setText(prefs.folderPass)
        etDomain.setText(prefs.folderDomain)
        findViewById<TextView>(R.id.tvDevice).text =
            "ID HP ini: ${SyncManager.deviceLabel(prefs)}   •   Sesi: ${prefs.batchId}"

        rgMode.check(
            when (prefs.syncMode) {
                SyncMode.NONE -> R.id.rbNone
                SyncMode.SHEETS -> R.id.rbSheets
                SyncMode.FOLDER -> R.id.rbFolder
            }
        )
        rgMode.setOnCheckedChangeListener { _, _ -> updateGroups() }
        updateGroups()

        findViewById<Button>(R.id.btnTest).setOnClickListener { testConnection() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
    }

    private fun selectedMode(): SyncMode = when (rgMode.checkedRadioButtonId) {
        R.id.rbSheets -> SyncMode.SHEETS
        R.id.rbFolder -> SyncMode.FOLDER
        else -> SyncMode.NONE
    }

    private fun updateGroups() {
        val mode = selectedMode()
        groupSheets.visibility = if (mode == SyncMode.SHEETS) View.VISIBLE else View.GONE
        groupFolder.visibility = if (mode == SyncMode.FOLDER) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnTest).visibility = if (mode == SyncMode.NONE) View.GONE else View.VISIBLE
        tvStatus.text = ""
    }

    /** Simpan isian ke prefs. Mengembalikan false bila ada isian wajib yang kosong. */
    private fun storeFields(): Boolean {
        val mode = selectedMode()
        val url = etUrl.text.toString().trim()
        val path = etPath.text.toString().trim()
        if (mode == SyncMode.SHEETS && !url.startsWith("https://script.google.com/")) {
            etUrl.error = "Tempel URL Web App (diawali https://script.google.com/...)"
            etUrl.requestFocus(); return false
        }
        if (mode == SyncMode.FOLDER && path.replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }.size < 2) {
            etPath.error = "Contoh: \\\\192.168.1.10\\CacahStok"
            etPath.requestFocus(); return false
        }
        prefs.sheetsUrl = url
        prefs.sheetsToken = etToken.text.toString().trim()
        prefs.folderPath = path
        prefs.folderUser = etUser.text.toString().trim()
        prefs.folderPass = etPass.text.toString()
        prefs.folderDomain = etDomain.text.toString().trim()
        prefs.syncMode = mode
        return true
    }

    private fun save() {
        if (!storeFields()) return
        prefs.lastSyncError = ""
        if (prefs.syncMode != SyncMode.NONE) {
            // Kirim semua data yang sudah ada ke tujuan baru
            SyncManager.markChanged(this)
            Toast.makeText(this, "Tersimpan. Data dikirim otomatis ke ${prefs.syncMode.label}.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Tersimpan. Data hanya disimpan di HP.", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun testConnection() {
        if (!storeFields()) return
        tvStatus.text = "Menguji koneksi…"
        val mode = prefs.syncMode
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (mode) {
                        SyncMode.SHEETS -> "Terhubung ke spreadsheet \"${SheetsSync.ping(prefs)}\""
                        SyncMode.FOLDER -> "Berhasil menulis ke ${FolderSync.test(prefs)}"
                        SyncMode.NONE -> ""
                    }
                }
            }
            result.onSuccess { tvStatus.text = "✔ $it" }
                .onFailure {
                    tvStatus.text = "✖ Gagal"
                    MaterialAlertDialogBuilder(this@SettingsActivity)
                        .setTitle("Tes koneksi gagal")
                        .setMessage(friendlyError(it))
                        .setPositiveButton("OK", null)
                        .show()
                }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
