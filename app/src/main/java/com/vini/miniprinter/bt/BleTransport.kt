package com.vini.miniprinter.bt

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
import android.util.Log
import com.vini.miniprinter.driver.PrinterTransport
import com.vini.miniprinter.driver.TransportKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import kotlin.math.min

/**
 * GATT transport: LE transport, high priority,
 * pick a known write characteristic (ISSC / AE01 / FF02...), negotiate MTU, enable notify.
 */
@SuppressLint("MissingPermission")
class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice,
    private val requestedMtu: Int,
    private val onDisconnected: () -> Unit,
) : PrinterTransport {

    override val kind = TransportKind.BLE

    @Volatile
    override var isOpen = false
        private set

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    private var chunkSize = 20

    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val writeLock = Mutex()

    private val connected = CompletableDeferred<Boolean>()
    private val servicesDiscovered = CompletableDeferred<Boolean>()
    private val mtuChanged = CompletableDeferred<Int>()
    private val descriptorWritten = CompletableDeferred<Boolean>()
    private val gattResults = Channel<Pair<Int, ByteArray?>>(Channel.UNLIMITED)
    @Volatile
    private var probing = false

    @Volatile
    private var pendingWrite: CompletableDeferred<Boolean>? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "state status=$status new=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.complete(true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val wasOpen = isOpen
                isOpen = false
                connected.complete(false)
                servicesDiscovered.complete(false)
                mtuChanged.complete(23)
                descriptorWritten.complete(false)
                pendingWrite?.complete(false)
                g.close()
                if (wasOpen) onDisconnected()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            servicesDiscovered.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            mtuChanged.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (probing) gattResults.trySend(status to null)
            descriptorWritten.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (probing) Log.d(PROBE, "notify ${c.uuid} <- ${value.hex()}")
            incoming.trySend(value.copyOf())
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            gattResults.trySend(status to value.copyOf())
        }

        @Deprecated("API < 33")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            gattResults.trySend(status to c.value?.copyOf())
        }

        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            c.value?.let { incoming.trySend(it.copyOf()) }
        }
    }

    suspend fun open() {
        val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw IOException("connectGatt falhou")
        gatt = g
        try {
            if (withTimeoutOrNull(10_000) { connected.await() } != true) throw IOException("não conectou")
            g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            delay(300)
            g.discoverServices()
            if (withTimeoutOrNull(10_000) { servicesDiscovered.await() } != true) throw IOException("sem serviços GATT")
            selectCharacteristics(g)

            if (g.requestMtu(requestedMtu)) {
                val mtu = withTimeoutOrNull(2_000) { mtuChanged.await() } ?: 23
                chunkSize = (mtu - 3).coerceAtLeast(20)
            }
            Log.d(TAG, "write=${writeChar?.uuid} chunk=$chunkSize")
            isOpen = true
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    private suspend fun selectCharacteristics(g: BluetoothGatt) {
        val all = g.services
            .filterNot { it.uuid in GENERIC_SERVICES }
            .flatMap { it.characteristics }
        all.forEach { Log.d(TAG, "char ${it.uuid} props=0x%02X".format(it.properties)) }

        val writable = all.filter { it.properties and (PROP_WRITE or PROP_WRITE_NR) != 0 }
        val chosen = WRITE_UUIDS.firstNotNullOfOrNull { u -> writable.firstOrNull { it.uuid == u } }
            ?: writable.firstOrNull()
            ?: throw IOException("característica de escrita não encontrada")
        writeChar = chosen
        writeType = if (chosen.properties and PROP_WRITE_NR != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        val notifiable = all.filter { it.properties and (PROP_NOTIFY or PROP_INDICATE) != 0 }
        val notify = NOTIFY_UUIDS.firstNotNullOfOrNull { u -> notifiable.firstOrNull { it.uuid == u } }
            ?: notifiable.firstOrNull { it.service == chosen.service }
            ?: return
        g.setCharacteristicNotification(notify, true)
        val cccd = notify.getDescriptor(CCCD) ?: return
        val value = if (notify.properties and PROP_INDICATE != 0 && notify.properties and PROP_NOTIFY == 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        val started = if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
        if (started) withTimeoutOrNull(2_000) { descriptorWritten.await() }
    }

    /** Non-cancellable so a cancelled job never leaves a half-sent command in the printer. */
    override suspend fun write(data: ByteArray) = withContext(NonCancellable) { writeLock.withLock { writeAll(data) } }

    private suspend fun writeAll(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            if (!isOpen) throw IOException("impressora desconectada")
            val end = min(offset + chunkSize, data.size)
            val part = data.copyOfRange(offset, end)
            var tries = 0
            while (true) {
                val done = CompletableDeferred<Boolean>()
                pendingWrite = done
                if (writeChunk(part)) {
                    withTimeoutOrNull(1_000) { done.await() }
                    break
                }
                if (++tries > 50) throw IOException("falha ao enviar dados (BLE)")
                delay(10)
            }
            offset = end
        }
    }

    private fun writeChunk(bytes: ByteArray): Boolean {
        val g = gatt ?: return false
        val c = writeChar ?: return false
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, bytes, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            c.writeType = writeType
            @Suppress("DEPRECATION")
            c.value = bytes
            @Suppress("DEPRECATION")
            g.writeCharacteristic(c)
        }
    }

    override suspend fun read(timeoutMs: Long): ByteArray? =
        withTimeoutOrNull(timeoutMs) { incoming.receive() }

    override fun drain() {
        while (incoming.tryReceive().isSuccess) Unit
    }

    override fun close() {
        isOpen = false
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
    }

    /**
     * Diagnostic only: dumps the whole GATT table, reads every readable characteristic,
     * subscribes to every notify characteristic and logs (tag PROBE) what each command makes the printer answer.
     */
    suspend fun probe(commands: List<Pair<String, ByteArray>>) = withContext(NonCancellable) {
        val g = gatt ?: return@withContext
        probing = true
        try {
            while (gattResults.tryReceive().isSuccess) Unit
            for (s in g.services) {
                Log.d(PROBE, "service ${s.uuid}")
                for (c in s.characteristics) {
                    Log.d(PROBE, "  char ${c.uuid} props=0x%02X".format(c.properties))
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                        @Suppress("DEPRECATION")
                        if (g.readCharacteristic(c)) {
                            val r = withTimeoutOrNull(1_500) { gattResults.receive() }
                            val v = r?.second
                            Log.d(PROBE, "    read status=${r?.first} ${v?.hex()} '${v?.let { String(it) }}'")
                        }
                    }
                    if (c.properties and (PROP_NOTIFY or PROP_INDICATE) != 0 && c != writeChar) {
                        g.setCharacteristicNotification(c, true)
                        val d = c.getDescriptor(CCCD) ?: continue
                        val started = if (Build.VERSION.SDK_INT >= 33) {
                            g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                        } else {
                            @Suppress("DEPRECATION")
                            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            g.writeDescriptor(d)
                        }
                        if (started) withTimeoutOrNull(1_500) { gattResults.receive() }
                        Log.d(PROBE, "    notify on ($started)")
                    }
                }
            }
            for ((name, cmd) in commands) {
                drain()
                Log.d(PROBE, "send $name -> ${cmd.hex()}")
                write(cmd)
                val end = System.currentTimeMillis() + 800
                while (true) {
                    val left = end - System.currentTimeMillis()
                    if (left <= 0) break
                    val r = read(left) ?: break
                    Log.d(PROBE, "  reply $name <- ${r.hex()} '${String(r)}'")
                }
            }
            Log.d(PROBE, "done")
        } finally {
            probing = false
        }
    }

    companion object {
        private const val TAG = "BLE"
        private const val PROBE = "PROBE"
        private fun ByteArray.hex() = joinToString(" ") { "%02X".format(it) }
        private const val PROP_WRITE = BluetoothGattCharacteristic.PROPERTY_WRITE
        private const val PROP_WRITE_NR = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        private const val PROP_NOTIFY = BluetoothGattCharacteristic.PROPERTY_NOTIFY
        private const val PROP_INDICATE = BluetoothGattCharacteristic.PROPERTY_INDICATE

        private fun uuid16(short: String): UUID = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

        private val CCCD = uuid16("2902")
        private val GENERIC_SERVICES = setOf(uuid16("1800"), uuid16("1801"))

        /** Write characteristics seen on ESC/POS and cat printers, in preference order. */
        private val WRITE_UUIDS = listOf(
            UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3"),
            uuid16("2af1"), uuid16("ae01"), uuid16("ff02"), uuid16("ffe1"),
            uuid16("ab01"), uuid16("ae03"), uuid16("ae10"), uuid16("ffa1"),
        )
        private val NOTIFY_UUIDS = listOf(
            UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616"),
            uuid16("2af0"), uuid16("ae02"), uuid16("ff01"), uuid16("ffe2"), uuid16("ab02"),
        )
    }
}
