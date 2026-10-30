package com.vini.miniprinter.ui

import android.text.Layout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vini.miniprinter.AppViewModel
import com.vini.miniprinter.Screen
import com.vini.miniprinter.image.MonoImage
import com.vini.miniprinter.image.Pipeline
import com.vini.miniprinter.image.TextFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val ALIGNS = listOf(
    Layout.Alignment.ALIGN_NORMAL to "Esquerda",
    Layout.Alignment.ALIGN_CENTER to "Centro",
    Layout.Alignment.ALIGN_OPPOSITE to "Direita",
)

@Composable
fun TextScreen(vm: AppViewModel, s: Screen.Text) {
    val settings by vm.printer.settings.collectAsStateWithLifecycle()
    val style = s.style
    val preview by produceState<MonoImage?>(null, s.text, style, settings.width) {
        delay(120)
        value = withContext(Dispatchers.Default) { Pipeline.text(s.text, style, settings.width) }
    }

    ScreenScaffold("Texto", onBack = vm::pop) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = s.text,
                onValueChange = { s.text = it },
                placeholder = { Text("Escreva aqui…") },
                minLines = 4,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            Panel {
                LabeledSlider(
                    "Tamanho", style.sizePx.toFloat(), 14f..96f,
                    { s.style = style.copy(sizePx = it.roundToInt()) },
                    format = { "${it.roundToInt()} px" },
                )
                SwitchRow("Negrito", style.bold) { s.style = style.copy(bold = it) }
                SectionTitle("Alinhamento")
                ChoiceChips(ALIGNS.map { it.first }, style.align, { a -> ALIGNS.first { it.first == a }.second }) {
                    s.style = style.copy(align = it)
                }
                SectionTitle("Fonte")
                ChoiceChips(TextFont.entries, style.font, { it.label }) { s.style = style.copy(font = it) }
            }
            Panel {
                BorderControls(style.border) { s.style = style.copy(border = it) }
            }
            SectionTitle("Prévia")
            MonoPreview(preview, Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(bottom = 16.dp))
        }
        val width = settings.width
        PrintRow(vm, enabled = s.text.isNotBlank()) {
            val text = s.text
            vm.print("texto") { listOf(Pipeline.text(text, style, width)) }
        }
    }
}
