package com.cacahstok.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Penyimpanan lokal (SQLite). Data tetap ada walau aplikasi ditutup / HP mati. */
class DbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "cacahstok.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE records(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                scanned_at INTEGER NOT NULL,
                username TEXT NOT NULL,
                lokasi TEXT NOT NULL,
                kode TEXT NOT NULL,
                deskripsi TEXT NOT NULL,
                qty REAL NOT NULL,
                satuan TEXT NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_records_kode ON records(kode)")
        db.execSQL("CREATE INDEX idx_records_lokasi ON records(lokasi)")
        db.execSQL("CREATE TABLE items(kode TEXT PRIMARY KEY, deskripsi TEXT NOT NULL, satuan TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---------- Hasil cacah ----------

    fun insert(r: CountRecord): Long = writableDatabase.insert("records", null, r.toValues())

    fun update(r: CountRecord) {
        writableDatabase.update("records", r.toValues(), "id=?", arrayOf(r.id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("records", "id=?", arrayOf(id.toString()))
    }

    fun deleteAll() {
        writableDatabase.delete("records", null, null)
    }

    /** lokasi = null berarti semua lokasi. */
    fun records(lokasi: String? = null, search: String? = null, limit: Int? = null): List<CountRecord> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (lokasi != null) {
            where += "lokasi=?"; args += lokasi
        }
        if (!search.isNullOrBlank()) {
            where += "(kode LIKE ? OR deskripsi LIKE ?)"
            args += "%${search.trim()}%"; args += "%${search.trim()}%"
        }
        val c = readableDatabase.query(
            "records", null,
            if (where.isEmpty()) null else where.joinToString(" AND "),
            if (args.isEmpty()) null else args.toTypedArray(),
            null, null, "scanned_at DESC, id DESC", limit?.toString()
        )
        return c.use { cur ->
            val list = ArrayList<CountRecord>()
            while (cur.moveToNext()) list.add(cur.toRecord())
            list
        }
    }

    fun countRecords(lokasi: String?): Long =
        if (lokasi == null) DatabaseUtils.queryNumEntries(readableDatabase, "records")
        else DatabaseUtils.queryNumEntries(readableDatabase, "records", "lokasi=?", arrayOf(lokasi))

    /** Berapa kali & total qty item ini sudah dicacah di lokasi tsb. */
    fun summary(kode: String, lokasi: String): Pair<Int, Double> {
        readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(qty),0) FROM records WHERE kode=? AND lokasi=?",
            arrayOf(kode, lokasi)
        ).use { c ->
            c.moveToFirst()
            return c.getInt(0) to c.getDouble(1)
        }
    }

    // ---------- Master item ----------

    fun findItem(kode: String): Item? {
        readableDatabase.query("items", null, "kode=?", arrayOf(kode), null, null, null).use { c ->
            if (!c.moveToFirst()) return null
            return Item(
                c.getString(c.getColumnIndexOrThrow("kode")),
                c.getString(c.getColumnIndexOrThrow("deskripsi")),
                c.getString(c.getColumnIndexOrThrow("satuan")),
            )
        }
    }

    /** Tambah / timpa item (dipakai saat impor dan saat user menyimpan hasil cacah). */
    fun upsertItems(items: List<Item>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (it in items) {
                val v = ContentValues().apply {
                    put("kode", it.kode)
                    put("deskripsi", it.deskripsi)
                    put("satuan", it.satuan)
                }
                db.insertWithOnConflict("items", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun itemCount(): Long = DatabaseUtils.queryNumEntries(readableDatabase, "items")

    // ---------- helper ----------

    private fun CountRecord.toValues() = ContentValues().apply {
        put("scanned_at", scannedAt)
        put("username", username)
        put("lokasi", lokasi)
        put("kode", kode)
        put("deskripsi", deskripsi)
        put("qty", qty)
        put("satuan", satuan)
    }

    private fun Cursor.toRecord() = CountRecord(
        id = getLong(getColumnIndexOrThrow("id")),
        scannedAt = getLong(getColumnIndexOrThrow("scanned_at")),
        username = getString(getColumnIndexOrThrow("username")),
        lokasi = getString(getColumnIndexOrThrow("lokasi")),
        kode = getString(getColumnIndexOrThrow("kode")),
        deskripsi = getString(getColumnIndexOrThrow("deskripsi")),
        qty = getDouble(getColumnIndexOrThrow("qty")),
        satuan = getString(getColumnIndexOrThrow("satuan")),
    )
}
