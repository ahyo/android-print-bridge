package id.beesmillah.printbridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.epson.epos2.Epos2CallbackCode
import com.epson.epos2.Epos2Exception
import com.epson.epos2.printer.Printer
import com.epson.epos2.printer.PrinterStatusInfo
import com.epson.epos2.printer.ReceiveListener
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Penerima deep link dari web app:
 *
 *   beesprint://print?data=<JSON ter-encodeURIComponent>
 *                    &success=<url>&cancel=<url>
 *
 * Activity ini tembus pandang: hanya menampilkan dialog "Mencetak…" di atas
 * browser, mencetak lewat SDK ePOS2 (LAN/Bluetooth/USB sesuai target yang
 * disimpan di SettingsActivity), lalu:
 *  - bila success/cancel URL dikirim → redirect balik ke URL itu dengan
 *    tambahan ?bzprint=ok|fail&bzmsg=<pesan>;
 *  - bila tidak → cukup toast + finish (kasir kembali ke halaman web semula
 *    tanpa reload).
 */
class PrintActivity : AppCompatActivity(), ReceiveListener {

    private val executor = Executors.newSingleThreadExecutor()
    private var printer: Printer? = null
    private var dialog: AlertDialog? = null
    private var successUrl: String? = null
    private var cancelUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = intent?.data
        val dataParam = uri?.getQueryParameter("data")
        successUrl = uri?.getQueryParameter("success")
        cancelUrl = uri?.getQueryParameter("cancel")

        if (dataParam.isNullOrBlank()) {
            fail("Data struk kosong atau tautan tidak valid.")
            return
        }

        val target = PrinterPrefs.target(this)
        if (target.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.no_printer), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            redirect(cancelUrl, "fail", "Printer belum diatur di aplikasi bridge.")
            return
        }

        dialog = AlertDialog.Builder(this)
            .setMessage(R.string.printing)
            .setCancelable(false)
            .show()

        val series = PrinterPrefs.series(this)
        executor.execute { doPrint(target, series, dataParam) }
    }

    private fun doPrint(target: String, series: Int, json: String) {
        try {
            val p = Printer(series, Printer.MODEL_ANK, this)
            printer = p
            p.setReceiveEventListener(this)
            ReceiptRenderer.render(p, JSONObject(json))
            p.connect(target, Printer.PARAM_DEFAULT)
            p.sendData(Printer.PARAM_DEFAULT)
            // Hasil datang lewat onPtrReceive.
        } catch (e: Exception) {
            releasePrinter()
            runOnUiThread { fail(describe(e)) }
        }
    }

    // Callback ePOS2 setelah printer merespons hasil cetak.
    override fun onPtrReceive(p: Printer?, code: Int, status: PrinterStatusInfo?, printJobId: String?) {
        // disconnect tidak boleh dipanggil di thread callback SDK.
        executor.execute {
            releasePrinter()
            runOnUiThread {
                if (code == Epos2CallbackCode.CODE_SUCCESS) {
                    Toast.makeText(this, "Tercetak.", Toast.LENGTH_SHORT).show()
                    redirect(successUrl, "ok", null)
                } else {
                    fail(statusMessage(code, status))
                }
            }
        }
    }

    private fun releasePrinter() {
        val p = printer ?: return
        printer = null
        try { p.disconnect() } catch (_: Exception) {}
        try { p.clearCommandBuffer() } catch (_: Exception) {}
        try { p.setReceiveEventListener(null) } catch (_: Exception) {}
    }

    private fun fail(message: String) {
        Toast.makeText(this, "Gagal cetak: $message", Toast.LENGTH_LONG).show()
        redirect(cancelUrl, "fail", message)
    }

    /** Kembali ke web. Tanpa URL → cukup tutup activity (browser masih di
     *  belakang, halaman tidak berubah). */
    private fun redirect(url: String?, status: String, message: String?) {
        dialog?.dismiss()
        dialog = null
        if (!url.isNullOrBlank()) {
            try {
                val b = Uri.parse(url).buildUpon().appendQueryParameter("bzprint", status)
                if (!message.isNullOrBlank()) b.appendQueryParameter("bzmsg", message)
                startActivity(Intent(Intent.ACTION_VIEW, b.build()))
            } catch (_: Exception) {
                // URL balikan rusak — tetap tutup supaya kasir kembali ke browser.
            }
        }
        finish()
    }

    /** Pesan Indonesia yang bisa ditindaklanjuti dari galat koneksi/SDK. */
    private fun describe(e: Exception): String {
        if (e is Epos2Exception) {
            return when (e.errorStatus) {
                Epos2Exception.ERR_CONNECT ->
                    "Tidak bisa terhubung ke printer. Cek printer menyala dan target (${PrinterPrefs.target(this)}) benar."
                Epos2Exception.ERR_TIMEOUT ->
                    "Printer tidak menjawab (timeout)."
                Epos2Exception.ERR_NOT_FOUND ->
                    "Printer tidak ditemukan di jaringan/Bluetooth/USB."
                Epos2Exception.ERR_IN_USE ->
                    "Printer sedang dipakai proses lain."
                else -> "Galat SDK Epson (kode ${e.errorStatus})."
            }
        }
        return e.message ?: e.javaClass.simpleName
    }

    private fun statusMessage(code: Int, s: PrinterStatusInfo?): String {
        if (s != null) {
            if (s.paper == Printer.PAPER_EMPTY) return "Kertas habis."
            if (s.coverOpen == Printer.TRUE) return "Tutup printer terbuka."
            if (s.online == Printer.FALSE) return "Printer offline."
        }
        return "Printer menolak (kode $code)."
    }

    override fun onDestroy() {
        dialog?.dismiss()
        executor.execute { releasePrinter() }
        executor.shutdown()
        super.onDestroy()
    }
}
