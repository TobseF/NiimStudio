package io.github.tobsef.niimstudio.protocol

/**
 * 1-bit image already in printer orientation: [cols] runs across the print head,
 * [rows] runs along the feed direction. Pixel (x, y) is black when [black] is true.
 */
class MonoImage(val cols: Int, val rows: Int, val black: BooleanArray) {
    init {
        require(black.size == cols * rows)
    }

    fun isBlack(x: Int, y: Int) = black[y * cols + x]
}

/** One line run: either an empty run or repeated identical pixel data. */
data class EncodedRow(val row: Int, val repeat: Int, val bits: ByteArray?, val blackCount: Int)

class EncodedImage(val cols: Int, val rows: Int, val rowsData: List<EncodedRow>)

object ImageEncoder {
    /** The repeat counter in row packets is a single byte. */
    private const val MAX_REPEAT = 255

    fun encode(img: MonoImage): EncodedImage {
        val cols = (img.cols + 7) / 8 * 8
        val bytesPerRow = cols / 8
        val out = mutableListOf<EncodedRow>()
        for (y in 0 until img.rows) {
            val bits = ByteArray(bytesPerRow)
            var count = 0
            for (x in 0 until img.cols) {
                if (img.isBlack(x, y)) {
                    // MSB of the first byte is the first print-head dot.
                    bits[x / 8] = (bits[x / 8].toInt() or (0x80 ushr (x % 8))).toByte()
                    count++
                }
            }
            val row = EncodedRow(y, 1, if (count == 0) null else bits, count)
            val last = out.lastOrNull()
            if (last != null && last.repeat < MAX_REPEAT && sameData(last, row)) {
                out[out.size - 1] = last.copy(repeat = last.repeat + 1)
            } else {
                out += row
            }
        }
        return EncodedImage(cols, img.rows, out)
    }

    private fun sameData(a: EncodedRow, b: EncodedRow): Boolean =
        if (a.bits == null || b.bits == null) a.bits == null && b.bits == null else a.bits.contentEquals(b.bits)

    /**
     * Turns encoded rows into 0x84 (empty), 0x83 (indexed, <= 6 dots) and 0x85 (bitmap) packets.
     */
    fun rowPackets(img: EncodedImage, printheadPixels: Int, useIndexed: Boolean = true): List<NiimbotPacket> =
        img.rowsData.map { r ->
            val bits = r.bits
            when {
                bits == null -> NiimbotPacket(Cmd.EMPTY_ROW, u16(r.row) + byteArrayOf(r.repeat.toByte()))
                useIndexed && r.blackCount <= 6 -> NiimbotPacket(
                    Cmd.BITMAP_ROW_INDEXED,
                    u16(r.row) + pixelCounts(bits, printheadPixels) + byteArrayOf(r.repeat.toByte()) + indexes(bits),
                )
                else -> NiimbotPacket(
                    Cmd.BITMAP_ROW,
                    u16(r.row) + pixelCounts(bits, printheadPixels) + byteArrayOf(r.repeat.toByte()) + bits,
                )
            }
        }

    /**
     * 3-byte "black pixel count" field. If the row fits into three print-head
     * chunks (true for the 96-dot B18 head: 3 x 32 dots), each byte holds the dot count of its chunk.
     * Otherwise the field is [0, total low, total high].
     */
    fun pixelCounts(bits: ByteArray, printheadPixels: Int): ByteArray {
        val chunkSize = printheadPixels / 8 / 3
        val split = chunkSize > 0 && bits.size <= chunkSize * 3
        val parts = IntArray(3)
        var total = 0
        bits.forEachIndexed { i, b ->
            val n = Integer.bitCount(b.toInt() and 0xFF)
            total += n
            if (split) parts[minOf(i / chunkSize, 2)] += n
        }
        return if (split) {
            byteArrayOf(parts[0].toByte(), parts[1].toByte(), parts[2].toByte())
        } else {
            byteArrayOf(0, (total and 0xFF).toByte(), ((total shr 8) and 0xFF).toByte())
        }
    }

    /** Positions of black dots, each as a big-endian u16. */
    fun indexes(bits: ByteArray): ByteArray {
        val out = mutableListOf<Byte>()
        bits.forEachIndexed { i, b ->
            for (bit in 0 until 8) {
                if ((b.toInt() and (0x80 ushr bit)) != 0) out += u16(i * 8 + bit).toList()
            }
        }
        return out.toByteArray()
    }
}
