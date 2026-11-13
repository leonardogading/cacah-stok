/**
 * Cacah Stok – penerima data dari aplikasi Android.
 *
 * Cara pasang (sekali saja):
 * 1. Buat Google Sheets baru (mis. "Hasil Cacah Stok").
 * 2. Menu Extensions → Apps Script. Hapus isi Code.gs, tempel seluruh isi file ini, Save.
 * 3. Klik Deploy → New deployment → ikon roda gigi → Web app.
 *      Execute as : Me
 *      Who has access : Anyone
 *    Klik Deploy, izinkan akses (Authorize access), salin "Web app URL".
 * 4. Di aplikasi: Pengiriman data → Google Sheets → tempel URL → TES KONEKSI → SIMPAN.
 *
 * Setiap HP mengirim seluruh datanya untuk sesi yang sedang berjalan. Baris lama milik
 * HP & sesi tersebut diganti dengan data terbaru (jadi edit / hapus di HP ikut terbarui).
 */

// Opsional: isi dengan kata rahasia, lalu isi Token yang sama di aplikasi.
const TOKEN = '';

const SHEET_NAME = 'Cacah Stok';
const HEADERS = ['Tanggal & Jam Scan', 'Username', 'Lokasi Scan', 'Kode Item',
                 'Deskripsi Item', 'Qty', 'Satuan', 'HP', 'Sesi', 'ID'];

function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    const req = JSON.parse(e.postData.contents);
    if (TOKEN && req.token !== TOKEN) return out_({ ok: false, error: 'Token salah' });

    const sh = getSheet_();
    if (req.action === 'ping') {
      return out_({ ok: true, sheet: SpreadsheetApp.getActiveSpreadsheet().getName() });
    }
    if (req.action !== 'replace') return out_({ ok: false, error: 'Perintah tidak dikenal' });

    const device = String(req.device || '');
    const batch = String(req.batch || '');
    const n = HEADERS.length;
    const last = sh.getLastRow();

    // Ambil baris lain (HP / sesi lain) yang harus tetap ada
    let keep = [];
    if (last > 1) {
      keep = sh.getRange(2, 1, last - 1, n).getValues()
        .filter(r => !(String(r[7]) === device && String(r[8]) === batch));
    }
    const rows = (req.rows || []).map(r => [
      new Date(r.t), r.user, r.lokasi, r.kode, r.desk, r.qty, r.satuan, device, batch, r.id
    ]);
    const all = keep.concat(rows);

    if (last > 1) sh.getRange(2, 1, last - 1, n).clearContent();
    if (all.length) {
      // Kode item sebagai teks agar nol di depan tidak hilang
      sh.getRange(2, 4, all.length, 1).setNumberFormat('@');
      sh.getRange(2, 1, all.length, n).setValues(all);
      sh.getRange(2, 1, all.length, 1).setNumberFormat('dd/mm/yyyy hh:mm:ss');
    }
    return out_({ ok: true, count: rows.length });
  } catch (err) {
    return out_({ ok: false, error: String(err) });
  } finally {
    lock.releaseLock();
  }
}

function doGet() {
  return ContentService.createTextOutput('Web App Cacah Stok aktif.');
}

function getSheet_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sh = ss.getSheetByName(SHEET_NAME);
  if (!sh) {
    sh = ss.insertSheet(SHEET_NAME);
    sh.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS])
      .setFontWeight('bold').setBackground('#DDEBF7');
    sh.setFrozenRows(1);
    sh.setColumnWidth(1, 150); sh.setColumnWidth(3, 260); sh.setColumnWidth(5, 280);
  }
  return sh;
}

function out_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
