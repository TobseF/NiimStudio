package dev.b18print.protocol

import dev.b18print.transport.Transport
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

data class PrintStatus(val page: Int, val printProgress: Int, val feedProgress: Int, val error: Int)

data class PrinterInfo(
    var connectResult: Int? = null,
    var protocolVersion: Int = 0,
    var modelId: Int? = null,
    var softwareVersion: String? = null,
    var hardwareVersion: String? = null,
    var serial: String? = null,
    var battery: Int? = null,
) {
    val isB18 get() = modelId == B18_MODEL_ID

    override fun toString() = buildString {
        append("model=").append(modelId?.let { if (it == B18_MODEL_ID) "B18 ($it)" else "$it" } ?: "?")
        append(" proto=v").append(protocolVersion)
        softwareVersion?.let { append(" fw=").append(it) }
        hardwareVersion?.let { append(" hw=").append(it) }
        serial?.let { append(" sn=").append(it) }
        battery?.let { append(" batt=").append(it) }
    }

    companion object {
        /** Model id reported by PrinterInfo(8); listed for B18 in niimbluelib's model table. */
        const val B18_MODEL_ID = 3584
    }
}

/**
 * Request/response layer on top of a [Transport]. One request is in flight at a time;
 * responses are matched by command id.
 */
class NiimbotClient(private val transport: Transport, private val log: (String) -> Unit) {

    private val parser = PacketStreamParser()
    private val incoming = Channel<NiimbotPacket>(Channel.UNLIMITED)
    private val lock = Mutex()

    var info = PrinterInfo()
        private set

    /** Pause between frames; the official SDK paces SPP writes too. */
    var packetIntervalMs = 0L

    var logRows = false

    suspend fun connect(onClosed: (Throwable?) -> Unit) {
        transport.open(
            onData = { bytes ->
                for (p in parser.feed(bytes)) {
                    log("RX ${p.toBytes().toHex()}  ($p)")
                    incoming.trySend(p)
                }
            },
            onClosed = onClosed,
        )
    }

    fun close() {
        transport.close()
        incoming.close()
    }

    /** Sends a packet without waiting for a reply (bitmap rows). */
    suspend fun sendOneWay(p: NiimbotPacket) = lock.withLock { write(p, logRows) }

    private suspend fun write(p: NiimbotPacket, verbose: Boolean = true) {
        val bytes = p.toBytes()
        if (verbose) log("TX ${bytes.toHex()}")
        transport.write(bytes)
        if (packetIntervalMs > 0) delay(packetIntervalMs)
    }

