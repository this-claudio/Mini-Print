package com.vini.miniprinter.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Rotate90DegreesCcw
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vini.miniprinter.AppViewModel
import com.vini.miniprinter.Screen
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.OutlinedTextField
import com.vini.miniprinter.image.Caption
import com.vini.miniprinter.image.TextFont
import kotlin.math.roundToInt
import com.vini.miniprinter.image.ImageOps
import com.vini.miniprinter.image.MonoImage
import com.vini.miniprinter.image.NormRect
import com.vini.miniprinter.image.PhotoEdit
import com.vini.miniprinter.image.Pipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val ASPECTS: List<Pair<String, Float?>> = listOf(
    "Livre" to null, "1:1" to 1f, "3:4" to 3f / 4f, "4:3" to 4f / 3f,
    "2:3" to 2f / 3f, "3:2" to 3f / 2f, "9:16" to 9f / 16f,
)

private enum class Tool(val label: String, val icon: ImageVector) {
    CROP("Recortar", Icons.Rounded.Crop),
    ROTATE("Girar", Icons.Rounded.Rotate90DegreesCw),
    TONE("Tom", Icons.Rounded.Tune),
    BORDER("Borda", Icons.Rounded.BorderOuter),
    CAPTION("Legenda", Icons.Rounded.Title),
}

