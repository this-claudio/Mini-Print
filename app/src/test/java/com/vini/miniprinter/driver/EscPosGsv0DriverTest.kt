package com.vini.miniprinter.driver

import com.vini.miniprinter.image.MonoImage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EscPosGsv0DriverTest {

    private class FakeTransport : PrinterTransport {
        override val kind = TransportKind.BLE
        override val isOpen = true
        val writes = mutableListOf<ByteArray>()
        override suspend fun write(data: ByteArray) {
            writes += data
        }
        override suspend fun read(timeoutMs: Long): ByteArray? = byteArrayOf(0)
        override fun drain() = Unit
        override fun close() = Unit
    }

    @Test
    fun rasterHeaderFor384DotsOneRow() {
        val cmd = EscPosGsv0Driver.rasterCommand(48, 1, ByteArray(48) { 0x55 }, 0)
        assertEquals(56, cmd.size)
        assertArrayEquals(byteArrayOf(0x1D, 0x76, 0x30, 0x00, 0x30, 0x00, 0x01, 0x00), cmd.copyOfRange(0, 8))
        assertEquals(0x55.toByte(), cmd[8])
    }

    @Test
    fun printsOneCommandPerRowAndCentersNarrowImage() = runTest {
        val t = FakeTransport()
        val img = MonoImage(8, 3)
        img[0, 0] = true
        EscPosGsv0Driver(t, DriverConfig(printerWidth = 384, rowsPerCommand = 1, delayMs = 0)).print(img)
        assertEquals(3, t.writes.size)
        val first = t.writes[0]
        // 8-dot image centered on 384 dots -> starts at dot 188 = byte 23, bit 4
        assertEquals(0x08.toByte(), first[8 + 23])
        assertEquals(56, first.size)
    }

    @Test
    fun fastFeedSplitsIntoEscJChunks() = runTest {
        val t = FakeTransport()
        EscPosGsv0Driver(t, DriverConfig(fastFeed = true)).feed(400)
        assertEquals(2, t.writes.size)
        assertArrayEquals(byteArrayOf(0x1B, 0x4A, 0xFF.toByte()), t.writes[0])
        assertArrayEquals(byteArrayOf(0x1B, 0x4A, 145.toByte()), t.writes[1])
    }

    @Test
    fun groupsRowsAndSyncsWithStatus() = runTest {
        val t = FakeTransport()
        EscPosGsv0Driver(t, DriverConfig(rowsPerCommand = 24, syncStatus = true)).print(MonoImage(384, 50))
        // 24 + 24 + 2 rows, each followed by GS r 1
        assertEquals(6, t.writes.size)
        assertEquals(24, t.writes[0][6].toInt())
        assertEquals(2, t.writes[4][6].toInt())
        assertArrayEquals(EscPosGsv0Driver.GS_STATUS, t.writes[1])
    }
}
