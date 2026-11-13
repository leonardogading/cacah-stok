package com.cacahstok.app

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumSet
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pengiriman otomatis. Setiap kali ada perubahan, seluruh data sesi ini di HP dikirim ulang
 * (snapshot) ke tujuan. Jadi data yang diedit / dihapus di HP ikut terbarui di tujuan,
 * dan bila HP sedang offline, pengiriman dicoba lagi otomatis saat jaringan tersedia.
 */
object SyncManager {
    private const val WORK_NAME = "cacahstok_sync"

    /** Panggil setiap ada data baru / diedit / dihapus. */
    fun markChanged(context: Context) {
        val prefs = Prefs(context)
        prefs.changeSeq = prefs.changeSeq + 1
        request(context)
    }

    /** Jadwalkan pengiriman (berjalan di latar belakang, butuh jaringan). */
    fun request(context: Context) {
        if (Prefs(context).syncMode == SyncMode.NONE) return
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
    }

    /** Teks status untuk ditampilkan di layar. */
    fun statusText(prefs: Prefs): String {
        val mode = prefs.syncMode
        if (mode == SyncMode.NONE) return "Pengiriman otomatis: mati (data hanya di HP)"
        val time = SimpleDateFormat("dd/MM HH:mm", Locale.US)
        val sb = StringBuilder("Tujuan: ${mode.label}\n")
        when {
            prefs.hasPendingChanges && prefs.lastSyncError.isNotEmpty() ->
                sb.append("⚠ Belum terkirim. ${prefs.lastSyncError}\nAkan dicoba lagi otomatis.")
            prefs.hasPendingChanges -> sb.append("⏳ Mengirim / menunggu jaringan…")
            prefs.lastSyncAt > 0 ->
                sb.append("✔ Terkirim ${prefs.lastSyncCount} baris • ${time.format(Date(prefs.lastSyncAt))}")
            else -> sb.append("Belum ada data dikirim")
        }
        return sb.toString()
    }

    /** Kirim sekarang secara langsung (dipakai oleh worker & tombol Tes Koneksi). */
    fun push(context: Context, prefs: Prefs, records: List<CountRecord>) {
        when (prefs.syncMode) {
            SyncMode.SHEETS -> SheetsSync.push(prefs, records)
            SyncMode.FOLDER -> FolderSync.push(prefs, records)
            SyncMode.NONE -> {}
        }
    }

    fun deviceLabel(prefs: Prefs): String =
        "${Build.MODEL.replace(Regex("[^A-Za-z0-9]+"), "")}-${prefs.deviceId}"
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        if (prefs.syncMode == SyncMode.NONE) return@withContext Result.success()
        val seq = prefs.changeSeq
        val records = DbHelper(applicationContext).records()
            .sortedWith(compareBy({ it.scannedAt }, { it.id }))
        try {
            SyncManager.push(applicationContext, prefs, records)
            if (seq > prefs.syncedSeq) prefs.syncedSeq = seq
            prefs.lastSyncAt = System.currentTimeMillis()
            prefs.lastSyncCount = records.size
            prefs.lastSyncError = ""
            Result.success()
        } catch (e: Exception) {
            prefs.lastSyncError = friendlyError(e)
            if (runAttemptCount < 20) Result.retry() else Result.failure()
        }
    }
}

fun friendlyError(e: Throwable): String {
    val msg = e.message ?: e.javaClass.simpleName
    return when {
        msg.contains("STATUS_LOGON_FAILURE") -> "Username / password folder salah"
        msg.contains("STATUS_BAD_NETWORK_NAME") -> "Nama folder share tidak ditemukan"
        msg.contains("STATUS_ACCESS_DENIED") -> "Tidak punya izin menulis ke folder"
        msg.contains("STATUS_SHARING_VIOLATION") -> "File sedang dibuka di komputer (tutup Excel-nya)"
        e is java.net.UnknownHostException -> "Alamat tidak ditemukan / tidak ada internet"
        e is java.net.SocketTimeoutException || e is java.net.ConnectException ->
            "Tidak bisa terhubung (cek WiFi / alamat)"
        else -> msg
    }
}

// ======================= Google Sheets =======================

/**
 * Mengirim data ke Google Apps Script Web App (lihat google-apps-script/Code.gs).
 * Script mengganti baris milik HP & sesi ini dengan data terbaru.
 */
object SheetsSync {

    fun push(prefs: Prefs, records: List<CountRecord>) {
        val rows = JSONArray()
        for (r in records) {
            rows.put(JSONObject().apply {
                put("id", r.id)
                put("t", r.scannedAt)
                put("user", r.username)
                put("lokasi", r.lokasi)
                put("kode", r.kode)
                put("desk", r.deskripsi)
                put("qty", r.qty)
                put("satuan", r.satuan)
            })
        }
        val body = JSONObject().apply {
            put("action", "replace")
            put("token", prefs.sheetsToken)
            put("device", SyncManager.deviceLabel(prefs))
            put("batch", prefs.batchId)
            put("rows", rows)
        }
        call(prefs, body)
    }

