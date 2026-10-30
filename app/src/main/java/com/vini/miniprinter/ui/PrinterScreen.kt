package com.vini.miniprinter.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vini.miniprinter.AppViewModel
import com.vini.miniprinter.ConnState
import com.vini.miniprinter.statusLine
import com.vini.miniprinter.TransportMode
import com.vini.miniprinter.bt.FoundDevice
import com.vini.miniprinter.bt.PrinterScanner
import com.vini.miniprinter.image.Pipeline
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private val BT_PERMISSIONS = if (Build.VERSION.SDK_INT >= 31) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

@Composable
fun PrinterScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val printer = vm.printer
    val conn by printer.conn.collectAsStateWithLifecycle()
    val settings by printer.settings.collectAsStateWithLifecycle()
    val adapter = printer.adapter

    fun hasPermissions() = BT_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(hasPermissions()) }
    var enabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    var scanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val found = remember { mutableStateMapOf<String, FoundDevice>() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasPermissions()
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        enabled = adapter?.isEnabled == true
    }

    LaunchedEffect(scanning) {
        if (!scanning || adapter == null) return@LaunchedEffect
        scanError = null
        try {
            withTimeoutOrNull(12_000) {
                PrinterScanner.scan(adapter).collect { d ->
                    if (d.name != null || found[d.address] == null) found[d.address] = d
                }
            }
        } catch (e: Exception) {
            scanError = e.message
        }
        scanning = false
    }

    val canPrint = settings.lastAddress != null || conn is ConnState.Connected

    ScreenScaffold("Impressora", onBack = vm::pop) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(if (conn is ConnState.Connected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (val c = conn) {
                                    is ConnState.Connected -> c.name
                                    is ConnState.Connecting -> c.name
                                    ConnState.Disconnected -> settings.lastName ?: "Nenhuma impressora"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                when (val c = conn) {
                                    is ConnState.Connected -> c.statusLine()
                                    is ConnState.Connecting -> "Conectando…"
                                    ConnState.Disconnected -> "Desconectada"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (conn is ConnState.Connected) {
                            TextButton(onClick = printer::disconnect) {
                                Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sair")
                            }
                        }
                    }
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { printer.print(listOf(Pipeline.testPage(settings.width)), "teste") },
                            enabled = canPrint,
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand.Yellow, contentColor = Brand.Ink),
                        ) {
                            Icon(Icons.Rounded.Print, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Teste")
                        }
                        FilledTonalButton(
                            onClick = { printer.feedPaper() },
                            enabled = canPrint,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(Icons.Rounded.VerticalAlignBottom, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Cuspir papel")
                        }
                    }
                }
            }

            item {
                Panel {
                    when {
                        adapter == null -> Text("Este aparelho não tem Bluetooth.")
                        !granted -> Button(onClick = { permLauncher.launch(BT_PERMISSIONS) }) { Text("Permitir Bluetooth") }
                        !enabled -> Button(onClick = { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) {
                            Text("Ligar Bluetooth")
                        }
                        else -> DeviceList(
                            devices = (found.values.filter { it.name != null } +
                                runCatching { PrinterScanner.bonded(adapter) }.getOrDefault(emptyList()))
                                .distinctBy { it.address }
                                .sortedWith(
                                    compareBy<FoundDevice> { it.address != settings.lastAddress }
                                        .thenByDescending { it.rssi ?: -999 }
                                ),
                            lastAddress = settings.lastAddress,
                            scanning = scanning,
                            scanError = scanError,
                            onScan = { scanning = !scanning },
                        ) { d ->
                            scanning = false
                            printer.connect(d.address, d.name)
                        }
                    }
                }
            }

            item {
                Panel {
                    SectionTitle("Conexão")
                    ChoiceChips(TransportMode.entries, settings.transport, { it.label }) { m ->
                        printer.updateSettings { it.copy(transport = m) }
                    }
                    SectionTitle("Papel")
                    ChoiceChips(listOf(384, 576), settings.width, { "$it pontos" }) { w ->
                        printer.updateSettings { it.copy(width = w) }
                    }
                    LabeledSlider(
                        "Cuspir no final", settings.ejectDots.toFloat(), 0f..800f,
                        { v -> printer.updateSettings { it.copy(ejectDots = v.roundToInt()) } },
                        format = { "${it.roundToInt()} px · ${"%.0f".format(it / 8f)} mm" },
                    )
                    SwitchRow("Avanço rápido (ESC J, nem toda impressora aceita)", settings.fastFeed) { v ->
                        printer.updateSettings { it.copy(fastFeed = v) }
                    }
                    Text(
                        "Se ligar e o papel parar de sair, desligue de novo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LabeledSlider(
                        "Espaço entre cópias", settings.feedDots.toFloat(), 0f..160f,
                        { v -> printer.updateSettings { it.copy(feedDots = v.roundToInt()) } },
                        format = { "${it.roundToInt()} px · ${"%.1f".format(it / 8f)} mm" },
                    )
                    SectionTitle("Velocidade de envio")
                    Text(
                        "Se sair falhado ou com lixo, aumente o atraso ou ligue a sincronização.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LabeledSlider(
                        "Atraso por comando", settings.delayMs.toFloat(), 0f..150f,
                        { v -> printer.updateSettings { it.copy(delayMs = v.roundToInt()) } },
                        format = { "${it.roundToInt()} ms" },
                    )
                    LabeledSlider(
                        "Linhas por comando", settings.rowsPerCommand.toFloat(), 1f..48f,
                        { v -> printer.updateSettings { it.copy(rowsPerCommand = v.roundToInt()) } },
                        format = { it.roundToInt().toString() },
                    )
                    SwitchRow("Sincronizar com status", settings.syncStatus) { v ->
                        printer.updateSettings { it.copy(syncStatus = v) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<FoundDevice>,
    lastAddress: String?,
    scanning: Boolean,
    scanError: String?,
    onScan: () -> Unit,
    onPick: (FoundDevice) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Dispositivos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        TextButton(onClick = onScan) {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (scanning) "Parar" else "Procurar")
        }
    }
    scanError?.let { Text("Erro na busca: $it", color = MaterialTheme.colorScheme.error) }
    if (devices.isEmpty()) {
        Text(
            "Ligue a impressora e toque em Procurar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
    devices.forEachIndexed { i, d ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onPick(d) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(d.name ?: d.address, style = MaterialTheme.typography.bodyLarge)
                Text(
                    buildString {
                        append(d.address)
                        if (d.bonded) append(" · pareado")
                        d.rssi?.let { append(" · $it dBm") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (d.address == lastAddress) Icon(Icons.Rounded.Check, contentDescription = "Última usada", tint = Brand.Ok)
        }
    }
}
