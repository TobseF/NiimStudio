package dev.b18print

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import dev.b18print.protocol.EncodedImage
import dev.b18print.protocol.ImageEncoder
import dev.b18print.protocol.LabelType
import dev.b18print.protocol.NiimbotClient
import dev.b18print.protocol.PrintJob
import dev.b18print.protocol.PrintOptions
import dev.b18print.protocol.PrintVariant
import dev.b18print.protocol.toHex
import dev.b18print.transport.BleTransport
import dev.b18print.transport.SppTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("MissingPermission", "SetTextI18n")
class MainActivity : Activity() {

    private val ui = MainScope()
    /** Bluetooth work runs here so it outlives button handlers but dies with the activity. */
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var adapter: BluetoothAdapter
    private var client: NiimbotClient? = null
    private var heartbeatJob: Job? = null
    @Volatile private var printing = false

    private data class Found(val device: BluetoothDevice, val name: String, val bonded: Boolean, val ble: Boolean)
    private val found = linkedMapOf<String, Found>()
    private val shown = mutableListOf<Found>()

    private val logLines = ArrayDeque<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private lateinit var status: TextView
    private lateinit var printerInfo: TextView
    private lateinit var transportSpinner: Spinner
    private lateinit var devicesSpinner: Spinner
    private lateinit var showAll: CheckBox
    private lateinit var keepAlive: CheckBox
    private lateinit var text: EditText
    private lateinit var widthMm: EditText
    private lateinit var lengthMm: EditText
    private lateinit var copies: EditText
    private lateinit var border: CheckBox
    private lateinit var density: Spinner
    private lateinit var labelType: Spinner
    private lateinit var variant: Spinner
    private lateinit var rotation: Spinner
    private lateinit var printhead: EditText
    private lateinit var center: CheckBox
    private lateinit var packetDelay: EditText
    private lateinit var indexed: CheckBox
    private lateinit var preview: ImageView
    private lateinit var previewInfo: TextView
    private lateinit var progress: TextView
    private lateinit var logView: TextView
    private lateinit var logRows: CheckBox

    private val rotations = listOf(90, 270, 0, 180)
    private val variants = listOf<PrintVariant?>(null) + PrintVariant.entries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        status = findViewById(R.id.status)
        printerInfo = findViewById(R.id.printerInfo)
        transportSpinner = findViewById(R.id.transport)
        devicesSpinner = findViewById(R.id.devices)
        showAll = findViewById(R.id.showAll)
        keepAlive = findViewById(R.id.keepAlive)
        text = findViewById(R.id.text)
        widthMm = findViewById(R.id.widthMm)
        lengthMm = findViewById(R.id.lengthMm)
        copies = findViewById(R.id.copies)
        border = findViewById(R.id.border)
        density = findViewById(R.id.density)
        labelType = findViewById(R.id.labelType)
        variant = findViewById(R.id.variant)
        rotation = findViewById(R.id.rotation)
        printhead = findViewById(R.id.printhead)
        center = findViewById(R.id.center)
        packetDelay = findViewById(R.id.packetDelay)
        indexed = findViewById(R.id.indexed)
        preview = findViewById(R.id.preview)
        previewInfo = findViewById(R.id.previewInfo)
        progress = findViewById(R.id.progress)
        logView = findViewById(R.id.log)
        logRows = findViewById(R.id.logRows)

        transportSpinner.adapter = simpleAdapter(listOf("Classic SPP", "BLE"))
        density.adapter = simpleAdapter(listOf("1 (light)", "2 (normal)", "3 (dark)", "4", "5"))
        density.setSelection(1)
        labelType.adapter = simpleAdapter(LabelType.all.map { "${it.second} (${it.first})" })
        variant.adapter = simpleAdapter(listOf("Auto (by protocol version)") + PrintVariant.entries.map { it.label })
        rotation.adapter = simpleAdapter(listOf("90° (text along tape, default)", "270°", "0° (no rotation)", "180°"))

        findViewById<Button>(R.id.scan).setOnClickListener { withPermissions { scan() } }
        findViewById<Button>(R.id.connect).setOnClickListener { withPermissions { connect() } }
        findViewById<Button>(R.id.disconnect).setOnClickListener { disconnect() }
        findViewById<Button>(R.id.heartbeat).setOnClickListener { queryStatus() }
        findViewById<Button>(R.id.print).setOnClickListener { print(test = false) }
        findViewById<Button>(R.id.printTest).setOnClickListener { print(test = true) }
        findViewById<Button>(R.id.shareLog).setOnClickListener { shareLog() }
        findViewById<Button>(R.id.clearLog).setOnClickListener { logLines.clear(); logView.text = "" }
        showAll.setOnCheckedChangeListener { _, _ -> refreshDeviceList() }
        logRows.setOnCheckedChangeListener { _, v -> client?.logRows = v }

