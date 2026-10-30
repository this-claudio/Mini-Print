package com.vini.miniprinter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import com.vini.miniprinter.bt.BleTransport
import com.vini.miniprinter.bt.SppTransport
import com.vini.miniprinter.driver.DriverConfig
import com.vini.miniprinter.driver.EscPosGsv0Driver
import com.vini.miniprinter.driver.PrinterInfo
import com.vini.miniprinter.driver.PrinterTransport
import com.vini.miniprinter.driver.TransportKind
import com.vini.miniprinter.image.MonoImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

enum class TransportMode(val label: String) { AUTO("Auto"), BLE("BLE"), SPP("Clássico") }

data class PrinterSettings(
    val lastAddress: String? = null,
    val lastName: String? = null,
    val transport: TransportMode = TransportMode.AUTO,
    val width: Int = 384,
    val rowsPerCommand: Int = 1,
    val delayMs: Int = 30,
    val syncStatus: Boolean = false,
    /** Blank dots between pages/copies. */
    val feedDots: Int = 40,
    /** Blank dots after the last page so the print clears the head and exits the slot. */
    val ejectDots: Int = 440,
    /** ESC J feed (fast); false = blank raster rows, for printers that ignore ESC J. */
    val fastFeed: Boolean = false,
) {
    fun driverConfig() = DriverConfig(width, rowsPerCommand, delayMs.toLong(), syncStatus, feedDots, fastFeed)

    fun save(p: SharedPreferences) = p.edit()
        .putString("lastAddress", lastAddress)
        .putString("lastName", lastName)
        .putString("transport", transport.name)
        .putInt("width", width)
        .putInt("rowsPerCommand", rowsPerCommand)
        .putInt("delayMs", delayMs)
        .putBoolean("syncStatus", syncStatus)
        .putInt("feedDots", feedDots)
        .putInt("ejectDots", ejectDots)
        .putBoolean("fastFeed", fastFeed)
        .putInt("version", VERSION)
        .apply()

    companion object {
        private const val VERSION = 7

        fun load(p: SharedPreferences): PrinterSettings {
            val d = PrinterSettings()
            // Older versions saved their defaults; reset the ones whose default changed since.
            val version = p.getInt("version", 1)
            val feedKey = if (version < 2) "-" else "feedDots"
            val ejectKey = if (version < 7) "-" else "ejectDots"
            return PrinterSettings(
                lastAddress = p.getString("lastAddress", null),
                lastName = p.getString("lastName", null),
                transport = runCatching { TransportMode.valueOf(p.getString("transport", null)!!) }
                    .getOrDefault(d.transport),
                width = p.getInt("width", d.width),
                rowsPerCommand = p.getInt("rowsPerCommand", d.rowsPerCommand),
                delayMs = p.getInt("delayMs", d.delayMs),
                syncStatus = p.getBoolean("syncStatus", d.syncStatus),
                feedDots = p.getInt(feedKey, d.feedDots),
                ejectDots = p.getInt(ejectKey, d.ejectDots),
                fastFeed = if (version < 4) d.fastFeed else p.getBoolean("fastFeed", d.fastFeed),
            )
        }
    }
}

sealed interface ConnState {
    data object Disconnected : ConnState
    data class Connecting(val name: String) : ConnState
    data class Connected(val name: String, val kind: TransportKind, val info: PrinterInfo? = null) : ConnState
}

sealed interface JobState {
    data object Idle : JobState
    data class Printing(val label: String, val progress: Float) : JobState
    data class Done(val message: String) : JobState
    data class Failed(val message: String) : JobState
}

@SuppressLint("MissingPermission")
class PrinterManager(private val context: Context, private val scope: CoroutineScope) {

