package com.vini.miniprinter.driver

import com.vini.miniprinter.image.MonoImage
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * @param printerWidth dots per line (384 for 58mm mini printers)
 * @param rowsPerCommand raster rows per GS v 0 command (1 is safest over BLE)
 * @param delayMs pause after each command so the printer buffer does not overflow
 * @param syncStatus send GS r 1 after each command and wait for the reply (or [delayMs] * 4)
 * @param feedDots blank dots fed after a job
 */
data class DriverConfig(
    val printerWidth: Int = 384,
    val rowsPerCommand: Int = 1,
    val delayMs: Long = 30,
    val syncStatus: Boolean = false,
    val feedDots: Int = 80,
    /** Feed with ESC J (motor only, fast) instead of printing blank raster rows (always supported, slow). */
    val fastFeed: Boolean = false,
)

/** ESC/POS raster driver (GS v 0). See docs/PROTOCOL.md. */
class EscPosGsv0Driver(
    private val transport: PrinterTransport,
    private val config: DriverConfig,
) {
    private val bytesPerRow = (config.printerWidth + 7) / 8

    suspend fun initialize() {
        transport.write(ESC_INIT)
        delay(50)
    }

    /** Asks for hardware/firmware/battery voltage; null if the printer does not answer. */
    suspend fun queryInfo(): PrinterInfo? {
        transport.drain()
        transport.write(PrinterInfo.QUERY)
        val reply = StringBuilder()
        while (true) {
            val chunk = transport.read(800) ?: break
            reply.append(String(chunk, Charsets.US_ASCII))
            if (chunk.isNotEmpty() && chunk.last() == 0.toByte()) break
        }
        return PrinterInfo.parse(reply.toString())
    }

    suspend fun print(image: MonoImage, onProgress: (Float) -> Unit = {}) {
        printRows(image.fitWidth(config.printerWidth), config.rowsPerCommand, 0, onProgress)
    }

    suspend fun feed(dots: Int = config.feedDots) {
        if (dots <= 0) return
        if (!config.fastFeed) {
            printRows(MonoImage(config.printerWidth, dots), FEED_ROWS_PER_COMMAND, FEED_MIN_DELAY_MS) {}
            return
        }
        var left = dots
        while (left > 0) {
            val n = min(left, 255)
            transport.write(feedCommand(n))
            left -= n
        }
    }

    private suspend fun printRows(
        img: MonoImage,
        rowsPerCommand: Int,
        minDelayMs: Long,
        onProgress: (Float) -> Unit,
    ) {
        val step = rowsPerCommand.coerceIn(1, 255)
        var y = 0
        while (y < img.height) {
            val rows = min(step, img.height - y)
            transport.write(rasterCommand(bytesPerRow, rows, img.data, y * bytesPerRow))
            pace(minDelayMs)
            y += rows
            onProgress(y.toFloat() / img.height)
        }
    }

    private suspend fun pace(minDelayMs: Long) {
        if (config.syncStatus) {
            transport.drain()
            transport.write(GS_STATUS)
            transport.read(maxOf(config.delayMs * 4, 200))
        } else if (maxOf(config.delayMs, minDelayMs) > 0) {
            delay(maxOf(config.delayMs, minDelayMs))
        }
    }

    companion object {
        val ESC_INIT = byteArrayOf(0x1B, 0x40)
        val GS_STATUS = byteArrayOf(0x1D, 0x72, 0x31)
        // Small blocks so blank feed never outruns the paper motor (a full buffer silently drops rows).
        private const val FEED_ROWS_PER_COMMAND = 4
        private const val FEED_MIN_DELAY_MS = 30L

        /** ESC J n: feed paper n dots. */
        fun feedCommand(dots: Int) = byteArrayOf(0x1B, 0x4A, dots.coerceIn(1, 255).toByte())

        /** GS v 0 m xL xH yL yH d1..dk with m = 0 (normal size). */
        fun rasterCommand(bytesPerRow: Int, rows: Int, src: ByteArray, offset: Int): ByteArray {
            val len = bytesPerRow * rows
            val out = ByteArray(8 + len)
            out[0] = 0x1D
            out[1] = 0x76
            out[2] = 0x30
            out[3] = 0x00
            out[4] = (bytesPerRow and 0xFF).toByte()
            out[5] = (bytesPerRow shr 8).toByte()
            out[6] = (rows and 0xFF).toByte()
            out[7] = (rows shr 8).toByte()
            System.arraycopy(src, offset, out, 8, len)
            return out
        }
    }
}