        val refresh = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = updatePreview()
        }
        listOf(text, widthMm, lengthMm, printhead).forEach { it.addTextChangedListener(refresh) }
        border.setOnCheckedChangeListener { _, _ -> updatePreview() }
        center.setOnCheckedChangeListener { _, _ -> updatePreview() }
        rotation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = updatePreview()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        registerReceiver(discoveryReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        })

        log("App started. Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        updatePreview()
        withPermissions { loadBonded() }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(discoveryReceiver) } catch (_: Exception) {}
        stopScan()
        client?.close()
        io.cancel()
        ui.cancel()
    }

    // ---------------------------------------------------------------- permissions

    private var pendingAfterPermission: (() -> Unit)? = null

    private fun neededPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun withPermissions(action: () -> Unit) {
        val missing = neededPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            if (!adapter.isEnabled) {
                toast("Please enable Bluetooth")
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                return
            }
            action()
        } else {
            pendingAfterPermission = action
            requestPermissions(missing.toTypedArray(), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            pendingAfterPermission?.let { withPermissions(it) }
        } else {
            toast("Bluetooth permissions are required")
            log("Permissions denied: ${permissions.joinToString()}")
        }
        pendingAfterPermission = null
    }

    // ---------------------------------------------------------------- scanning

    private fun loadBonded() {
        for (d in adapter.bondedDevices.orEmpty()) {
            found[d.address] = Found(d, d.name ?: "?", bonded = true, ble = false)
        }
        refreshDeviceList()
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }) ?: return
                    addFound(d, intent.getStringExtra(BluetoothDevice.EXTRA_NAME), ble = false)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> log("Classic discovery finished")
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addFound(result.device, result.scanRecord?.deviceName, ble = true)
        }

        override fun onScanFailed(errorCode: Int) {
            log("BLE scan failed: $errorCode")
        }
    }

    private fun addFound(d: BluetoothDevice, name: String?, ble: Boolean) {
        val prev = found[d.address]
        val n = name ?: d.name ?: prev?.name ?: "?"
        if (prev == null) log("Found ${if (ble) "BLE" else "classic"} device $n (${d.address})")
        found[d.address] = Found(
            d, n,
            bonded = d.bondState == BluetoothDevice.BOND_BONDED,
            ble = ble || prev?.ble == true,
        )
        refreshDeviceList()
    }

    private fun scan() {
        log("Scanning (classic discovery + BLE, 12 s)…")
        loadBonded()
        adapter.cancelDiscovery()
        adapter.startDiscovery()
        try {
            adapter.bluetoothLeScanner?.startScan(bleScanCallback)
        } catch (e: Exception) {
            log("BLE scan not started: ${e.message}")
        }
        ui.launch {
            delay(12_000)
            stopScan()
            log("Scan finished, ${found.size} devices known")
        }
    }

    private fun stopScan() {
        try {
            adapter.cancelDiscovery()
            adapter.bluetoothLeScanner?.stopScan(bleScanCallback)
        } catch (_: Exception) {}
    }

    private fun isPrinterName(n: String) = n.uppercase().startsWith("B18")

    private fun refreshDeviceList() {
        val selected = shown.getOrNull(devicesSpinner.selectedItemPosition)?.device?.address
        shown.clear()
        shown += found.values.filter { showAll.isChecked || isPrinterName(it.name) }
            .sortedByDescending { isPrinterName(it.name) }
        val labels = shown.map {
            buildString {
                append(it.name).append("  ").append(it.device.address)
                if (it.bonded) append("  [paired]")
                if (it.ble) append("  [BLE]")
            }
        }.ifEmpty { listOf(if (showAll.isChecked) "No devices — tap Scan" else "No B18 found — tap Scan or tick 'All devices'") }
        devicesSpinner.adapter = simpleAdapter(labels)
        shown.indexOfFirst { it.device.address == selected }.takeIf { it >= 0 }?.let { devicesSpinner.setSelection(it) }
    }

    // ---------------------------------------------------------------- connection

    private fun connect() {
        val f = shown.getOrNull(devicesSpinner.selectedItemPosition) ?: return toast("Select a device first")
        disconnect()
        stopScan()
        val useBle = transportSpinner.selectedItemPosition == 1
        val transport = if (useBle) BleTransport(this, f.device, ::log) else SppTransport(adapter, f.device, ::log)
        val c = NiimbotClient(transport, ::log)
        c.logRows = logRows.isChecked
        client = c
        setStatus("Connecting to ${f.name} via ${transport.name}…")
        io.launch {
            try {
                c.connect { err ->
                    log("Connection closed${err?.let { ": ${it.message}" } ?: ""}")
                    if (client === c) ui.launch { setStatus("Disconnected"); heartbeatJob?.cancel() }
                }
                setStatus("Connected (${transport.name}), handshaking…")
                val info = c.negotiate()
                log("Printer: $info")
                ui.launch {
                    printerInfo.text = info.toString()
                    if (info.modelId != null && !info.isB18) toast("Warning: model id ${info.modelId} is not a B18")
                }
                setStatus("Connected to ${f.name} via ${transport.name}")
                startHeartbeat(c)
            } catch (e: Exception) {
                log("Connect failed: ${e.message}")
                setStatus("Connect failed: ${e.message}")
                c.close()
                if (client === c) client = null
            }
        }
    }

    private fun disconnect() {
        heartbeatJob?.cancel()
        client?.let {
            log("Disconnecting")
            it.close()
        }
        client = null
        setStatus("Not connected")
    }

    private fun startHeartbeat(c: NiimbotClient) {
        heartbeatJob?.cancel()
        heartbeatJob = io.launch {
            var failures = 0
            while (isActive) {
                delay(5_000)
                if (!keepAlive.isChecked || printing || client !== c) continue
                try {
                    c.heartbeat()
                    failures = 0
                } catch (e: Exception) {
                    if (++failures <= 3) log("Heartbeat failed: ${e.message}")
                }
            }
        }
    }

    private fun queryStatus() {
        val c = client ?: return toast("Not connected")
        io.launch {
            try {
                val hb = c.heartbeat()
                log("Heartbeat ${"%02X".format(hb.command)}: ${hb.data.toHex()}")
                val info = c.negotiate()
                log("Printer: $info")
                ui.launch { printerInfo.text = info.toString() }
            } catch (e: Exception) {
                log("Status failed: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------------- printing

    private fun num(e: EditText, def: Float) = e.text.toString().replace(',', '.').toFloatOrNull() ?: def

    private fun printheadDots() = num(printhead, 96f).toInt().coerceIn(8, 2048)

    private fun rotationDeg() = rotations[rotation.selectedItemPosition.coerceAtLeast(0)]

    private fun buildImage(test: Boolean): Pair<EncodedImage, Bitmap> {
        val w = num(widthMm, 12f).coerceIn(1f, 100f)
        val l = num(lengthMm, 40f).coerceIn(1f, 500f)
        val label = if (test) LabelRenderer.renderTestPattern(w, l) else LabelRenderer.renderText(text.text.toString(), w, l, border.isChecked)
        val rot = rotationDeg()
        val mono = LabelRenderer.toMono(LabelRenderer.rotate(label, rot), printheadDots(), center.isChecked)
        // Rotate the preview back so it reads like the label, but it still shows the thresholded data.
        val pv = LabelRenderer.rotate(LabelRenderer.monoPreview(mono, 3), (360 - rot) % 360)
        return ImageEncoder.encode(mono) to pv
    }

    private fun updatePreview() {
        if (!::preview.isInitialized) return
        try {
            val (enc, pv) = buildImage(test = false)
            preview.setImageBitmap(pv)
            previewInfo.text = "Sent as ${enc.cols} dots × ${enc.rows} rows " +
                "(${enc.rowsData.size} row packets). Grey frame = image bounds."
        } catch (e: Exception) {
            previewInfo.text = "Preview error: ${e.message}"
        }
    }

    private fun print(test: Boolean) {
        val c = client ?: return toast("Not connected")
        if (printing) return toast("Already printing")
        val (enc, pv) = buildImage(test)
        preview.setImageBitmap(pv)
        val chosen = variants[variant.selectedItemPosition.coerceAtLeast(0)]
        val v = chosen ?: if (c.info.protocolVersion >= 4) PrintVariant.V4 else PrintVariant.V3
        val opts = PrintOptions(
            density = density.selectedItemPosition + 1,
            labelType = LabelType.all[labelType.selectedItemPosition.coerceAtLeast(0)].first,
            copies = num(copies, 1f).toInt().coerceIn(1, 999),
            variant = v,
            printheadPixels = printheadDots(),
            useIndexedRows = indexed.isChecked,
        )
        c.packetIntervalMs = num(packetDelay, 10f).toLong().coerceIn(0, 500)
        printing = true
        io.launch {
            try {
                PrintJob(c, ::log) { msg -> ui.launch { progress.text = msg } }.print(enc, opts)
                log("Print finished")
            } catch (e: Exception) {
                log("PRINT FAILED: ${e.message}")
                ui.launch { progress.text = "Failed: ${e.message}" }
            } finally {
                printing = false
            }
        }
    }

    // ---------------------------------------------------------------- log / misc

    private fun log(msg: String) {
        val line = "${timeFmt.format(Date())} $msg"
        android.util.Log.d("B18Print", msg)
        ui.launch {
            logLines.addLast(line)
            while (logLines.size > 1500) logLines.removeFirst()
            logView.text = logLines.takeLast(300).joinToString("\n")
        }
    }

    private fun shareLog() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "B18Print log")
            putExtra(Intent.EXTRA_TEXT, logLines.joinToString("\n"))
        }
        startActivity(Intent.createChooser(send, "Share log"))
    }

    private fun setStatus(s: String) {
        ui.launch { status.text = s }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun simpleAdapter(items: List<String>) =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
}
