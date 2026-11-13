package com.cacahstok.app

import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Form input hasil pencacahan (dipakai untuk data baru maupun edit). */
object CountDialog {

    /** Form setelah scan. onDone(true) jika data tersimpan. */
    fun showNew(
        activity: AppCompatActivity, db: DbHelper,
        username: String, lokasi: String,
        kode: String, nama: String,
        onDone: (Boolean) -> Unit,
    ) {
        // Lengkapi deskripsi & satuan dari master item jika ada
        val item = if (kode.isNotBlank()) db.findItem(kode) else null
        val base = CountRecord(
            scannedAt = System.currentTimeMillis(),
            username = username, lokasi = lokasi, kode = kode,
            deskripsi = nama.ifBlank { item?.deskripsi ?: "" },
            qty = 0.0,
            satuan = item?.satuan ?: "",
        )
        show(activity, db, existing = null, base = base, onDone = onDone)
    }

    fun showEdit(activity: AppCompatActivity, db: DbHelper, record: CountRecord, onDone: (Boolean) -> Unit) {
        show(activity, db, existing = record, base = record, onDone = onDone)
    }

    private fun show(
        activity: AppCompatActivity, db: DbHelper,
        existing: CountRecord?, base: CountRecord,
        onDone: (Boolean) -> Unit,
    ) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_count, null)
        val tvInfo = view.findViewById<TextView>(R.id.tvInfo)
        val tvMeta = view.findViewById<TextView>(R.id.tvMeta)
        val etKode = view.findViewById<EditText>(R.id.etKode)
        val etDesk = view.findViewById<EditText>(R.id.etDeskripsi)
        val etQty = view.findViewById<EditText>(R.id.etQty)
        val etSatuan = view.findViewById<AutoCompleteTextView>(R.id.etSatuan)

        etSatuan.setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, SATUAN_UMUM))
        etSatuan.setOnClickListener { etSatuan.showDropDown() }

        etKode.setText(base.kode)
        etDesk.setText(base.deskripsi)
        etSatuan.setText(base.satuan, false)
        if (existing != null) etQty.setText(formatQty(existing.qty))

        tvMeta.text = "Lokasi: ${base.lokasi}\nUser: ${base.username}" +
            if (existing != null) "\nWaktu scan: ${formatDateTime(existing.scannedAt)}" else ""

        if (existing == null && base.kode.isNotBlank()) {
            val (n, total) = db.summary(base.kode, base.lokasi)
            if (n > 0) {
                tvInfo.text = "Perhatian: item ini sudah dicacah $n kali di lokasi ini " +
                    "(total ${formatQty(total)}). Data baru akan ditambahkan sebagai baris terpisah."
                tvInfo.visibility = View.VISIBLE
            }
        }

        var saved = false
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(if (existing == null) "Input Hasil Cacah" else "Edit Hasil Cacah")
            .setView(view)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
        if (existing != null) builder.setNeutralButton("Hapus", null)
        val dialog = builder.create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val kode = etKode.text.toString().trim()
                val desk = etDesk.text.toString().trim()
                val qty = parseQty(etQty.text.toString())
                val satuan = etSatuan.text.toString().trim().uppercase()
                when {
                    kode.isEmpty() -> { etKode.error = "Kode item wajib diisi"; etKode.requestFocus() }
                    qty == null || qty < 0 -> { etQty.error = "Isi qty dengan angka"; etQty.requestFocus() }
                    satuan.isEmpty() -> { etSatuan.error = "Satuan wajib diisi"; etSatuan.requestFocus() }
                    else -> {
                        val rec = base.copy(kode = kode, deskripsi = desk, qty = qty, satuan = satuan)
                        if (existing == null) db.insert(rec) else db.update(rec)
                        // "Belajar": item ini otomatis terisi pada scan berikutnya
                        if (desk.isNotEmpty()) db.upsertItems(listOf(Item(kode, desk, satuan)))
                        saved = true
                        Toast.makeText(activity, "Tersimpan: $kode = ${formatQty(qty)} $satuan", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
            if (existing != null) {
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                    MaterialAlertDialogBuilder(activity)
                        .setMessage("Hapus data ${existing.kode} (${formatQty(existing.qty)} ${existing.satuan})?")
                        .setPositiveButton("Hapus") { _, _ ->
                            db.delete(existing.id)
                            saved = true
                            dialog.dismiss()
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            }
            // Fokus ke kolom yang perlu diisi lebih dulu
            val target = when {
                etKode.text.isBlank() -> etKode
                etDesk.text.isBlank() -> etDesk
                else -> etQty
            }
            target.requestFocus()
            target.setSelection(target.text.length)
        }
        dialog.setOnDismissListener { onDone(saved) }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
    }
}
