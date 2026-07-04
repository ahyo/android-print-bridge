package id.beesmillah.printbridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.epson.epos2.Epos2CallbackCode
import com.epson.epos2.discovery.DeviceInfo
import com.epson.epos2.discovery.Discovery
import com.epson.epos2.discovery.DiscoveryListener
import com.epson.epos2.discovery.FilterOption
import com.epson.epos2.printer.Printer
import com.epson.epos2.printer.PrinterStatusInfo
import com.epson.epos2.printer.ReceiveListener
import java.util.concurrent.Executors

/**
 * Layar setting (launcher): pilih printer via discovery Epson
 * (LAN/Bluetooth/USB), atau isi target manual, lalu Tes Cetak.
 * Target tersimpan dipakai PrintActivity saat menerima beesprint://print.
 */
class SettingsActivity : AppCompatActivity(), ReceiveListener {

    private val executor = Executors.newSingleThreadExecutor()
    private var discovering = false
    private var testPrinter: Printer? = null

    private lateinit var txtSaved: TextView
    private lateinit var results: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        txtSaved = findViewById(R.id.txt_saved)
        results = findViewById(R.id.results)
        val btnSearch = findViewById<Button>(R.id.btn_search)
        val btnTest = findViewById<Button>(R.id.btn_test)
        val btnManual = findViewById<Button>(R.id.btn_manual)
        val edtManual = findViewById<EditText>(R.id.edt_manual)
        val spnSeries = findViewById<Spinner>(R.id.spn_series)
        val rb58 = findViewById<RadioButton>(R.id.rb_58)
        val rb80 = findViewById<RadioButton>(R.id.rb_80)

        spnSeries.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            PrinterPrefs.SERIES.map { it.first }
        )
        spnSeries.setSelection(PrinterPrefs.seriesIndex(this))
        spnSeries.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                PrinterPrefs.saveSeriesIndex(this@SettingsActivity, pos)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        if (PrinterPrefs.paperWidth(this) == 58) rb58.isChecked = true else rb80.isChecked = true
        rb58.setOnClickListener { PrinterPrefs.savePaperWidth(this, 58) }
        rb80.setOnClickListener { PrinterPrefs.savePaperWidth(this, 80) }

        btnSearch.setOnClickListener {
            if (discovering) stopDiscovery(btnSearch) else startDiscovery(btnSearch)
        }
        btnManual.setOnClickListener {
            val t = edtManual.text.toString().trim()
            if (t.isEmpty()) {
                Toast.makeText(this, "Isi target dulu, mis. TCP:192.168.1.55", Toast.LENGTH_SHORT).show()
            } else {
                PrinterPrefs.save(this, t, null)
                refreshSaved()
                Toast.makeText(this, "Target disimpan.", Toast.LENGTH_SHORT).show()
            }
        }
        btnTest.setOnClickListener { runTestPrint() }

        refreshSaved()
    }

    private fun refreshSaved() {
        val t = PrinterPrefs.target(this)
        val n = PrinterPrefs.deviceName(this)
        txtSaved.text = if (t.isNullOrBlank()) getString(R.string.none)
        else (if (n.isNullOrBlank()) t else "$n\n$t")
    }

    // ── Discovery ──────────────────────────────────────────────────────────

    private fun neededPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun startDiscovery(btn: Button) {
        val missing = neededPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
            return
        }
        results.removeAllViews()
        val filter = FilterOption().apply {
            deviceType = Discovery.TYPE_PRINTER
            portType = Discovery.PORTTYPE_ALL
        }
        try {
            Discovery.start(this, filter, discoveryListener)
            discovering = true
            btn.setText(R.string.stop_search)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memulai pencarian: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopDiscovery(btn: Button? = null) {
        if (!discovering) return
        try { Discovery.stop() } catch (_: Exception) {}
        discovering = false
        (btn ?: findViewById(R.id.btn_search))?.setText(R.string.search_printer)
    }

    private val discoveryListener = DiscoveryListener { deviceInfo: DeviceInfo ->
        runOnUiThread { addResult(deviceInfo) }
    }

    private fun addResult(d: DeviceInfo) {
        val tv = TextView(this)
        tv.text = "${d.deviceName}\n${d.target}"
        tv.setPadding(24, 24, 24, 24)
        tv.setOnClickListener {
            PrinterPrefs.save(this, d.target, d.deviceName)
            refreshSaved()
            stopDiscovery()
            Toast.makeText(this, "Printer dipilih: ${d.deviceName}", Toast.LENGTH_SHORT).show()
        }
        results.addView(tv)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, granted: IntArray) {
        super.onRequestPermissionsResult(code, perms, granted)
        if (code == 1 && granted.all { it == PackageManager.PERMISSION_GRANTED }) {
            startDiscovery(findViewById(R.id.btn_search))
        }
    }

    // ── Tes cetak ──────────────────────────────────────────────────────────

    private fun runTestPrint() {
        val target = PrinterPrefs.target(this)
        if (target.isNullOrBlank()) {
            Toast.makeText(this, R.string.no_printer, Toast.LENGTH_LONG).show()
            return
        }
        val series = PrinterPrefs.series(this)
        val paper = PrinterPrefs.paperWidth(this)
        Toast.makeText(this, R.string.printing, Toast.LENGTH_SHORT).show()
        executor.execute {
            try {
                val p = Printer(series, Printer.MODEL_ANK, this)
                testPrinter = p
                p.setReceiveEventListener(this)
                ReceiptRenderer.render(p, ReceiptRenderer.testPayload(paper))
                p.connect(target, Printer.PARAM_DEFAULT)
                p.sendData(Printer.PARAM_DEFAULT)
            } catch (e: Exception) {
                releaseTestPrinter()
                runOnUiThread {
                    Toast.makeText(this, "Gagal: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onPtrReceive(p: Printer?, code: Int, status: PrinterStatusInfo?, jobId: String?) {
        executor.execute {
            releaseTestPrinter()
            runOnUiThread {
                val msg = if (code == Epos2CallbackCode.CODE_SUCCESS) "Tes cetak berhasil."
                else "Printer menolak (kode $code) — cek kertas/tutup."
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun releaseTestPrinter() {
        val p = testPrinter ?: return
        testPrinter = null
        try { p.disconnect() } catch (_: Exception) {}
        try { p.clearCommandBuffer() } catch (_: Exception) {}
        try { p.setReceiveEventListener(null) } catch (_: Exception) {}
    }

    override fun onStop() {
        stopDiscovery()
        super.onStop()
    }

    override fun onDestroy() {
        executor.execute { releaseTestPrinter() }
        executor.shutdown()
        super.onDestroy()
    }
}
