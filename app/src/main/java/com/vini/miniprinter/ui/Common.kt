package com.vini.miniprinter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vini.miniprinter.AppViewModel
import com.vini.miniprinter.JobState
import com.vini.miniprinter.Screen
import com.vini.miniprinter.image.BorderSettings
import com.vini.miniprinter.image.DitherMode
import com.vini.miniprinter.image.ImageOps.toBitmap
import com.vini.miniprinter.image.MonoImage
import com.vini.miniprinter.image.TonePresets
import com.vini.miniprinter.image.ToneSettings
import kotlin.math.roundToInt

@Composable
fun AppRoot(vm: AppViewModel) {
    BackHandler(enabled = vm.backStack.size > 1) { vm.pop() }
    val job by vm.printer.job.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PrintJobBar(job, vm) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            AnimatedContent(
                targetState = vm.current,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.Home -> HomeScreen(vm)
                    Screen.Printer -> PrinterScreen(vm)
                    is Screen.Photo -> PhotoEditorScreen(vm, s)
                    is Screen.Document -> DocumentScreen(vm, s)
                    is Screen.Text -> TextScreen(vm, s)
                }
            }
        }
    }
}

@Composable
private fun PrintJobBar(job: JobState, vm: AppViewModel) {
    AnimatedVisibility(
        visible = job != JobState.Idle,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val failed = job is JobState.Failed
        Surface(
            color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.inverseSurface,
            contentColor = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.inverseOnSurface,
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (job) {
                            is JobState.Done -> Icons.Rounded.CheckCircle
                            is JobState.Failed -> Icons.Rounded.ErrorOutline
                            else -> Icons.Rounded.Print
                        },
                        contentDescription = null,
                        tint = if (job is JobState.Done) Brand.Ok else Color.Unspecified,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        when (job) {
                            is JobState.Printing -> "Imprimindo ${job.label}  ${(job.progress * 100).roundToInt()}%"
                            is JobState.Done -> job.message
                            is JobState.Failed -> job.message
                            JobState.Idle -> ""
                        },
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (job is JobState.Printing) {
                        Button(
                            onClick = vm.printer::cancelPrint,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text("Cancelar") }
                    } else {
                        TextButton(onClick = vm.printer::clearJob) {
                            Text("OK", color = if (failed) Color.Unspecified else Brand.Yellow)
                        }
                    }
                }
                if (job is JobState.Printing) {
                    LinearProgressIndicator(
                        progress = { job.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp).clip(CircleShape),
                        color = Brand.Yellow,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets(0.dp),
        )
        content()
    }
}

/** Rounded group of related controls. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MonoLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    format: (Float) -> String = { "%+.2f".format(it) },
    steps: Int = 0,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    format(value),
                    Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(label(o)) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

private const val CUSTOM_TONE = "Personalizado"

@Composable
fun ToneControls(tone: ToneSettings, onChange: (ToneSettings) -> Unit) {
    val matched = TonePresets.all.firstOrNull { it.matches(tone) }
    var custom by remember { mutableStateOf(matched == null) }
    SectionTitle("Tom")
    ChoiceChips(
        TonePresets.all.map { it.name } + CUSTOM_TONE,
        if (custom || matched == null) CUSTOM_TONE else matched.name,
        { it },
    ) { name ->
        custom = name == CUSTOM_TONE
        TonePresets.all.firstOrNull { it.name == name }?.let { onChange(it.applyTo(tone)) }
    }
    AnimatedVisibility(custom) {
        Column(Modifier.padding(top = 6.dp)) {
            LabeledSlider("Brilho", tone.brightness, -1f..1f, { onChange(tone.copy(brightness = it)) })
            LabeledSlider("Contraste", tone.contrast, -1f..1f, { onChange(tone.copy(contrast = it)) })
            LabeledSlider("Gama", tone.gamma, 0.3f..3f, { onChange(tone.copy(gamma = it)) }, format = { "%.2f".format(it) })
        }
    }
    SectionTitle("Pontilhado")
    ChoiceChips(DitherMode.entries, tone.mode, { it.label }) { onChange(tone.copy(mode = it)) }
    if (tone.mode == DitherMode.THRESHOLD || tone.mode == DitherMode.ADAPTIVE) {
        LabeledSlider(
            if (tone.mode == DitherMode.THRESHOLD) "Limiar" else "Sensibilidade",
            tone.threshold.toFloat(), 0f..255f,
            { onChange(tone.copy(threshold = it.roundToInt())) },
            format = { it.roundToInt().toString() },
        )
    }
}

@Composable
fun BorderControls(border: BorderSettings, allowPolaroid: Boolean = false, onChange: (BorderSettings) -> Unit) {
    SwitchRow("Borda", border.enabled) { onChange(border.copy(enabled = it)) }
    AnimatedVisibility(border.enabled) {
        Column {
            LabeledSlider(
                "Linha", border.thickness.toFloat(), 0f..8f,
                { onChange(border.copy(thickness = it.roundToInt())) },
                format = { if (it.roundToInt() == 0) "sem linha" else "${it.roundToInt()} px" }, steps = 7,
            )
            LabeledSlider(
                "Margem branca", border.margin.toFloat(), 0f..32f,
                { onChange(border.copy(margin = it.roundToInt())) },
                format = { "${it.roundToInt()} px" }, steps = 31,
            )
            if (allowPolaroid) {
                SwitchRow("Modo polaroid", border.polaroid) { on ->
                    onChange(border.copy(polaroid = on, margin = if (on) maxOf(border.margin, 16) else border.margin))
                }
                AnimatedVisibility(border.polaroid) {
                    LabeledSlider(
                        "Espaço embaixo", border.polaroidBottom.toFloat(), 40f..240f,
                        { onChange(border.copy(polaroidBottom = it.roundToInt())) },
                        format = { "${it.roundToInt()} px" },
                    )
                }
            }
        }
    }
}

/** The exact dots that will be printed, shown as a strip of thermal paper. */
@Composable
fun MonoPreview(image: MonoImage?, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (image == null) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val bmp = remember(image) { image.toBitmap().asImageBitmap() }
            Image(
                bitmap = bmp,
                contentDescription = "Prévia da impressão",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1))
                    .shadow(8.dp, RoundedCornerShape(3.dp))
                    .background(Color.White),
            )
        }
    }
}

@Composable
fun CopiesStepper(value: Int, onChange: (Int) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            FilledTonalIconButton(onClick = { onChange(value - 1) }, enabled = value > 1, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Remove, contentDescription = "Menos cópias")
            }
            Text(
                "$value×",
                Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            FilledTonalIconButton(onClick = { onChange(value + 1) }, enabled = value < 20, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = "Mais cópias")
            }
        }
    }
}

/** Copies stepper + main print button, shared by all editors. */
@Composable
fun PrintRow(
    vm: AppViewModel,
    enabled: Boolean = true,
    text: String = "Imprimir",
    extra: @Composable RowScope.() -> Unit = {},
    onPrint: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CopiesStepper(vm.copies) { vm.copies = it }
        extra()
        Button(
            onClick = onPrint,
            enabled = enabled,
            modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand.Yellow,
                contentColor = Brand.Ink,
            ),
        ) {
            Icon(Icons.Rounded.Print, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Int = 48) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brand.Yellow),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Brand.Ink)
    }
}
