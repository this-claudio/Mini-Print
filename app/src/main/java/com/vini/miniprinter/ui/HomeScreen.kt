package com.vini.miniprinter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vini.miniprinter.AppViewModel
import com.vini.miniprinter.ConnState
import com.vini.miniprinter.statusLine
import com.vini.miniprinter.Screen

@Composable
fun HomeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val conn by vm.printer.conn.collectAsStateWithLifecycle()
    val settings by vm.printer.settings.collectAsStateWithLifecycle()

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) vm.push(Screen.Photo(uris))
    }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) vm.push(Screen.Photo(listOf(Uri.parse(u))))
    }
    fun launchCamera() {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the latest shot
        val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        cameraUri = uri.toString()
        takePhoto.launch(uri)
    }
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.push(Screen.Document(uri, ctx.contentResolver.getType(uri) ?: ""))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
            Text("Mini Printer", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Fotos, documentos e textos na sua impressora térmica",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val connected = conn is ConnState.Connected
        Card(
            onClick = { vm.push(Screen.Printer) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(if (connected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(
                                when (conn) {
                                    is ConnState.Connected -> Brand.Ok
                                    is ConnState.Connecting -> Brand.Yellow
                                    ConnState.Disconnected -> MaterialTheme.colorScheme.outline
                                }
                            )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (val c = conn) {
                                is ConnState.Connected -> c.statusLine()
                                is ConnState.Connecting -> "Conectando…"
                                ConnState.Disconnected ->
                                    if (settings.lastAddress != null) "Conecta sozinha ao imprimir" else "Toque para escolher"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BigTile(
                "Galeria",
                "Fotos com recorte, tom, legenda e polaroid",
                Icons.Rounded.PhotoLibrary,
                highlight = true,
                modifier = Modifier.weight(1f).height(170.dp),
            ) {
                pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            BigTile(
                "Câmera",
                "Tire uma foto e imprima",
                Icons.Rounded.PhotoCamera,
                highlight = true,
                modifier = Modifier.weight(1f).height(170.dp),
            ) { launchCamera() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BigTile("Documento", "PDF ou foto de papel", Icons.Rounded.Description, modifier = Modifier.weight(1f).height(150.dp)) {
                pickDoc.launch(arrayOf("application/pdf", "image/*"))
            }
            BigTile("Texto", "Notas, listas, recados", Icons.Rounded.TextFields, modifier = Modifier.weight(1f).height(150.dp)) {
                vm.push(Screen.Text(""))
            }
        }

        OutlinedButton(
            onClick = { vm.printer.feedPaper() },
            enabled = settings.lastAddress != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Rounded.VerticalAlignBottom, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Cuspir papel")
        }

        HistorySection(vm)
    }
}

@Composable
private fun BigTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) Brand.Ink else MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = if (highlight) Color.White else MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (highlight) 4.dp else 1.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            IconBadge(icon)
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlight) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