    private val prefs = context.getSharedPreferences("printer", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(PrinterSettings.load(prefs))
    val settings: StateFlow<PrinterSettings> = _settings.asStateFlow()

    private val _conn = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val conn: StateFlow<ConnState> = _conn.asStateFlow()

    private val _job = MutableStateFlow<JobState>(JobState.Idle)
    val job: StateFlow<JobState> = _job.asStateFlow()

    private var transport: PrinterTransport? = null
    private var printJob: Job? = null
    private var pollJob: Job? = null
    private val lock = Mutex()

    val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun updateSettings(change: (PrinterSettings) -> PrinterSettings) {
        val s = change(_settings.value)
        _settings.value = s
        s.save(prefs)
    }

    fun connect(address: String, name: String?) {
        scope.launch {
            lock.withLock {
                runCatching { connectLocked(address, name) }
                    .onFailure { _job.value = JobState.Failed("Falha ao conectar: ${it.message}") }
            }
        }
    }

    fun disconnect() {
        transport?.close()
        transport = null
        _conn.value = ConnState.Disconnected
    }

    fun clearJob() {
        if (_job.value !is JobState.Printing) _job.value = JobState.Idle
    }

    fun reportError(message: String) {
        _job.value = JobState.Failed(message)
    }

    fun cancelPrint() {
        printJob?.cancel()
    }

    /**
     * Queues [pages] for printing; each page is followed by the configured paper feed.
     * [onPrinted] runs only when every page went out (not on cancel or error).
     */
    fun print(pages: List<MonoImage>, label: String, onPrinted: suspend () -> Unit = {}) {
        if (pages.isEmpty()) return
        runJob(label) { driver ->
            pages.forEachIndexed { i, page ->
                driver.print(page) { p ->
                    _job.value = JobState.Printing(label, (i + p) / pages.size)
                }
                driver.feed(if (i == pages.lastIndex) _settings.value.ejectDots else _settings.value.feedDots)
            }
            runCatching { onPrinted() }
        }
    }

    /** Pushes blank paper out ("cuspir papel"). */
    fun feedPaper(dots: Int = _settings.value.ejectDots) {
        runJob("avanço de papel") { it.feed(dots) }
    }

    /** Reads battery voltage (`1E 47 03`) into [conn]; failures are ignored, printing matters more. */
    private suspend fun refreshInfoLocked(t: PrinterTransport) {
        val info = runCatching {
            withTimeout(3_000) { EscPosGsv0Driver(t, _settings.value.driverConfig()).queryInfo() }
        }.getOrNull() ?: return
        (_conn.value as? ConnState.Connected)?.let { _conn.value = it.copy(info = info) }
    }

    private fun startBatteryPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                delay(BATTERY_POLL_MS)
                val t = transport?.takeIf { it.isOpen } ?: break
                // Skip this round when a print holds the lock; never queue behind it.
                if (lock.tryLock()) {
                    try {
                        refreshInfoLocked(t)
                    } finally {
                        lock.unlock()
                    }
                }
            }
        }
    }

    /** Diagnostic: asks the printer every known status/battery query and logs the replies (logcat tag PROBE). */
    fun probe() {
        runJob("diagnóstico") {
            val t = transport as? BleTransport ?: throw IOException("diagnóstico só via BLE")
            t.probe(PROBE_COMMANDS)
        }
    }

    private fun runJob(label: String, body: suspend (EscPosGsv0Driver) -> Unit) {
        printJob = scope.launch {
            lock.withLock {
                _job.value = JobState.Printing(label, 0f)
                var driver: EscPosGsv0Driver? = null
                try {
                    val t = transport?.takeIf { it.isOpen } ?: run {
                        val s = _settings.value
                        val address = s.lastAddress ?: throw IOException("escolha a impressora primeiro")
                        connectLocked(address, s.lastName)
                    }
                    driver = EscPosGsv0Driver(t, _settings.value.driverConfig()).also { it.initialize() }
                    body(driver)
                    _job.value = JobState.Done("Pronto: $label")
                    refreshInfoLocked(t)
                    delay(4_000)
                    if (_job.value is JobState.Done) _job.value = JobState.Idle
                } catch (e: CancellationException) {
                    _job.value = JobState.Failed("Impressão cancelada")
                    // Commands are written atomically, so the stream is clean: push the paper out.
                    withContext(NonCancellable) {
                        runCatching { withTimeout(8_000) { driver?.feed(_settings.value.ejectDots) } }
                    }
                } catch (e: Exception) {
                    _job.value = JobState.Failed("Erro: ${e.message ?: e.javaClass.simpleName}")
                    if (transport?.isOpen != true) disconnect()
                }
            }
        }
    }

    private suspend fun connectLocked(address: String, name: String?): PrinterTransport {
        transport?.close()
        transport = null
        val a = adapter ?: throw IOException("Bluetooth indisponível")
        if (!a.isEnabled) throw IOException("Bluetooth desligado")
        val device = a.getRemoteDevice(address)
        val label = name ?: device.name ?: address
        _conn.value = ConnState.Connecting(label)

        val onLost = {
            transport = null
            _conn.value = ConnState.Disconnected
        }
        val errors = mutableListOf<String>()
        for (kind in transportOrder(device)) {
            try {
                val t: PrinterTransport = when (kind) {
                    TransportKind.BLE ->
                        BleTransport(context, device, _settings.value.width / 8 + 11, onLost).also { it.open() }
                    TransportKind.SPP -> SppTransport(a, device, onLost).also { it.open() }
                }
                transport = t
                _conn.value = ConnState.Connected(label, kind)
                updateSettings { it.copy(lastAddress = address, lastName = label) }
                refreshInfoLocked(t)
                startBatteryPolling()
                return t
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "${kind.label}: ${e.message}"
            }
        }
        _conn.value = ConnState.Disconnected
        throw IOException(errors.joinToString("; "))
    }

    private fun transportOrder(device: BluetoothDevice): List<TransportKind> = when (_settings.value.transport) {
        TransportMode.BLE -> listOf(TransportKind.BLE)
        TransportMode.SPP -> listOf(TransportKind.SPP)
        TransportMode.AUTO -> when (device.type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> listOf(TransportKind.SPP)
            BluetoothDevice.DEVICE_TYPE_LE -> listOf(TransportKind.BLE)
            else -> listOf(TransportKind.BLE, TransportKind.SPP)
        }
    }
}

