package com.vini.miniprinter.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.vini.miniprinter.driver.PrinterTransport
import com.vini.miniprinter.driver.TransportKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

/** Bluetooth Classic RFCOMM (Serial Port Profile) transport, for dual-mode printers. */
@SuppressLint("MissingPermission")
class SppTransport(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val onDisconnected: () -> Unit,
) : PrinterTransport {

    override val kind = TransportKind.SPP

    @Volatile
    override var isOpen = false
        private set

    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)

    suspend fun open() = withContext(Dispatchers.IO) {
        adapter.cancelDiscovery()
        val s = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (e: IOException) {
            device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        }
        socket = s
        out = s.outputStream
        isOpen = true
        thread(name = "spp-reader", isDaemon = true) {
            val buf = ByteArray(256)
            try {
                val input = s.inputStream
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) incoming.trySend(buf.copyOf(n))
                }
            } catch (_: IOException) {
            }
            if (isOpen) {
                isOpen = false
                onDisconnected()
            }
        }
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        val o = out ?: throw IOException("impressora desconectada")
        o.write(data)
        o.flush()
    }

    override suspend fun read(timeoutMs: Long): ByteArray? =
        withTimeoutOrNull(timeoutMs) { incoming.receive() }

    override fun drain() {
        while (incoming.tryReceive().isSuccess) Unit
    }

    override fun close() {
        isOpen = false
        runCatching { socket?.close() }
        socket = null
        out = null
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
