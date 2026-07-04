package id.beesmillah.printbridge

import android.content.Context
import com.epson.epos2.printer.Printer

/**
 * Penyimpanan printer terpilih. Web app TIDAK menyimpan IP/target printer —
 * satu-satunya sumber kebenaran koneksi printer ada di aplikasi ini.
 */
object PrinterPrefs {
    private const val FILE = "printer_prefs"

    /** Daftar seri yang ditawarkan di setting. Label → konstanta ePOS2. */
    val SERIES: List<Pair<String, Int>> = listOf(
        "TM-T82 / T82X" to Printer.TM_T82,
        "TM-T88 (semua varian)" to Printer.TM_T88,
        "TM-m30" to Printer.TM_M30,
        "TM-T20" to Printer.TM_T20,
        "TM-P20 (portabel)" to Printer.TM_P20,
        "TM-U220 (dot matrix)" to Printer.TM_U220,
    )

    fun target(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("target", null)

    fun deviceName(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("device_name", null)

    fun seriesIndex(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("series_index", 0)

    fun series(ctx: Context): Int = SERIES[seriesIndex(ctx).coerceIn(0, SERIES.size - 1)].second

    fun paperWidth(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("paper_width", 80)

    fun save(ctx: Context, target: String, deviceName: String?) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("target", target)
            .putString("device_name", deviceName)
            .apply()
    }

    fun saveSeriesIndex(ctx: Context, index: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("series_index", index).apply()
    }

    fun savePaperWidth(ctx: Context, width: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("paper_width", width).apply()
    }
}
