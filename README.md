# Beesmillah Print Bridge (Android)

Aplikasi jembatan cetak untuk web app [beesmillah.id](https://beesmillah.id):
menerima perintah cetak dari browser lewat deep link `beesprint://` lalu
mencetak struk **langsung** ke printer Epson (TM-T82X, TM-T88, TM-m30, dst.)
memakai SDK resmi **Epson ePOS2** via **LAN, Bluetooth, atau USB**.

Dengan jembatan ini, sisi web tidak lagi menyentuh printer sama sekali — tidak
ada masalah CORS, mixed content HTTPS→HTTP, maupun sertifikat printer.

## Alur

```
Web (Chrome) ──beesprint://print?data=…──▶ PrintActivity (translucent)
                                              │  SDK ePOS2: connect → sendData
                                              ▼
                                   Printer Epson (LAN/BT/USB)
                                              │
              ◀── redirect success/cancel ────┘  (atau cukup finish → balik ke browser)
```

## Kontrak deep link (dipakai web app)

```
beesprint://print
  ?data=<encodeURIComponent(JSON)>     — wajib
  &success=<url>                        — opsional; dibuka dengan ?bzprint=ok
  &cancel=<url>                         — opsional; dibuka dengan ?bzprint=fail&bzmsg=<pesan>
```

Bila `success`/`cancel` tidak dikirim, aplikasi hanya menampilkan toast lalu
menutup diri — kasir kembali ke halaman web semula tanpa reload.

### Payload JSON `data`

```json
{
  "v": 1,
  "paperWidth": 80,
  "cut": true,
  "drawer": false,
  "receipt": {
    "storeName": "Warung Contoh",
    "invoiceNo": "INV-001",
    "dateStr": "04/07/2026 16.30",
    "customer": "Budi",
    "cashier": "Ani",
    "method": "TUNAI",
    "status": "paid",
    "items": [
      { "name": "Kopi Susu", "qty": 2, "price": 18000, "lineTotal": 36000 }
    ],
    "hasAdjustments": true,
    "subtotal": 36000,
    "discount": 0,
    "adminFee": 0,
    "total": 36000,
    "bayar": 50000,
    "change": 14000,
    "due": 0
  }
}
```

Bentuk `receipt` identik dengan objek `data` yang dipakai `BzPrinter` di
`app/static/js/epos-print.js` (repo web), sehingga layout struk sama persis
dengan jalur ePOS-Print lama. Koneksi printer (IP/BT/USB) TIDAK dikirim dari
web — diatur sekali di aplikasi ini.

## Menyiapkan SDK Epson (wajib sebelum build)

Lisensi Epson tidak mengizinkan menaruh SDK di repo publik, jadi:

1. Unduh **Epson ePOS SDK for Android** dari
   https://download.epson-biz.com/modules/pos/ (pilih ePOS SDK → Android;
   yang benar bernama `ePOS_SDK_Android_vX.Y.Z.zip` — BUKAN paket
   JavaScript, itu untuk browser).
2. Ekstrak, lalu salin dari paket SDK ke proyek ini:
   - `ePOS2.jar` → `app/libs/ePOS2.jar`
   - folder ABI `arm64-v8a/`, `armeabi-v7a/`, `x86/`, `x86_64/` (isi
     `libepos2.so` dkk.) → `app/src/main/jniLibs/<abi>/`

## Build

Buka di Android Studio (sinkron Gradle otomatis), atau:

```sh
./gradlew assembleDebug     # hasil: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # perlu keystore untuk rilis tertanda tangan
```

Kebutuhan: JDK 17, Android SDK 34. `local.properties` dibuat otomatis oleh
Android Studio (`sdk.dir=…`).

## Pemakaian di warung

1. Pasang APK di tablet/HP Android kasir (Android 8.0+).
2. Buka aplikasi → **Cari Printer** (atau isi target manual, mis.
   `TCP:192.168.1.55`, `BT:00:11:22:33:44:55`, `USB:`), pilih seri printer,
   lalu **Tes Cetak**.
3. Di web app beesmillah.id → Setting Toko → tipe printer **Aplikasi Bridge**.
4. Tombol cetak di web akan memanggil aplikasi ini secara otomatis.

## Struktur kode

- `PrintActivity.kt` — penerima `beesprint://print`, translucent, cetak, balik.
- `SettingsActivity.kt` — discovery printer (LAN/BT/USB), target manual, tes cetak.
- `ReceiptRenderer.kt` — payload JSON → perintah ePOS2 (port dari epos-print.js).
- `PrinterPrefs.kt` — penyimpanan target/seri/lebar kertas.