    /**
     * Sends [p] and waits for a response whose command is in [expect].
     * 0xDB (print error) and 0x00 (not supported) end the wait with an exception.
     */
    suspend fun request(p: NiimbotPacket, expect: Set<Int>, timeoutMs: Long = 2_000): NiimbotPacket = lock.withLock {
        // Drop stale packets left over from earlier timeouts.
        while (true) {
            val stale = incoming.tryReceive().getOrNull() ?: break
            log("   (ignored stale ${"%02X".format(stale.command)})")
        }
        write(p)
        try {
            withTimeout(timeoutMs) {
                while (true) {
                    val r = incoming.receive()
                    when {
                        r.command in expect -> return@withTimeout r
                        r.command == Rsp.PRINT_ERROR -> {
                            val code = r.data.firstOrNull()?.toInt()?.and(0xFF) ?: -1
                            throw PrinterException("Printer error $code: ${PrinterError.name(code)}", code)
                        }
                        r.command == Rsp.NOT_SUPPORTED ->
                            throw PrinterException("Command %02X not supported by printer".format(p.command))
                        else -> log("   (unsolicited %02X while waiting for %s)".format(r.command, expect.hex()))
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            }
        } catch (e: TimeoutCancellationException) {
            throw PrinterException("Timeout waiting for %s after %s".format(expect.hex(), p))
        }
    }

    private suspend fun requestBool(p: NiimbotPacket, expect: Int, timeoutMs: Long = 2_000): Boolean {
        val r = request(p, setOf(expect), timeoutMs)
        return r.data.firstOrNull()?.toInt() == 1
    }

    // ---- connection / info ----

    /** Connect handshake (0xC1). Result 3 means "protocol v3+", details via PrinterStatusData. */
    suspend fun negotiate(): PrinterInfo {
        val i = PrinterInfo()
        try {
            val r = request(NiimbotPacket(Cmd.CONNECT), setOf(Rsp.CONNECT), 3_000)
            i.connectResult = r.data.firstOrNull()?.toInt()?.and(0xFF)
            log("Connect result: ${i.connectResult}")
            if (i.connectResult == 2) i.protocolVersion = 1
            if (i.connectResult == 3) {
                val s = request(NiimbotPacket(Cmd.PRINTER_STATUS_DATA), setOf(Rsp.PRINTER_STATUS_DATA))
                if (s.data.size >= 13) {
                    val n = s.data.u(11) * 100 + s.data.u(12)
                    i.protocolVersion = when {
                        n >= 302 -> 5
                        n >= 300 -> 4
                        n >= 204 -> 3
                        else -> 0
                    }
                }
            }
        } catch (e: Exception) {
            log("Connect handshake failed (continuing): ${e.message}")
        }
        i.modelId = infoOrNull(PrinterInfoType.MODEL_ID)?.let {
            when (it.size) {
                0 -> null
                1 -> it.u(0) shl 8
                else -> it.u(0) shl 8 or it.u(1)
            }
        }
        i.softwareVersion = infoOrNull(PrinterInfoType.SOFTWARE_VERSION)?.let(::versionString)
        i.hardwareVersion = infoOrNull(PrinterInfoType.HARDWARE_VERSION)?.let(::versionString)
        i.serial = infoOrNull(PrinterInfoType.SERIAL)?.let { d ->
            if (d.all { it in 0x20..0x7E }) String(d, Charsets.US_ASCII) else d.toHex().replace(" ", "")
        }
        i.battery = infoOrNull(PrinterInfoType.BATTERY)?.firstOrNull()?.toInt()?.and(0xFF)
        info = i
        return i
    }

    private suspend fun infoOrNull(type: Int): ByteArray? = try {
        request(NiimbotPacket(Cmd.PRINTER_INFO, byteArrayOf(type.toByte())), setOf(Rsp.printerInfo(type))).data
    } catch (e: Exception) {
        log("PrinterInfo($type) failed: ${e.message}")
        null
    }

    private fun versionString(d: ByteArray): String =
        if (d.size >= 2) "%d.%02d".format(d.u(0), d.u(1)) else d.toHex()

    suspend fun heartbeat(): NiimbotPacket = request(
        NiimbotPacket(Cmd.HEARTBEAT, byteArrayOf(if (info.protocolVersion >= 3) 4 else 1)),
        setOf(Rsp.HEARTBEAT_ADV1, Rsp.HEARTBEAT_BASIC, Rsp.HEARTBEAT_INFO, Rsp.HEARTBEAT_ADV2),
        1_500,
    )

    // ---- print primitives ----

    suspend fun setDensity(v: Int) = requestBool(NiimbotPacket(Cmd.SET_DENSITY, byteArrayOf(v.toByte())), Rsp.SET_DENSITY)
    suspend fun setLabelType(v: Int) = requestBool(NiimbotPacket(Cmd.SET_LABEL_TYPE, byteArrayOf(v.toByte())), Rsp.SET_LABEL_TYPE)
    suspend fun printStart(data: ByteArray) = requestBool(NiimbotPacket(Cmd.PRINT_START, data), Rsp.PRINT_START)
    suspend fun printClear() = requestBool(NiimbotPacket(Cmd.PRINT_CLEAR), Rsp.PRINT_CLEAR)
    suspend fun pageStart() = requestBool(NiimbotPacket(Cmd.PAGE_START), Rsp.PAGE_START)
    suspend fun setPageSize(data: ByteArray) = requestBool(NiimbotPacket(Cmd.SET_PAGE_SIZE, data), Rsp.SET_PAGE_SIZE)
    suspend fun setQuantity(n: Int) = requestBool(NiimbotPacket(Cmd.PRINT_QUANTITY, u16(n)), Rsp.PRINT_QUANTITY)
    suspend fun printEnd() = requestBool(NiimbotPacket(Cmd.PRINT_END), Rsp.PRINT_END, 3_000)
    suspend fun cancelPrint() = requestBool(NiimbotPacket(Cmd.CANCEL_PRINT), Rsp.CANCEL_PRINT)

    /** PageEnd can be refused (0) while the printer still digests rows; retry a few times. */
    suspend fun pageEnd(): Boolean {
        repeat(10) {
            if (requestBool(NiimbotPacket(Cmd.PAGE_END), Rsp.PAGE_END, 3_000)) return true
            delay(200)
        }
        return false
    }

    suspend fun printStatus(): PrintStatus {
        val r = request(NiimbotPacket(Cmd.PRINT_STATUS), setOf(Rsp.PRINT_STATUS), 3_000)
        val d = r.data
        if (d.size < 4) throw PrinterException("Short PrintStatus: ${d.toHex()}")
        val err = if (d.size >= 10) d.u(6) else 0
        return PrintStatus(d.u(0) shl 8 or d.u(1), d.u(2), d.u(3), err)
    }

    private fun Set<Int>.hex() = joinToString("/") { "%02X".format(it) }
}
