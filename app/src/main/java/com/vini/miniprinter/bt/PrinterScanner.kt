package com.vini.miniprinter.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class FoundDevice(val address: String, val name: String?, val rssi: Int?, val bonded: Boolean)

@SuppressLint("MissingPermission")
object PrinterScanner {

    fun bonded(adapter: BluetoothAdapter): List<FoundDevice> =
        adapter.bondedDevices.orEmpty().map { FoundDevice(it.address, it.name, null, true) }

    /** LE scan; emits every advertisement. Cancel the collector to stop. */
    fun scan(adapter: BluetoothAdapter): Flow<FoundDevice> = callbackFlow {
        val scanner = adapter.bluetoothLeScanner ?: run {
            close(IllegalStateException("Bluetooth desligado"))
            return@callbackFlow
        }
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name
                trySend(FoundDevice(result.device.address, name, result.rssi, false))
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("scan falhou ($errorCode)"))
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, cb)
        awaitClose { runCatching { scanner.stopScan(cb) } }
    }
}
