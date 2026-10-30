package com.vini.miniprinter.driver

enum class TransportKind(val label: String) { BLE("BLE"), SPP("Clássico") }

/** Byte pipe to the printer. Implementations: BleTransport, SppTransport. */
interface PrinterTransport {
    val kind: TransportKind
    val isOpen: Boolean

    suspend fun write(data: ByteArray)

    /** Next chunk of bytes sent by the printer, or null on timeout. */
    suspend fun read(timeoutMs: Long): ByteArray?

    /** Discards anything already received. */
    fun drain()

    fun close()
}
