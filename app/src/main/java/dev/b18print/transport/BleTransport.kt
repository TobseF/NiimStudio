package dev.b18print.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

/**
 * BLE transport. NIIMBOT printers expose a vendor service with one characteristic that
 * supports notify + write-without-response; we pick it the same way niimbluelib does.
 */
@SuppressLint("MissingPermission")
class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice,
    private val log: (String) -> Unit,
) : Transport {

    override val name = "BLE"

    private var gatt: BluetoothGatt? = null
    private var channel: BluetoothGattCharacteristic? = null
    private var mtu = 23
    private val writeLock = Mutex()

    private var onData: (ByteArray) -> Unit = {}
    private var onClosed: (Throwable?) -> Unit = {}

    private var connected = CompletableDeferred<Unit>()
    private var mtuDone = CompletableDeferred<Int>()
    private var servicesDone = CompletableDeferred<Boolean>()
    private var descriptorDone = CompletableDeferred<Int>()
    @Volatile private var writeDone: CompletableDeferred<Int>? = null
    @Volatile private var opened = false

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            log("BLE: state=$newState status=$status")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected.complete(Unit)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val err = IOException("BLE disconnected (status $status)")
                connected.completeExceptionally(err)
                writeDone?.completeExceptionally(err)
                if (opened) onClosed(if (status == 0) null else err)
                g.close()
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            mtuDone.complete(mtu)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            servicesDone.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            descriptorDone.complete(status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writeDone?.complete(status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onData(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            // On API 33+ the 3-arg overload above is called instead.
            if (Build.VERSION.SDK_INT < 33) @Suppress("DEPRECATION") onData(c.value ?: return)
        }
    }

    override suspend fun open(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit) {
        this.onData = onData
        this.onClosed = onClosed
        log("BLE: connecting to ${device.address}")
        val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        gatt = g
        try {
            withTimeout(15_000) { connected.await() }

            g.requestMtu(512)
            mtu = try { withTimeout(5_000) { mtuDone.await() } } catch (e: Exception) { 23 }
            log("BLE: MTU $mtu")

            g.discoverServices()
            if (!withTimeout(15_000) { servicesDone.await() }) throw IOException("Service discovery failed")

            val ch = findChannel(g) ?: throw IOException("No notify+writeNoResponse characteristic found")
            log("BLE: using service ${ch.service.uuid} char ${ch.uuid}")
            channel = ch

            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(CCCD)
            if (cccd != null) {
                val v = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(cccd, v)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = v
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(cccd)
                }
                withTimeout(5_000) { descriptorDone.await() }
            }
            opened = true
        } catch (e: Exception) {
            close()
            throw if (e is IOException) e else IOException("BLE connect failed: ${e.message}", e)
        }
    }

    private fun findChannel(g: BluetoothGatt): BluetoothGattCharacteristic? {
        for (s in g.services) {
            // Skip standard 16-bit SIG services (generic access etc.).
            if (s.uuid.toString().endsWith(SIG_BASE_SUFFIX) && s.uuid.toString().startsWith("0000")) continue
            for (c in s.characteristics) {
                val p = c.properties
                if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 &&
                    p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                ) return c
            }
        }
        return null
    }

    override suspend fun write(bytes: ByteArray) = writeLock.withLock {
        val g = gatt ?: throw IOException("Not connected")
        val ch = channel ?: throw IOException("Not connected")
        val chunk = (mtu - 3).coerceAtLeast(20)
        var off = 0
        while (off < bytes.size) {
            val part = bytes.copyOfRange(off, minOf(off + chunk, bytes.size))
            val done = CompletableDeferred<Int>()
            writeDone = done
            val type = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            val ok = if (Build.VERSION.SDK_INT >= 33) {
                var rc = g.writeCharacteristic(ch, part, type)
                var tries = 0
                while (rc == BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY && tries++ < 50) {
                    delay(10)
                    rc = g.writeCharacteristic(ch, part, type)
                }
                rc == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.writeType = type
                @Suppress("DEPRECATION")
                ch.value = part
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
            if (!ok) throw IOException("BLE write rejected")
            withTimeout(3_000) { done.await() }
            off += part.size
        }
    }

    override fun close() {
        opened = false
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        channel = null
    }

    companion object {
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val SIG_BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"
    }
}
