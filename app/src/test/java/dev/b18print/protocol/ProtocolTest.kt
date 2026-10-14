package dev.b18print.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocolTest {

    private fun hex(s: String) = s.split(" ").filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun heartbeatPacketEncoding() {
        // 55 55 DC 01 01 XOR(DC,01,01)=DC AA AA
        assertArrayEquals(hex("55 55 DC 01 01 DC AA AA"), NiimbotPacket(Cmd.HEARTBEAT, byteArrayOf(1)).toBytes())
    }

    @Test
    fun connectPacketHasPrefix() {
        assertArrayEquals(hex("03 55 55 C1 01 01 C1 AA AA"), NiimbotPacket(Cmd.CONNECT).toBytes())
    }

    @Test
    fun parserHandlesSplitAndMergedFrames() {
        val p = PacketStreamParser()
        val a = NiimbotPacket(Rsp.SET_DENSITY, byteArrayOf(1)).toBytes()
        val b = NiimbotPacket(Rsp.PRINT_STATUS, byteArrayOf(0, 1, 100, 100)).toBytes()
        val stream = byteArrayOf(0x00, 0x13) + a + b
        assertEquals(0, p.feed(stream.copyOfRange(0, 5)).size)
        val got = p.feed(stream.copyOfRange(5, stream.size))
        assertEquals(listOf(Rsp.SET_DENSITY, Rsp.PRINT_STATUS), got.map { it.command })
        assertArrayEquals(byteArrayOf(0, 1, 100, 100), got[1].data)
    }

    @Test
    fun parserSkipsCorruptFrame() {
        val p = PacketStreamParser()
        val bad = NiimbotPacket(0x31, byteArrayOf(1)).toBytes().also { it[5] = 0x00 }
        val good = NiimbotPacket(0x33, byteArrayOf(1)).toBytes()
        assertEquals(listOf(0x33), p.feed(bad + good).map { it.command })
    }

    @Test
    fun imageEncodingAndRowPackets() {
        // 96 x 4 image: row 0 empty, rows 1-2 identical (first 10 dots), row 3 has 1 dot at x=95.
        val cols = 96
        val black = BooleanArray(cols * 4)
        for (y in 1..2) for (x in 0 until 10) black[y * cols + x] = true
        black[3 * cols + 95] = true
        val enc = ImageEncoder.encode(MonoImage(cols, 4, black))
        assertEquals(3, enc.rowsData.size)
        assertNull(enc.rowsData[0].bits)
        assertEquals(2, enc.rowsData[1].repeat)

        val pk = ImageEncoder.rowPackets(enc, 96)
        assertEquals(Cmd.EMPTY_ROW, pk[0].command)
        assertArrayEquals(byteArrayOf(0, 0, 1), pk[0].data)

        // 10 dots -> full bitmap row: row u16, counts per 32-dot chunk (10,0,0), repeat 2, 12 bytes data
        assertEquals(Cmd.BITMAP_ROW, pk[1].command)
        val d = pk[1].data
        assertEquals(2 + 3 + 1 + 12, d.size)
        assertArrayEquals(byteArrayOf(0, 1, 10, 0, 0, 2), d.copyOfRange(0, 6))
        assertEquals(0xFF.toByte(), d[6])
        assertEquals(0xC0.toByte(), d[7])

        // single dot -> indexed packet with index 95
        assertEquals(Cmd.BITMAP_ROW_INDEXED, pk[2].command)
        assertArrayEquals(byteArrayOf(0, 3, 0, 0, 1, 1, 0, 95), pk[2].data)
    }

    @Test
    fun pixelCountsTotalModeForWideRows() {
        val bits = ByteArray(48) { 0xFF.toByte() } // 384 dots, head 96 -> doesn't fit split mode
        assertArrayEquals(byteArrayOf(0, (384 and 0xFF).toByte(), 1), ImageEncoder.pixelCounts(bits, 96))
    }

    @Test
    fun repeatIsCappedAt255() {
        val enc = ImageEncoder.encode(MonoImage(8, 600, BooleanArray(8 * 600)))
        assertEquals(listOf(255, 255, 90), enc.rowsData.map { it.repeat })
    }
}
