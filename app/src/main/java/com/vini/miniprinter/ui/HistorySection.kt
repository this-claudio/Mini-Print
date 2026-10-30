package com.vini.miniprinter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vini.miniprinter.AppViewModel
import com.vini.miniprinter.HistoryEntry
import com.vini.miniprinter.PrintHistory
import com.vini.miniprinter.image.ImageOps.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Recent prints with thumbnail, reprint and delete. Lives under "Cuspir papel" on the home screen. */
@Composable
fun HistorySection(vm: AppViewModel) {
    val entries by vm.history.entries.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text("Histórico", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (entries.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Limpar") }
    }
    if (entries.isEmpty()) {
        Text(
            "O que você imprimir aparece aqui para imprimir de novo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    entries.forEach { e ->
        HistoryRow(
            vm.history, e,
            onReprint = { vm.reprint(e) },
            onDelete = { scope.launch { vm.history.delete(e.id) } },
        )
    }
    if (entries.isNotEmpty()) {
        Text(
            "Guarda as últimas ${PrintHistory.MAX_ENTRIES} impressões.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Limpar histórico?") },
            text = { Text("As ${entries.size} impressões salvas serão apagadas.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { vm.history.clear() }
                }) { Text("Limpar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun HistoryRow(history: PrintHistory, e: HistoryEntry, onReprint: () -> Unit, onDelete: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, e.id) {
        value = history.page(e.id, 0)?.let { withContext(Dispatchers.Default) { it.toBitmap().asImageBitmap() } }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
            ) {
                thumb?.let {
                    Image(
                        it, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        filterQuality = FilterQuality.Medium,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    e.label.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    DATE_FORMAT.format(Date(e.id)) + if (e.pageCount > 1) " · ${e.pageCount} pág." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Apagar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onReprint) {
                Icon(Icons.Rounded.Print, contentDescription = "Imprimir de novo")
            }
        }
    }
}

private val DATE_FORMAT = SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR"))
