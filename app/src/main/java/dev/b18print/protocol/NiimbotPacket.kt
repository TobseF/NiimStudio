package dev.b18print.protocol

/**
 * NIIMBOT wire frame:
 *
 *   0x55 0x55 | CMD | LEN | DATA[LEN] | XOR(CMD, LEN, DATA...) | 0xAA 0xAA
 *
 * The same framing is used over classic Bluetooth SPP (what the official app's
 * `NiimbotSppPacket` uses) and over BLE.
 */
class NiimbotPacket(val command: Int, val data: ByteArray = byteArrayOf(1)) {

    val checksum: Int
        get() {
            var c = command xor data.size
            for (b in data) c = c xor (b.toInt() and 0xFF)
            return c and 0xFF
        }

    fun toBytes(): ByteArray {
        val out = ByteArray(7 + data.size)
        out[0] = 0x55; out[1] = 0x55
        out[2] = command.toByte()
        out[3] = data.size.toByte()
        System.arraycopy(data, 0, out, 4, data.size)
        out[4 + data.size] = checksum.toByte()
        out[5 + data.size] = 0xAA.toByte()
        out[6 + data.size] = 0xAA.toByte()
        // The Connect packet is prefixed with 0x03 (seen in official app traffic).
        return if (command == Cmd.CONNECT) byteArrayOf(3) + out else out
    }

    override fun toString(): String = "%02X[%s]".format(command, data.toHex())

    companion object {
        const val HEAD: Int = 0x55
        const val TAIL: Int = 0xAA
    }
}

/** Request command ids (app -> printer). */
object Cmd {
    const val CONNECT = 0xC1
    const val HEARTBEAT = 0xDC
    const val PRINTER_INFO = 0x40
    const val PRINTER_STATUS_DATA = 0xA5
    const val SET_DENSITY = 0x21
    const val SET_LABEL_TYPE = 0x23
    const val PRINT_START = 0x01
    const val PRINT_END = 0xF3
    const val PAGE_START = 0x03
    const val PAGE_END = 0xE3
    const val SET_PAGE_SIZE = 0x13
    const val PRINT_QUANTITY = 0x15
    const val PRINT_STATUS = 0xA3
    const val PRINT_CLEAR = 0x20
    const val CANCEL_PRINT = 0xDA
    const val BITMAP_ROW = 0x85
    const val BITMAP_ROW_INDEXED = 0x83
    const val EMPTY_ROW = 0x84
    const val RFID_INFO = 0x1A
    const val RFID_INFO2 = 0x1C
}

/** Response command ids (printer -> app). */
object Rsp {
    const val NOT_SUPPORTED = 0x00
    const val CONNECT = 0xC2
    const val HEARTBEAT_ADV1 = 0xDD
    const val HEARTBEAT_BASIC = 0xDE
    const val HEARTBEAT_INFO = 0xDF
    const val HEARTBEAT_ADV2 = 0xD9
    const val PRINTER_STATUS_DATA = 0xB5
    const val SET_DENSITY = 0x31
    const val SET_LABEL_TYPE = 0x33
    const val PRINT_START = 0x02
    const val PRINT_END = 0xF4
    const val PAGE_START = 0x04
    const val PAGE_END = 0xE4
    const val SET_PAGE_SIZE = 0x14
    const val PRINT_QUANTITY = 0x16
    const val PRINT_STATUS = 0xB3
    const val PRINT_CLEAR = 0x30
    const val CANCEL_PRINT = 0xD0
    const val PRINT_ERROR = 0xDB
    const val RFID_INFO = 0x1B
    const val RFID_INFO2 = 0x1D

    /** PRINTER_INFO responses are 0x40 + info type. */
    fun printerInfo(type: Int) = 0x40 + type
}

object PrinterInfoType {
    const val DENSITY = 1
    const val LABEL_TYPE = 3
    const val MODEL_ID = 8
    const val SOFTWARE_VERSION = 9
    const val BATTERY = 10
    const val SERIAL = 11
    const val HARDWARE_VERSION = 12
}

object LabelType {
    const val WITH_GAPS = 1
    const val BLACK_MARK = 2
    const val CONTINUOUS = 3
    const val TRANSPARENT = 5
    const val BLACK_MARK_GAP = 10
    const val HEAT_SHRINK_TUBE = 11

    val all = listOf(
        WITH_GAPS to "With gaps",
        CONTINUOUS to "Continuous",
        TRANSPARENT to "Transparent",
        BLACK_MARK to "Black mark",
        BLACK_MARK_GAP to "Black mark gap",
        HEAT_SHRINK_TUBE to "Heat shrink tube",
    )
}

/** Error codes delivered in a 0xDB packet or in the PrintStatus response. */
object PrinterError {
    private val names = mapOf(
        0x01 to "Cover open", 0x02 to "No paper", 0x03 to "Low battery", 0x04 to "Battery exception",
        0x05 to "User cancel", 0x06 to "Data error", 0x07 to "Overheat", 0x08 to "Paper out exception",
        0x09 to "Printer busy", 0x0A to "No print head", 0x0B to "Temperature low", 0x0C to "Print head loose",
        0x0D to "No ribbon", 0x0E to "Wrong ribbon", 0x0F to "Used ribbon", 0x10 to "Wrong paper",
        0x11 to "Set paper fail", 0x12 to "Set print mode fail", 0x13 to "Set density fail",
        0x16 to "Communication exception", 0x32 to "Illegal page", 0x33 to "Illegal ribbon page",
        0x34 to "Receive data timeout", 0x35 to "Non-dedicated ribbon",
    )

    fun name(code: Int) = names[code] ?: "Unknown error"
}

class PrinterException(message: String, val code: Int = -1) : Exception(message)

/**
 * Reassembles frames from a byte stream. SPP delivers arbitrary chunks and BLE
 * notifications may split or merge frames, so we buffer and cut on the length byte.
 */
class PacketStreamParser {
    private var buf = ByteArray(0)

    fun feed(chunk: ByteArray): List<NiimbotPacket> {
        buf += chunk
        val out = mutableListOf<NiimbotPacket>()
        var pos = 0
        while (true) {
            // Find the 0x55 0x55 head.
            while (pos + 1 < buf.size && !(buf.u(pos) == 0x55 && buf.u(pos + 1) == 0x55)) pos++
            if (pos + 4 > buf.size) break
            val len = buf.u(pos + 3)
            val total = 7 + len
            if (pos + total > buf.size) break
            val cmd = buf.u(pos + 2)
            val data = buf.copyOfRange(pos + 4, pos + 4 + len)
            val sum = buf.u(pos + 4 + len)
            val tailOk = buf.u(pos + 5 + len) == 0xAA && buf.u(pos + 6 + len) == 0xAA
            val pkt = NiimbotPacket(cmd, data)
            if (tailOk && pkt.checksum == sum) {
                out += pkt
                pos += total
            } else {
                pos += 1 // bad frame, resync on next head
            }
        }
        buf = buf.copyOfRange(minOf(pos, buf.size), buf.size)
        return out
    }

    fun reset() {
        buf = ByteArray(0)
    }
}

internal fun ByteArray.u(i: Int) = this[i].toInt() and 0xFF

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

fun u16(n: Int): ByteArray = byteArrayOf(((n shr 8) and 0xFF).toByte(), (n and 0xFF).toByte())
