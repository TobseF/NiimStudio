package dev.b18print.printer

import android.Manifest
import android.annotation.SuppressLint
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
import android.os.Build
import dev.b18print.data.Store
import dev.b18print.model.TransportKind
import dev.b18print.protocol.EncodedImage
import dev.b18print.protocol.NiimbotClient
import dev.b18print.protocol.PrintJob
import dev.b18print.protocol.PrintOptions
import dev.b18print.protocol.PrintProgress
import dev.b18print.protocol.PrintStage
import dev.b18print.protocol.PrinterInfo
import dev.b18print.transport.BleTransport
import dev.b18print.transport.SppTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FailReason { BLUETOOTH_OFF, NO_PERMISSION, CONNECT_FAILED }

sealed class ConnState {
    data object Disconnected : ConnState()
    data class Connecting(val name: String) : ConnState()
    data class Connected(val name: String, val info: PrinterInfo) : ConnState()
    data class Failed(val reason: FailReason, val detail: String? = null) : ConnState()
}

/** App-wide printer connection, keep-alive and print execution. */
@SuppressLint("MissingPermission")
object PrinterManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectLock = Mutex()

    private val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val _printing = MutableStateFlow<PrintProgress?>(null)
    val printing: StateFlow<PrintProgress?> = _printing

    private var client: NiimbotClient? = null
    private var heartbeatJob: Job? = null
    private var printJob: Job? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    val isConnected get() = _state.value is ConnState.Connected
    val info get() = (state.value as? ConnState.Connected)?.info

    fun log(msg: String) {
        android.util.Log.d("B18Print", msg)
        val line = "${timeFmt.format(Date())} $msg"
        synchronized(this) {
            val l = _log.value
            _log.value = (if (l.size >= 3000) l.drop(500) else l) + line
        }
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    fun hasPermissions(ctx: Context) = requiredPermissions().all {
        ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun adapter(ctx: Context): BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    /** Connects to the device stored in the settings. */
    fun connectSaved(ctx: Context) {
        val s = Store.settings.value
        val addr = s.deviceAddress ?: return
        connect(ctx, addr, s.deviceName ?: addr, s.transport)
    }

    fun connect(ctx: Context, address: String, name: String, kind: TransportKind) {
        scope.launch { connectNow(ctx, address, name, kind) }
    }

    /** Ensures a connection to the saved printer. Returns true when connected. */
    suspend fun ensureConnected(ctx: Context): Boolean {
        if (isConnected) return true
        val s = Store.settings.value
        val addr = s.deviceAddress ?: return false
        connectNow(ctx, addr, s.deviceName ?: addr, s.transport)
        return isConnected
    }

    private suspend fun connectNow(ctx: Context, address: String, name: String, kind: TransportKind) = connectLock.withLock {
        if ((_state.value as? ConnState.Connected)?.name == name && client != null) return@withLock
        closeClient()
        val ad = adapter(ctx)
        if (ad == null || !ad.isEnabled) {
            _state.value = ConnState.Failed(FailReason.BLUETOOTH_OFF)
            return@withLock
        }
        if (!hasPermissions(ctx)) {
            _state.value = ConnState.Failed(FailReason.NO_PERMISSION)
            return@withLock
        }
        val device = ad.getRemoteDevice(address)
        val transport = if (kind == TransportKind.BLE) BleTransport(ctx.applicationContext, device, ::log) else SppTransport(ad, device, ::log)
        val c = NiimbotClient(transport, ::log)
        c.packetIntervalMs = Store.settings.value.packetDelayMs.toLong()
        client = c
        _state.value = ConnState.Connecting(name)
        try {
            c.connect { err ->
                log("Connection closed${err?.let { ": ${it.message}" } ?: ""}")
                if (client === c) {
                    client = null
                    heartbeatJob?.cancel()
                    _state.value = ConnState.Disconnected
                }
            }
            val info = c.negotiate()
            log("Printer: $info")
            _state.value = ConnState.Connected(name, info)
            startHeartbeat(c)
        } catch (e: Exception) {
            log("Connect failed: ${e.message}")
            c.close()
            if (client === c) client = null
            _state.value = ConnState.Failed(FailReason.CONNECT_FAILED, e.message)
        }
    }

    fun disconnect() {
        scope.launch { connectLock.withLock { closeClient() } }
    }

    private fun closeClient() {
        heartbeatJob?.cancel()
        client?.let {
            log("Disconnect")
            it.close()
        }
        client = null
        _state.value = ConnState.Disconnected
    }

    private fun startHeartbeat(c: NiimbotClient) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var failures = 0
            while (isActive) {
                delay(5_000)
                if (!Store.settings.value.keepAlive || _printing.value != null || client !== c) continue
                try {
                    c.heartbeat()
                    failures = 0
                } catch (e: Exception) {
                    if (++failures <= 3) log("Heartbeat failed: ${e.message}")
                }
            }
        }
    }

    suspend fun refreshInfo(): PrinterInfo? {
        val c = client ?: return null
        return try {
            val hb = c.heartbeat()
            log("Heartbeat %02X: %s".format(hb.command, hb.data.joinToString(" ") { "%02X".format(it) }))
            val info = c.negotiate()
            (_state.value as? ConnState.Connected)?.let { _state.value = it.copy(info = info) }
            info
        } catch (e: Exception) {
            log("Status failed: ${e.message}")
            null
        }
    }

    fun print(pages: List<EncodedImage>, opts: PrintOptions, onDone: (Throwable?) -> Unit = {}) {
        val c = client ?: return onDone(IllegalStateException("Not connected"))
        if (printJob?.isActive == true) return onDone(IllegalStateException("A print job is already running"))
        c.packetIntervalMs = Store.settings.value.packetDelayMs.toLong()
        _printing.value = PrintProgress(0, pages.size * opts.copies, PrintStage.STARTING, 0f)
        printJob = scope.launch {
            var error: Throwable? = null
            try {
                PrintJob(c, ::log) { _printing.value = it }.print(pages, opts)
                log("Print finished")
            } catch (e: Throwable) {
                error = e
                log("PRINT FAILED: ${e.message}")
            } finally {
                _printing.value = null
                onDone(error)
            }
        }
    }

    fun cancelPrint() {
        printJob?.cancel()
    }
}

