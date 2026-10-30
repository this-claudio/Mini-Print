package com.vini.miniprinter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vini.miniprinter.AppViewModel
import com.vini.miniprinter.Screen
import com.vini.miniprinter.image.DocSource
import com.vini.miniprinter.image.MonoImage
import com.vini.miniprinter.image.Pipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DocumentScreen(vm: AppViewModel, s: Screen.Document) {
    val appCtx = LocalContext.current.applicationContext
    val settings by vm.printer.settings.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    val source by produceState<DocSource?>(null, s.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { DocSource.open(appCtx, s.uri, s.mime) }.onFailure { error = it.message }.getOrNull()
        }
    }
    val opened = source
    DisposableEffect(opened) {
        // Close the instance this effect was created for, not whatever `source` holds at dispose time.
        onDispose { opened?.close() }
    }
    var showSettings by remember { mutableStateOf(false) }

    ScreenScaffold("Documento", onBack = vm::pop, actions = {
        IconToggleButton(checked = showSettings, onCheckedChange = { showSettings = it }) {
            Icon(Icons.Rounded.Tune, contentDescription = "Ajustes")
        }
    }) {
        val src = source
        if (src == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error != null) Text("Não foi possível abrir: $error") else CircularProgressIndicator()
            }
            return@ScreenScaffold
        }
        val width = settings.width
        val contentWidth = width - 2 * s.border.pad

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                AnimatedVisibility(showSettings) {
                    Panel {
                        SwitchRow("Paisagem (letras maiores)", s.landscape) { s.landscape = it }
                        ToneControls(s.tone) { s.tone = it }
                        BorderControls(s.border) { s.border = it }
                    }
                }
            }
            items((0 until src.pageCount).toList()) { i ->
                val page by produceState<Result<MonoImage>?>(null, src, i, s.tone, s.border, s.landscape, contentWidth) {
                    delay(60)
                    value = withContext(Dispatchers.Default) {
                        runCatching { Pipeline.finish(src.render(i, contentWidth, s.landscape), s.tone, s.border) }
                    }
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (src.pageCount > 1) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = s.excluded[i] != true, onCheckedChange = { s.excluded[i] = !it })
                            Text("Página ${i + 1}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    val failure = page?.exceptionOrNull()
                    if (failure != null) {
                        Text(
                            "Erro ao renderizar: ${failure.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        MonoPreview(page?.getOrNull(), Modifier.fillMaxWidth(0.85f).heightIn(min = 120.dp))
                    }
                }
            }
        }
        val selected = (0 until src.pageCount).filter { s.excluded[it] != true }
        PrintRow(
            vm,
            enabled = selected.isNotEmpty(),
            text = if (src.pageCount > 1) "Imprimir ${selected.size} pág." else "Imprimir",
        ) {
            val tone = s.tone
            val border = s.border
            val landscape = s.landscape
            vm.print(if (selected.size == 1) "documento" else "${selected.size} páginas") {
                selected.map { i -> Pipeline.finish(src.render(i, contentWidth, landscape), tone, border) }
            }
        }
    }
}
