# Cacah Stok – Aplikasi Android

**Download APK (selalu versi terbaru):** https://github.com/leonardogading/cacah-stok/releases/latest/download/CacahStok.apk

Aplikasi cacah stok (stock opname) dengan barcode. Berjalan **offline**, data tersimpan di HP, lalu diexport ke Excel.

## Cara membuat APK

1. Install **Android Studio** (versi terbaru) di komputer.
2. Ekstrak `CacahStok.zip`, lalu di Android Studio pilih **File → Open** dan buka folder `CacahStok`.
3. Tunggu proses *Gradle Sync* selesai (butuh internet saat pertama kali).
4. Menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. APK ada di `app/build/outputs/apk/debug/app-debug.apk`. Salin ke HP lalu install
   (izinkan "Install dari sumber tidak dikenal").

Atau sambungkan HP dengan kabel USB (USB debugging aktif) lalu tekan tombol ▶ Run.

Minimal Android 7.0 (API 24).

## Link download APK otomatis (GitHub)

Proyek ini sudah berisi `.github/workflows/build-apk.yml`. Setelah diunggah ke repository GitHub
(**public**), GitHub otomatis membuat APK dan menerbitkannya di halaman **Releases**.
Link download tetap untuk versi terbaru:

```
https://github.com/<akun>/<nama-repo>/releases/latest/download/CacahStok.apk
```

Setiap ada perubahan kode yang diunggah, APK baru dibuat otomatis dan link tersebut langsung
mengarah ke versi terbaru. APK ditandatangani dengan kunci tetap (`app/cacahstok.jks`), jadi update
bisa langsung dipasang menimpa versi lama tanpa kehilangan data.

## Pengiriman data otomatis ke komputer

Di layar awal tekan tombol hijau **Pengiriman data**, lalu pilih salah satu:

| Pilihan | Hasil | Syarat |
|---|---|---|
| **Hanya di HP** | Data tetap di HP, export Excel manual | – |
| **Kirim otomatis ke Google Sheets** | Setiap Simpan/Edit/Hapus langsung masuk ke satu spreadsheet bersama | HP ada internet |
| **Simpan ke folder bersama jaringan kantor** | File Excel per HP di folder share komputer Windows, diperbarui setiap ada scan | HP di WiFi kantor yang sama |

Isi pengaturannya, tekan **TES KONEKSI** sampai muncul ✔, lalu **SIMPAN**. Data yang sudah ada
di HP langsung ikut terkirim. Status pengiriman terlihat di layar scan (tombol **KIRIM SEKARANG**
untuk mengirim ulang manual). Jika HP sedang offline, aplikasi mencoba lagi otomatis saat
jaringan kembali.

**Hapus semua data** di HP memulai *sesi* baru: data yang sudah terkirim ke Google Sheets / folder
tidak ikut terhapus.

### A. Setup Google Sheets (sekali saja, oleh admin)

1. Buat Google Sheets baru, mis. *Hasil Cacah Stok*. Atur **File → Settings → Time zone** ke
   (GMT+07:00) Jakarta / Bangkok.
2. Menu **Extensions → Apps Script**. Hapus isi `Code.gs`, tempel seluruh isi file
   [`google-apps-script/Code.gs`](google-apps-script/Code.gs), lalu **Save**.
3. **Deploy → New deployment** → ikon roda gigi → **Web app**.
   - *Execute as*: **Me**
   - *Who has access*: **Anyone**
4. Klik **Deploy** → **Authorize access** → pilih akun Google → *Advanced* → *Go to … (unsafe)* →
   **Allow**. Salin **Web app URL** (diawali `https://script.google.com/macros/s/…/exec`).
5. Di setiap HP: **Pengiriman data → Google Sheets** → tempel URL → **TES KONEKSI** → **SIMPAN**.
   Cara mudah membagikan URL ke HP: kirim lewat WhatsApp lalu salin–tempel.

Data masuk ke tab **Cacah Stok** dengan kolom: Tanggal & Jam Scan, Username, Lokasi Scan,
Kode Item, Deskripsi Item, Qty, Satuan, HP, Sesi, ID. Di komputer bisa langsung
**File → Download → Microsoft Excel (.xlsx)**.

Opsional: isi `const TOKEN = 'katarahasia';` di script (lalu **Deploy → Manage deployments →
Edit → Version: New version → Deploy**) dan isi Token yang sama di aplikasi, agar hanya
aplikasi Anda yang bisa mengirim data.

### B. Setup folder bersama di komputer Windows (sekali saja)

1. Buat folder, mis. `C:\CacahStok`.
2. Klik kanan folder → **Properties → Sharing → Advanced Sharing** → centang
   **Share this folder** → **Permissions** → beri **Change** (atau Full Control) untuk user yang
   akan dipakai HP → OK.
3. Catat alamat IP komputer: buka *Command Prompt*, ketik `ipconfig`, lihat **IPv4 Address**
   (mis. `192.168.1.10`). Sebaiknya IP ini dibuat tetap (static / DHCP reservation di router).
4. Pastikan jaringan Windows berprofil **Private** dan **File and printer sharing** aktif
   (Settings → Network & Internet → Advanced sharing settings).
