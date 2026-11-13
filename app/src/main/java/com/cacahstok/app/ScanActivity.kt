package com.cacahstok.app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Layar pencacahan.
 *  - Tombol SCAN: memakai kamera HP.
 *  - Kolom "Input scanner": untuk barcode scanner fisik / HP scanner industri
 *    (mode keyboard / keyboard wedge, diakhiri Enter), atau ketik kode manual.
 */
class ScanActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var db: DbHelper
    private lateinit var etScanner: EditText
    private lateinit var cbAuto: CheckBox
    private lateinit var tvTotal: TextView
    private val adapter = RecordAdapter { r -> CountDialog.showEdit(this, db, r) { refresh(); focusScanner() } }
    private var dialogOpen = false

    private val cameraScan = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) handleBarcode(contents, fromCamera = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        prefs = Prefs(this)
        db = DbHelper(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Cacah Stok"

        findViewById<TextView>(R.id.tvUser).text = prefs.username
        findViewById<TextView>(R.id.tvLokasi).text = prefs.lokasi
        tvTotal = findViewById(R.id.tvTotal)
        etScanner = findViewById(R.id.etScanner)
        cbAuto = findViewById(R.id.cbAuto)
        cbAuto.isChecked = prefs.autoRescan
        cbAuto.setOnCheckedChangeListener { _, checked -> prefs.autoRescan = checked }

        findViewById<Button>(R.id.btnScan).setOnClickListener { launchCamera() }
        findViewById<Button>(R.id.btnHasil).setOnClickListener {
            startActivity(Intent(this, ResultActivity::class.java))
        }

        // Barcode scanner fisik biasanya mengirim teks + Enter
        etScanner.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.action == KeyEvent.ACTION_UP) submitScannerText()
                true
            } else false
        }
        etScanner.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_NULL
            ) {
                submitScannerText(); true
            } else false
        }

        findViewById<RecyclerView>(R.id.rvRecent).apply {
            layoutManager = LinearLayoutManager(this@ScanActivity)
            adapter = this@ScanActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        focusScanner()
    }

    private fun submitScannerText() {
        val text = etScanner.text.toString()
        etScanner.setText("")
        if (text.isNotBlank()) handleBarcode(text, fromCamera = false)
    }

    private fun launchCamera() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            .setPrompt("Arahkan kamera ke barcode item\n(${prefs.lokasi})")
            .setBeepEnabled(true)
            .setOrientationLocked(false)
        cameraScan.launch(options)
    }

    private fun handleBarcode(raw: String, fromCamera: Boolean) {
        if (dialogOpen) return
        val parsed = BarcodeParser.parse(raw)
        dialogOpen = true
        CountDialog.showNew(this, db, prefs.username, prefs.lokasi, parsed.kode, parsed.nama) { saved ->
            dialogOpen = false
            refresh()
            if (saved && fromCamera && cbAuto.isChecked) launchCamera() else focusScanner()
        }
    }

    private fun refresh() {
        tvTotal.text = "Sudah dicacah di lokasi ini: ${db.countRecords(prefs.lokasi)} baris"
        adapter.submit(db.records(lokasi = prefs.lokasi, limit = 50))
    }

    private fun focusScanner() {
        etScanner.post { etScanner.requestFocus() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Ganti user / lokasi")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home || item.itemId == 1) {
            finish(); return true
        }
        return super.onOptionsItemSelected(item)
    }
}
