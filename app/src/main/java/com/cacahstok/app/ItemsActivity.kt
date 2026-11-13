package com.cacahstok.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.DialogInterface

/** Melihat, mencari, mengedit dan menghapus data master item hasil impor. */
class ItemsActivity : AppCompatActivity() {

    private lateinit var db: DbHelper
    private lateinit var etSearch: EditText
    private lateinit var tvSummary: TextView
    private lateinit var btnSelectAll: Button
    private lateinit var btnDelete: Button
    private val adapter = ItemAdapter(
        onClick = { item -> showEdit(item) },
        onSelectionChanged = { updateSummary() },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_items)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Master Item"
        db = DbHelper(this)

        etSearch = findViewById(R.id.etSearch)
        tvSummary = findViewById(R.id.tvSummary)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDelete = findViewById(R.id.btnDelete)

        findViewById<RecyclerView>(R.id.rvItems).apply {
            layoutManager = LinearLayoutManager(this@ItemsActivity)
            addItemDecoration(DividerItemDecoration(this@ItemsActivity, DividerItemDecoration.VERTICAL))
            adapter = this@ItemsActivity.adapter
        }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        })

        btnSelectAll.setOnClickListener {
            if (adapter.allVisibleSelected()) adapter.clearSelection() else adapter.selectAllVisible()
        }
        btnDelete.setOnClickListener { confirmDeleteSelected() }
        refresh()
    }

    private fun refresh() {
        adapter.submit(db.items(etSearch.text.toString()))
        updateSummary()
    }

    private fun updateSummary() {
        val total = db.itemCount()
        val shown = adapter.itemCount
        val sel = adapter.selected.size
        tvSummary.text = buildString {
            append(if (shown.toLong() == total) "$total item" else "$shown dari $total item")
            if (sel > 0) append("  •  $sel dipilih")
        }
        btnDelete.isEnabled = sel > 0
        btnDelete.text = if (sel > 0) "HAPUS TERPILIH ($sel)" else "HAPUS TERPILIH"
        btnSelectAll.text = if (shown > 0 && adapter.allVisibleSelected()) "BATAL PILIH" else "PILIH SEMUA"
        btnSelectAll.isEnabled = shown > 0
    }

    private fun confirmDeleteSelected() {
        val kodes = adapter.selected.toList()
        if (kodes.isEmpty()) return
        val preview = kodes.take(5).joinToString("\n") { "• $it" } +
            if (kodes.size > 5) "\n… dan ${kodes.size - 5} lainnya" else ""
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus ${kodes.size} item?")
            .setMessage("$preview\n\nItem dihapus dari master item saja. Data hasil cacah tidak ikut terhapus.")
            .setPositiveButton("Hapus") { _, _ ->
                db.deleteItems(kodes)
                adapter.clearSelection()
                refresh()
                toast("${kodes.size} item dihapus")
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /** Edit / tambah item. item = null berarti tambah baru. */
    private fun showEdit(item: Item?) {
        val view = layoutInflater.inflate(R.layout.dialog_item, null)
        val etKode = view.findViewById<EditText>(R.id.etKode)
        val etDesk = view.findViewById<EditText>(R.id.etDeskripsi)
        val etSatuan = view.findViewById<AutoCompleteTextView>(R.id.etSatuan)
        etSatuan.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, SATUAN_UMUM))
        etSatuan.setOnClickListener { etSatuan.showDropDown() }
        if (item != null) {
            etKode.setText(item.kode)
            etDesk.setText(item.deskripsi)
            etSatuan.setText(item.satuan, false)
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(if (item == null) "Tambah Item" else "Edit Item")
            .setView(view)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
        if (item != null) builder.setNeutralButton("Hapus", null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val kode = etKode.text.toString().trim()
                val desk = etDesk.text.toString().trim()
                val satuan = etSatuan.text.toString().trim().uppercase()
                if (kode.isEmpty()) {
                    etKode.error = "Kode item wajib diisi"; return@setOnClickListener
                }
                if (item != null && kode != item.kode) db.deleteItems(listOf(item.kode))
                db.upsertItems(listOf(Item(kode, desk, satuan)))
                refresh()
                toast("Tersimpan: $kode")
                dialog.dismiss()
            }
            if (item != null) {
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                    MaterialAlertDialogBuilder(this)
                        .setMessage("Hapus item ${item.kode} dari master item?")
                        .setPositiveButton("Hapus") { _, _ ->
                            db.deleteItems(listOf(item.kode))
                            adapter.selected.remove(item.kode)
                            refresh()
                            toast("Item ${item.kode} dihapus")
                            dialog.dismiss()
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            }
        }
        dialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_ADD, 0, "Tambah item")
        menu.add(0, MENU_DELETE_ALL, 1, "Hapus semua master item")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { finish(); return true }
            MENU_ADD -> { showEdit(null); return true }
            MENU_DELETE_ALL -> {
                val total = db.itemCount()
                if (total == 0L) { toast("Master item sudah kosong"); return true }
                MaterialAlertDialogBuilder(this)
                    .setTitle("Hapus semua master item?")
                    .setMessage("Semua $total item akan dihapus. Data hasil cacah tidak ikut terhapus. " +
                        "Anda bisa mengimpor ulang file master item kapan saja.")
                    .setPositiveButton("Hapus semua") { _, _ ->
                        db.deleteAllItems()
                        adapter.clearSelection()
                        refresh()
                        toast("Semua master item dihapus")
                    }
                    .setNegativeButton("Batal", null)
                    .show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val MENU_ADD = 1
        private const val MENU_DELETE_ALL = 2
    }
}

/** Daftar master item dengan kotak centang untuk pilih banyak. */
class ItemAdapter(
    private val onClick: (Item) -> Unit,
    private val onSelectionChanged: () -> Unit,
) : RecyclerView.Adapter<ItemAdapter.VH>() {

    private var items: List<Item> = emptyList()
    val selected = LinkedHashSet<String>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Item>) {
        items = list
        notifyDataSetChanged()
    }

    fun allVisibleSelected(): Boolean = items.isNotEmpty() && items.all { it.kode in selected }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAllVisible() {
        items.forEach { selected.add(it.kode) }
        notifyDataSetChanged(); onSelectionChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged(); onSelectionChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cb: CheckBox = v.findViewById(R.id.cbSelect)
        val tvKode: TextView = v.findViewById(R.id.tvKode)
        val tvDesk: TextView = v.findViewById(R.id.tvDeskripsi)
        val tvSatuan: TextView = v.findViewById(R.id.tvSatuan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_master, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.tvKode.text = item.kode
        h.tvDesk.text = item.deskripsi.ifBlank { "(tanpa deskripsi)" }
        h.tvSatuan.text = item.satuan
        h.cb.setOnCheckedChangeListener(null)
        h.cb.isChecked = item.kode in selected
        h.cb.setOnCheckedChangeListener { _, checked ->
            if (checked) selected.add(item.kode) else selected.remove(item.kode)
            onSelectionChanged()
        }
        h.itemView.setOnClickListener { _ -> onClick(item) }
        h.itemView.setOnLongClickListener { _ ->
            h.cb.isChecked = !h.cb.isChecked
            true
        }
    }
}