    /** Untuk tombol Tes Koneksi. Mengembalikan nama spreadsheet. */
    fun ping(prefs: Prefs): String {
        val res = call(prefs, JSONObject().put("action", "ping").put("token", prefs.sheetsToken))
        return res.optString("sheet", "OK")
    }

    private fun call(prefs: Prefs, body: JSONObject): JSONObject {
        val url = prefs.sheetsUrl.trim()
        require(url.startsWith("https://")) { "URL Web App Google Sheets belum diisi" }
        val text = postJson(url, body.toString())
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IOException("Balasan bukan dari script Cacah Stok. Pastikan Web App di-deploy dengan akses \"Anyone\".")
        }
        if (!json.optBoolean("ok")) throw IOException(json.optString("error", "Ditolak oleh Google Sheets"))
        return json
    }

    /** POST lalu ikuti redirect Google (script.google.com -> googleusercontent.com). */
    private fun postJson(url: String, body: String): String {
        var conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 90_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        var code = conn.responseCode
        var hops = 0
        while (code in 300..399 && hops < 5) {
            val loc = conn.getHeaderField("Location") ?: break
            val next = URL(conn.url, loc)
            conn.disconnect()
            conn = (next.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 90_000
            }
            code = conn.responseCode
            hops++
        }
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw IOException("Google Sheets membalas HTTP $code")
        return text
    }
}

// ======================= Folder jaringan (SMB / Windows share) =======================

/**
 * Menyimpan file Excel ke folder bersama Windows (SMB2/3).
 * Satu file per HP per sesi: CacahStok_<HP>_<sesi>.xlsx, ditimpa setiap ada perubahan.
 */
object FolderSync {

    private data class Target(val host: String, val share: String, val dir: String)

    fun push(prefs: Prefs, records: List<CountRecord>) {
        val name = "CacahStok_${SyncManager.deviceLabel(prefs)}_${prefs.batchId}.xlsx"
        withShare(prefs) { disk, dir ->
            val path = if (dir.isEmpty()) name else "$dir\\$name"
            val f = disk.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE),
            )
            f.use { file -> file.outputStream.use { XlsxWriter.write(records, it) } }
        }
    }

    /** Tes koneksi: masuk ke folder, tulis & hapus file kecil. */
    fun test(prefs: Prefs): String {
        val t = parse(prefs.folderPath)
        withShare(prefs) { disk, dir ->
            val path = if (dir.isEmpty()) ".cacahstok_tes.txt" else "$dir\\.cacahstok_tes.txt"
            val f = disk.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE),
            )
            f.use { file -> file.outputStream.use { it.write("tes".toByteArray()) } }
            disk.rm(path)
        }
        return "\\\\${t.host}\\${t.share}" + if (t.dir.isEmpty()) "" else "\\${t.dir}"
    }

    private fun withShare(prefs: Prefs, block: (DiskShare, String) -> Unit) {
        val t = parse(prefs.folderPath)
        val config = SmbConfig.builder()
            .withSecurityProvider(BCSecurityProvider())
            .withTimeout(30, TimeUnit.SECONDS)
            .build()
        SMBClient(config).use { client ->
            client.connect(t.host).use { conn ->
                val auth = if (prefs.folderUser.isBlank()) AuthenticationContext.anonymous()
                else AuthenticationContext(
                    prefs.folderUser.trim(),
                    prefs.folderPass.toCharArray(),
                    prefs.folderDomain.trim().ifEmpty { null },
                )
                val session = conn.authenticate(auth)
                session.use {
                    (session.connectShare(t.share) as DiskShare).use { disk ->
                        // Buat subfolder bila belum ada
                        if (t.dir.isNotEmpty()) {
                            var cur = ""
                            for (part in t.dir.split("\\")) {
                                cur = if (cur.isEmpty()) part else "$cur\\$part"
                                if (!disk.folderExists(cur)) disk.mkdir(cur)
                            }
                        }
                        block(disk, t.dir)
                    }
                }
            }
        }
    }

    /** Terima \\host\share\sub, //host/share/sub, atau smb://host/share/sub */
    private fun parse(raw: String): Target {
        val s = raw.trim().removePrefix("smb:").replace('\\', '/').trim('/')
        val parts = s.split('/').filter { it.isNotBlank() }
        require(parts.size >= 2) { "Alamat folder belum benar. Contoh: \\\\192.168.1.10\\CacahStok" }
        return Target(parts[0], parts[1], parts.drop(2).joinToString("\\"))
    }
}
