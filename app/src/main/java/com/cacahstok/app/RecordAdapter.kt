package com.cacahstok.app

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecordAdapter(private val onClick: (CountRecord) -> Unit) :
    RecyclerView.Adapter<RecordAdapter.VH>() {

    private var items: List<CountRecord> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<CountRecord>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvKode: TextView = v.findViewById(R.id.tvKode)
        val tvQty: TextView = v.findViewById(R.id.tvQty)
        val tvDesk: TextView = v.findViewById(R.id.tvDeskripsi)
        val tvMeta: TextView = v.findViewById(R.id.tvMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        h.tvKode.text = r.kode
        h.tvQty.text = "${formatQty(r.qty)} ${r.satuan}"
        h.tvDesk.text = r.deskripsi.ifBlank { "(tanpa deskripsi)" }
        h.tvMeta.text = "${formatDateTime(r.scannedAt)}  •  ${r.username}  •  ${r.lokasi}"
        h.itemView.setOnClickListener { onClick(r) }
    }
}