5. Di setiap HP: **Pengiriman data → Folder jaringan kantor**:
   - Alamat folder: `\\192.168.1.10\CacahStok`
   - Username / Password: akun Windows yang diberi izin pada langkah 2
     (akun Microsoft: pakai alamat email & passwordnya; disarankan buat user lokal khusus,
     mis. `cacahstok`)
   - Domain: kosongkan, kecuali komputer kantor memakai domain
   - **TES KONEKSI** → **SIMPAN**

Setiap HP membuat file `CacahStok_<HP>_<sesi>.xlsx` di folder tersebut. Untuk menggabungkan
semua file di Excel: **Data → Get Data → From File → From Folder** → pilih folder → **Combine**.

Catatan: password folder disimpan di HP ini. Gunakan user Windows khusus yang hanya punya akses
ke folder tersebut.

## Alur pemakaian

1. **Layar awal**: isi *Username* dan pilih *Lokasi Scan*.
   Daftar lokasi diambil dari `lokasiscan.xlsx` yang sudah tertanam di aplikasi
   (11 lokasi). Jika daftar lokasi berubah, tekan **Impor file lokasi (.xlsx)**:
   nama lokasi diambil dari kolom A.
2. Tekan **MULAI CACAH**.
3. **Layar scan**:
   - **SCAN**: memindai barcode dengan kamera HP.
   - **Barcode scanner fisik / HP scanner industri** (Zebra, Honeywell, dll.) yang diset
     mode *keyboard / keyboard wedge* dengan akhiran *Enter*: langsung tembak saja,
     kursor otomatis berada di kolom input scanner.
   - Setelah barcode terbaca, muncul **form hasil cacah**: Kode Item, Deskripsi Item,
     **Qty**, dan **Satuan**. Tekan **Simpan**.
   - Jika item yang sama sudah pernah dicacah di lokasi itu, form menampilkan peringatan
     berisi jumlah sebelumnya. Data baru tetap disimpan sebagai baris terpisah.
   - Centang *"Buka kamera lagi setelah simpan"* agar bisa scan beruntun dengan cepat.
4. **HASIL SCAN**: daftar semua hasil cacah (bisa difilter per lokasi dan dicari).
   Ketuk satu baris untuk **edit / hapus**.
   - **EXPORT EXCEL**: simpan file `.xlsx` ke HP (Download, Drive, dll.).
   - **BAGIKAN**: buat file Excel lalu kirim lewat WhatsApp, Email, dll.
   - Menu ⋮ **Hapus semua data**: kosongkan data setelah selesai diexport.

## Isi file Excel hasil export

| Tanggal & Jam Scan | Username | Lokasi Scan | Kode Item | Deskripsi Item | Qty | Satuan |
|---|---|---|---|---|---|---|

- Tanggal tersimpan sebagai tanggal Excel asli (format `dd/mm/yyyy hh:mm:ss`), jadi bisa di-sort dan difilter.
- Qty berupa angka (bisa desimal, misalnya 2,5 KG).
- Kode item tetap teks, jadi nol di depan (mis. `00123`) tidak hilang.
- Baris judul dibekukan dan sudah ada filter otomatis.
- Data yang diexport mengikuti filter yang aktif di layar Hasil Scan (lokasi ini saja / semua lokasi, dan kata pencarian).

## Format barcode yang dikenali

| Isi barcode | Kode Item | Deskripsi Item |
|---|---|---|
| `BRG-001\|KERTAS KRAFT 80GR` | BRG-001 | KERTAS KRAFT 80GR |
| `BRG-001;KERTAS KRAFT 80GR` | BRG-001 | KERTAS KRAFT 80GR |
| Kode dan nama dipisah Tab / baris baru (QR) | baris 1 | baris 2 |
| `BRG-001` saja | BRG-001 | diambil dari master item / riwayat scan, atau diketik manual |

**Master item (opsional)**: jika barcode hanya berisi kode, impor file Excel lewat tombol
*Impor master item* dengan kolom **A = Kode Item, B = Deskripsi, C = Satuan**. Deskripsi dan satuan
kemudian terisi otomatis saat scan. Aplikasi juga "mengingat" item yang pernah disimpan, jadi
scan berikutnya untuk kode yang sama otomatis terisi.

## Struktur kode

```
app/src/main/java/com/cacahstok/app/
  LoginActivity.kt   – layar username & lokasi, impor lokasi / master item
  ScanActivity.kt    – tombol SCAN (kamera), input scanner fisik, daftar scan terakhir
  CountDialog.kt     – form input hasil cacah (baru / edit / hapus)
  ResultActivity.kt  – daftar hasil, cari, filter, export & bagikan Excel
  DbHelper.kt        – database SQLite lokal
  XlsxReader.kt      – baca .xlsx (lokasi & master item)
  XlsxWriter.kt      – tulis .xlsx hasil export
  BarcodeParser.kt   – memecah isi barcode menjadi kode & nama item
  SettingsActivity.kt – pilihan tujuan pengiriman data + tes koneksi
  Sync.kt            – pengiriman otomatis (Google Sheets & folder jaringan SMB)
google-apps-script/Code.gs – script penerima data di Google Sheets
app/src/main/assets/lokasiscan.xlsx – daftar lokasi bawaan
```

Library kamera: `zxing-android-embedded` (tidak membutuhkan Google Play Services, jadi juga
jalan di HP scanner industri). Membaca dan menulis Excel memakai kode sendiri (tanpa Apache POI),
jadi APK tetap kecil.