/** Classic discovery + BLE scan, reporting every device once with merged info. */
@SuppressLint("MissingPermission")
class BluetoothScanner(private val ctx: Context, private val onUpdate: (List<FoundDevice>) -> Unit) {
    data class FoundDevice(val address: String, val name: String, val bonded: Boolean, val ble: Boolean) {
        val isB18 get() = name.uppercase().startsWith("B18")
    }

    private val adapter = PrinterManager.adapter(ctx)
    private val found = linkedMapOf<String, FoundDevice>()
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_FOUND) return
            val d: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }) ?: return
            add(d, intent.getStringExtra(BluetoothDevice.EXTRA_NAME), false)
        }
    }

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            add(result.device, result.scanRecord?.deviceName, true)
        }
    }

    private fun add(d: BluetoothDevice, name: String?, ble: Boolean) {
        val prev = found[d.address]
        val n = name ?: d.name ?: prev?.name ?: d.address
        found[d.address] = FoundDevice(d.address, n, d.bondState == BluetoothDevice.BOND_BONDED, ble || prev?.ble == true)
        onUpdate(found.values.sortedWith(compareByDescending<FoundDevice> { it.isB18 }.thenByDescending { it.bonded }))
    }

    fun start() {
        val ad = adapter ?: return
        ad.bondedDevices.orEmpty().forEach { add(it, it.name, false) }
        ctx.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        registered = true
        ad.cancelDiscovery()
        ad.startDiscovery()
        try {
            ad.bluetoothLeScanner?.startScan(bleCallback)
        } catch (e: Exception) {
            PrinterManager.log("BLE scan not started: ${e.message}")
        }
    }

    fun stop() {
        try {
            adapter?.cancelDiscovery()
            adapter?.bluetoothLeScanner?.stopScan(bleCallback)
        } catch (_: Exception) {}
        if (registered) {
            try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
            registered = false
        }
    }
}
