package id.beesmillah.printbridge

import com.epson.epos2.printer.Printer
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

/**
 * Merender payload JSON struk (kontrak dengan web app beesmillah.id, lihat
 * README) menjadi perintah ePOS2. Logika layout (lebar kolom, padLR, banner
 * status) adalah port 1:1 dari app/static/js/epos-print.js di repo web agar
 * hasil cetak identik dengan jalur ePOS-Print lama.
 *
 * Payload: { v, paperWidth: 58|80, cut, drawer, copies, receipt: {
 *   storeName, invoiceNo, dateStr, customer, cashier, method, status,
 *   items: [{name, qty, price, lineTotal}], hasAdjustments,
 *   subtotal, discount, adminFee, total, bayar, change, due } }
 *
 * copies (opsional, default 1, maks 5): jumlah lembar struk per transaksi —
 * setingan "Jumlah Cetak Struk" warung di web. Tiap lembar dipotong sendiri;
 * laci kas hanya dibuka sekali.
 */
object ReceiptRenderer {

    private val nf: NumberFormat = NumberFormat.getInstance(Locale("id", "ID"))

    private fun rp(n: Double): String = "Rp " + nf.format(n.toLong())

    // Font A: 80mm ≈ 48 kolom, 58mm ≈ 32 kolom.
    private fun lineWidth(paperWidth: Int): Int = if (paperWidth == 58) 32 else 48

    /** Baris kiri-kanan dalam satu lebar kolom; bila tidak muat, nilai kanan
     *  turun ke baris berikutnya rata kanan. */
    private fun padLR(leftIn: String, right: String, width: Int): String {
        var left = leftIn
        if (left.length + right.length + 1 <= width) {
            return left + " ".repeat(width - left.length - right.length) + right + "\n"
        }
        val out = StringBuilder()
        while (left.length > width) {
            out.append(left, 0, width).append("\n")
            left = left.substring(width)
        }
        out.append(left).append("\n")
        if (right.isNotEmpty()) {
            out.append(" ".repeat((width - right.length).coerceAtLeast(0))).append(right).append("\n")
        }
        return out.toString()
    }

    private fun qtyStr(q: Double): String =
        if (q == Math.floor(q)) q.toLong().toString() else q.toString()

    /** Isi perintah cetak ke [p] — diulang sebanyak `copies` (1–5). Lempar
     *  Epos2Exception bila buffer gagal. */
    fun render(p: Printer, payload: JSONObject) {
        val copies = payload.optInt("copies", 1).coerceIn(1, 5)
        repeat(copies) { i ->
            renderOne(p, payload, openDrawer = i == 0)
        }
    }