private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

/** Status/battery queries used by various cheap thermal printer firmwares. */
private val PROBE_COMMANDS = listOf(
    "GS r 1 (papel)" to bytes(0x1D, 0x72, 0x31),
    "GS r 2 (gaveta)" to bytes(0x1D, 0x72, 0x32),
    "DLE EOT 1" to bytes(0x10, 0x04, 0x01),
    "DLE EOT 2" to bytes(0x10, 0x04, 0x02),
    "DLE EOT 3" to bytes(0x10, 0x04, 0x03),
    "DLE EOT 4" to bytes(0x10, 0x04, 0x04),
    "ESC v" to bytes(0x1B, 0x76),
    "GS I 1 (modelo)" to bytes(0x1D, 0x49, 0x01),
    "GS I 2" to bytes(0x1D, 0x49, 0x02),
    "GS I 3 (firmware)" to bytes(0x1D, 0x49, 0x03),
    "GS I 67 (nome)" to bytes(0x1D, 0x49, 0x43),
    "1F 11 08 (bateria Phomemo)" to bytes(0x1F, 0x11, 0x08),
    "1F 11 07 (firmware Phomemo)" to bytes(0x1F, 0x11, 0x07),
    "1F 11 11 (papel Phomemo)" to bytes(0x1F, 0x11, 0x11),
    "1F 11 0E (desligamento Phomemo)" to bytes(0x1F, 0x11, 0x0E),
    "10 FF 50 F1 (bateria)" to bytes(0x10, 0xFF, 0x50, 0xF1),
    "10 FF 40 (status)" to bytes(0x10, 0xFF, 0x40),
    "10 FF 20 F0 (modelo)" to bytes(0x10, 0xFF, 0x20, 0xF0),
    "cat AB (bateria)" to bytes(0x51, 0x78, 0xAB, 0x00, 0x01, 0x00, 0x00, 0x00, 0xFF),
    "cat A3 (status)" to bytes(0x51, 0x78, 0xA3, 0x00, 0x01, 0x00, 0x00, 0x00, 0xFF),
    "1E 47 03 (bateria)" to bytes(0x1E, 0x47, 0x03),
)

private const val BATTERY_POLL_MS = 60_000L

/** "Conectada · BLE · bateria 75%" */
fun ConnState.Connected.statusLine(): String {
    val pct = info?.batteryPercent
    return "Conectada · ${kind.label}" + if (pct != null) " · bateria $pct%" else ""
}
