package dev.b18print.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Classic Bluetooth RFCOMM / Serial Port Profile transport. The official app's SDK
 * (`BluetoothConnHelper`) uses createRfcommSocketToServiceRecord with the standard SPP UUID.
 */
@SuppressLint("MissingPermission")
class SppTransport(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val log: (String) -> Unit,
) : Transport {

    override val name = "SPP"

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var reader: Thread? = null
    @Volatile private var closing = false

    override suspend fun open(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit) = withContext(Dispatchers.IO) {
        adapter.cancelDiscovery()
        val attempts: List<Pair<String, () -> BluetoothSocket>> = listOf(
            "secure RFCOMM (SPP UUID)" to { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            "insecure RFCOMM (SPP UUID)" to { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            "RFCOMM channel 1 (reflection)" to {
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            },
        )
        var last: Exception? = null
        for ((label, factory) in attempts) {
            try {
                log("SPP: trying $label")
                val s = factory()
                s.connect()
                socket = s
                output = s.outputStream
                log("SPP: connected via $label")
                startReader(s.inputStream, onData, onClosed)
                return@withContext
            } catch (e: Exception) {
                log("SPP: $label failed: ${e.message}")
                last = e
                try { socket?.close() } catch (_: IOException) {}
                socket = null
            }
        }
        throw IOException("SPP connect failed: ${last?.message}", last)
    }

    private fun startReader(input: InputStream, onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit) {
        reader = Thread({
            val buf = ByteArray(1024)
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) onData(buf.copyOf(n))
                }
                onClosed(null)
            } catch (e: IOException) {
                onClosed(if (closing) null else e)
            }
        }, "spp-reader").apply { isDaemon = true; start() }
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val out = output ?: throw IOException("Not connected")
        out.write(bytes)
        out.flush()
    }

    override fun close() {
        closing = true
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        output = null
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