@Composable
fun PhotoEditorScreen(vm: AppViewModel, s: Screen.Photo) {
    val appCtx = LocalContext.current.applicationContext
    val settings by vm.printer.settings.collectAsStateWithLifecycle()
    val uri = s.uris[s.index]
    val edit = s.edits[s.index]
    fun update(f: (PhotoEdit) -> PhotoEdit) {
        s.edits[s.index] = f(s.edits[s.index])
    }

    var loadError by remember(uri) { mutableStateOf<String?>(null) }
    val base by produceState<Bitmap?>(null, uri) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { ImageOps.decode(appCtx, uri) }.onFailure { loadError = it.message }.getOrNull()
        }
    }
    val geom by produceState<Bitmap?>(null, base, edit.totalRotation, edit.mirror) {
        val b = base
        value = if (b == null) null else withContext(Dispatchers.Default) {
            ImageOps.transform(b, edit.totalRotation, edit.mirror)
        }
    }
    val mono by produceState<MonoImage?>(null, geom, edit.crop, edit.tone, edit.border, edit.caption, settings.width) {
        val g = geom ?: return@produceState
        delay(60) // debounce slider drags
        value = withContext(Dispatchers.Default) { Pipeline.photo(g, edit, settings.width) }
    }

    var tool by remember { mutableStateOf<Tool?>(null) }
    var cropping by remember { mutableStateOf(false) }
    var cropBackup by remember { mutableStateOf(NormRect.FULL) }
    var aspectLabel by remember { mutableStateOf("Livre") }
    val aspect = ASPECTS.first { it.first == aspectLabel }.second

    val multi = s.uris.size > 1
    ScreenScaffold(
        title = if (cropping) "Recortar" else if (multi) "Foto ${s.index + 1} de ${s.uris.size}" else "Foto",
        onBack = if (cropping) null else vm::pop,
        actions = {
            if (multi && !cropping) {
                IconButton(onClick = { s.index-- }, enabled = s.index > 0) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Anterior")
                }
                IconButton(onClick = { s.index++ }, enabled = s.index < s.uris.lastIndex) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Próxima")
                }
            }
        },
    ) {
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val g = geom
            when {
                loadError != null -> Text("Não foi possível abrir a imagem: $loadError")
                cropping && g != null -> {
                    val ib = remember(g) { g.asImageBitmap() }
                    CropView(ib, edit.crop, aspect?.let { it * g.height / g.width }) { c ->
                        update { it.copy(crop = c) }
                    }
                }
                cropping -> CircularProgressIndicator()
                else -> MonoPreview(mono, Modifier.fillMaxSize())
            }
        }

        if (cropping) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ASPECTS.forEach { (label, a) ->
                    FilterChip(
                        selected = label == aspectLabel,
                        onClick = {
                            aspectLabel = label
                            val g = geom
                            update { it.copy(crop = if (a == null || g == null) NormRect.FULL else NormRect.centered(a * g.height / g.width)) }
                        },
                        label = { Text(label) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        update { it.copy(crop = cropBackup) }
                        cropping = false
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Cancelar") }
                Button(
                    onClick = { cropping = false },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.Yellow, contentColor = Brand.Ink),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("OK", style = MaterialTheme.typography.titleMedium)
                }
            }
            return@ScreenScaffold
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 6.dp,
        ) {
            Column {
                AnimatedVisibility(
                    visible = tool != null && tool != Tool.CROP,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    AnimatedContent(tool, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tool") { t ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp)
                                .padding(top = 6.dp)
                        ) {
                            when (t) {
                                Tool.ROTATE -> RotatePanel(edit, ::update)
                                Tool.TONE -> ToneControls(edit.tone) { v -> update { it.copy(tone = v) } }
                                Tool.BORDER -> BorderControls(edit.border, allowPolaroid = true) { b -> update { it.copy(border = b) } }
                                Tool.CAPTION -> CaptionPanel(edit.caption) { c -> update { it.copy(caption = c) } }
                                else -> Unit
                            }
                            if (multi && (t == Tool.TONE || t == Tool.BORDER)) {
                                TextButton(onClick = {
                                    val cur = s.edits[s.index]
                                    for (i in s.edits.indices) s.edits[i] = s.edits[i].copy(tone = cur.tone, border = cur.border)
                                }) { Text("Aplicar tom e borda em todas as fotos") }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Tool.entries.forEach { t ->
                        ToolButton(t, selected = t == tool, modifier = Modifier.weight(1f)) {
                            if (t == Tool.CROP) {
                                cropBackup = edit.crop
                                cropping = true
                            } else {
                                tool = if (tool == t) null else t
                            }
                        }
                    }
                }
                val width = settings.width
                PrintRow(
                    vm,
                    enabled = geom != null,
                    text = if (multi) "Esta" else "Imprimir",
                    extra = {
                        if (multi) {
                            FilledTonalButton(onClick = {
                                val uris = s.uris
                                val edits = s.edits.toList()
                                vm.print("${uris.size} fotos") {
                                    uris.mapIndexed { i, u -> Pipeline.photoFromUri(appCtx, u, edits[i], width) }
                                }
                            }, modifier = Modifier.height(52.dp)) { Text("Todas") }
                        }
                    },
                ) {
                    val g = geom ?: return@PrintRow
                    val e = edit
                    vm.print("foto") { listOf(Pipeline.photo(g, e, width)) }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(tool: Tool, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (selected) Brand.Yellow else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Icon(tool.icon, contentDescription = null, tint = if (selected) Brand.Ink else MaterialTheme.colorScheme.onSurface)
        }
        Text(
            tool.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CaptionPanel(caption: Caption, onChange: (Caption) -> Unit) {
    SectionTitle("Legenda")
    OutlinedTextField(
        value = caption.text,
        onValueChange = { onChange(caption.copy(text = it)) },
        placeholder = { Text("Ex.: Férias 2026 ☀") },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )
    SectionTitle("Posição")
    ChoiceChips(listOf(false, true), caption.below, { if (it) "Abaixo" else "Acima" }) {
        onChange(caption.copy(below = it))
    }
    val style = caption.style
    LabeledSlider(
        "Tamanho", style.sizePx.toFloat(), 14f..64f,
        { onChange(caption.copy(style = style.copy(sizePx = it.roundToInt()))) },
        format = { "${it.roundToInt()} px" },
    )
    SwitchRow("Negrito", style.bold) { onChange(caption.copy(style = style.copy(bold = it))) }
    SectionTitle("Fonte")
    ChoiceChips(TextFont.entries, style.font, { it.label }) { onChange(caption.copy(style = style.copy(font = it))) }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RotatePanel(edit: PhotoEdit, update: ((PhotoEdit) -> PhotoEdit) -> Unit) {
    SectionTitle("Girar e espelhar")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Mirror is applied after rotation, so flip direction to keep the on-screen turn as labelled.
        FilledTonalButton(onClick = {
            update { it.copy(rotation90 = (it.rotation90 + if (it.mirror) 1 else 3) % 4, crop = it.crop.rotatedCcw()) }
        }) { Icon(Icons.Rounded.Rotate90DegreesCcw, contentDescription = "Girar à esquerda") }
        FilledTonalButton(onClick = {
            update { it.copy(rotation90 = (it.rotation90 + if (it.mirror) 3 else 1) % 4, crop = it.crop.rotatedCw()) }
        }) { Icon(Icons.Rounded.Rotate90DegreesCw, contentDescription = "Girar à direita") }
        FilledTonalButton(onClick = {
            update { it.copy(mirror = !it.mirror, crop = it.crop.mirrored()) }
        }) {
            Icon(Icons.Rounded.Flip, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Espelhar")
        }
    }
    LabeledSlider(
        "Ajuste fino", edit.fineRotation, -45f..45f,
        { v -> update { it.copy(fineRotation = v) } },
        format = { "%.0f°".format(it) },
    )
    Spacer(Modifier.height(8.dp))
}