    /** Satu lembar struk. [openDrawer] hanya true di lembar pertama. */
    private fun renderOne(p: Printer, payload: JSONObject, openDrawer: Boolean) {
        val r = payload.optJSONObject("receipt") ?: JSONObject()
        val w = lineWidth(payload.optInt("paperWidth", 80))

        // Header (tengah)
        p.addTextAlign(Printer.ALIGN_CENTER)
        p.addTextSize(2, 2)
        p.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.TRUE, Printer.COLOR_1)
        p.addText((r.optString("storeName").ifEmpty { "Beesmillah POS" }) + "\n")
        p.addTextSize(1, 1)
        r.optString("invoiceNo").takeIf { it.isNotEmpty() }?.let { p.addText(it + "\n") }
        p.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.FALSE, Printer.COLOR_1)
        r.optString("dateStr").takeIf { it.isNotEmpty() }?.let { p.addText(it + "\n") }

        // Banner status (pengganti watermark di mode teks termal)
        val status = r.optString("status").lowercase()
        val stLabel = when {
            status == "paid" -> "LUNAS"
            status == "dp" -> "DP"
            status.isNotEmpty() -> "BELUM BAYAR"
            else -> ""
        }
        if (stLabel.isNotEmpty()) {
            p.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.TRUE, Printer.COLOR_1)
            p.addText("*** $stLabel ***\n")
            p.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.FALSE, Printer.COLOR_1)
        }

        // Info (kiri)
        p.addTextAlign(Printer.ALIGN_LEFT)
        p.addFeedLine(1)
        r.optString("customer").takeIf { it.isNotEmpty() }?.let { p.addText("Pelanggan: $it\n") }
        r.optString("cashier").takeIf { it.isNotEmpty() }?.let { p.addText("Kasir : $it\n") }
        r.optString("method").takeIf { it.isNotEmpty() }?.let { p.addText("Bayar : $it\n") }
        r.optString("status").takeIf { it.isNotEmpty() }?.let { p.addText("Status: $it\n") }
        p.addText("-".repeat(w) + "\n")

        // Item: nama, lalu "qty x harga" kiri & total kanan.
        val items = r.optJSONArray("items")
        if (items != null) {
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                p.addText(it.optString("name").ifEmpty { "-" } + "\n")
                p.addText(
                    padLR(
                        "  " + qtyStr(it.optDouble("qty", 0.0)) + " x " + rp(it.optDouble("price", 0.0)),
                        rp(it.optDouble("lineTotal", 0.0)),
                        w
                    )
                )
            }
        }
        p.addText("-".repeat(w) + "\n")

        // Ringkasan
        if (r.optBoolean("hasAdjustments")) {
            p.addText(padLR("Subtotal", rp(r.optDouble("subtotal", 0.0)), w))
            if (r.optDouble("discount", 0.0) > 0) {
                p.addText(padLR("Diskon", "- " + rp(r.optDouble("discount", 0.0)), w))
            }
            if (r.optDouble("adminFee", 0.0) != 0.0) {
                p.addText(padLR("Biaya Admin", rp(r.optDouble("adminFee", 0.0)), w))
            }
        }
        p.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.TRUE, Printer.COLOR_1)
        p.addText(padLR("TOTAL", rp(r.optDouble("total", 0.0)), w))
        p.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.FALSE, Printer.COLOR_1)
        p.addText(padLR("Bayar", rp(r.optDouble("bayar", 0.0)), w))
        p.addText(padLR("Kembalian", rp(r.optDouble("change", 0.0)), w))
        if (r.optDouble("due", 0.0) > 0) {
            p.addText(padLR("Kurang", rp(r.optDouble("due", 0.0)), w))
        }

        // Footer
        p.addTextAlign(Printer.ALIGN_CENTER)
        p.addFeedLine(1)
        p.addText("Terima kasih atas kunjungan Anda\n")
        p.addText("Supported by beesmillah.id\n")

        p.addFeedLine(2)
        if (openDrawer && payload.optBoolean("drawer")) {
            p.addPulse(Printer.DRAWER_2PIN, Printer.PULSE_100)
        }
        if (payload.optBoolean("cut", true)) {
            p.addCut(Printer.CUT_FEED)
        }
    }

    /** Payload contoh untuk Tes Cetak dari layar setting. */
    fun testPayload(paperWidth: Int): JSONObject {
        val json = """
        {
          "v": 1, "paperWidth": $paperWidth, "cut": true, "drawer": false,
          "receipt": {
            "storeName": "TES PRINTER",
            "dateStr": "${java.text.SimpleDateFormat("dd/MM/yyyy HH.mm.ss", Locale("id", "ID")).format(java.util.Date())}",
            "customer": "Pelanggan Contoh",
            "method": "TUNAI",
            "items": [
              {"name": "Kopi Susu", "qty": 2, "price": 18000, "lineTotal": 36000},
              {"name": "Roti Bakar Cokelat Keju", "qty": 1, "price": 22000, "lineTotal": 22000}
            ],
            "hasAdjustments": true,
            "subtotal": 58000, "discount": 8000, "adminFee": 0,
            "total": 50000, "bayar": 50000, "change": 0, "due": 0
          }
        }
        """.trimIndent()
        return JSONObject(json)
    }
}
