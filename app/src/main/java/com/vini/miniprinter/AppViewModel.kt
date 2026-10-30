package com.vini.miniprinter

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vini.miniprinter.image.BorderSettings
import com.vini.miniprinter.image.DitherMode
import com.vini.miniprinter.image.MonoImage
import com.vini.miniprinter.image.PhotoEdit
import com.vini.miniprinter.image.TextStyle
import com.vini.miniprinter.image.ToneSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screens carry their own editable state so it survives rotation (held by the ViewModel). */
sealed class Screen {
    data object Home : Screen()
    data object Printer : Screen()

    class Photo(val uris: List<Uri>) : Screen() {
        val edits = mutableStateListOf<PhotoEdit>().apply { repeat(uris.size) { add(PhotoEdit()) } }
        var index by mutableIntStateOf(0)
    }

    class Document(val uri: Uri, val mime: String) : Screen() {
        private val isPdf = mime == "application/pdf"
        var tone by mutableStateOf(
            if (isPdf) ToneSettings(mode = DitherMode.THRESHOLD, threshold = 170)
            else ToneSettings(mode = DitherMode.ADAPTIVE)
        )
        var border by mutableStateOf(BorderSettings(enabled = false))
        var landscape by mutableStateOf(false)
        val excluded = mutableStateMapOf<Int, Boolean>()
    }

    class Text(initial: String) : Screen() {
        var text by mutableStateOf(initial)
        var style by mutableStateOf(TextStyle())
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val printer = (app as MiniPrinterApp).printer
    val history = (app as MiniPrinterApp).history

    val backStack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = backStack.last()

    fun push(s: Screen) {
        backStack.add(s)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    var copies by mutableIntStateOf(1)

    /** Renders off the main thread, then hands the pages ([copies] times) to the printer queue. */
    fun print(label: String, render: suspend () -> List<MonoImage>) {
        val n = copies
        viewModelScope.launch {
            val pages = runCatching { withContext(Dispatchers.Default) { render() } }
                .getOrElse {
                    printer.reportError("Erro ao preparar: ${it.message}")
                    return@launch
                }
            printer.print(List(n) { pages }.flatten(), if (n > 1) "$label ×$n" else label) {
                history.add(label, pages)
            }
        }
    }

    /** Prints a history entry again, pixel-identical, once. */
    fun reprint(entry: HistoryEntry) {
        viewModelScope.launch {
            val pages = history.pages(entry.id)
            if (pages.isEmpty()) printer.reportError("Impressão não encontrada no histórico")
            else printer.print(pages, entry.label)
        }
    }

    fun handleIntent(intent: Intent?) {
        val screen = screenFor(intent ?: return) ?: return
        backStack.clear()
        backStack.add(Screen.Home)
        backStack.add(screen)
    }

    private fun screenFor(intent: Intent): Screen? {
        val cr = getApplication<Application>().contentResolver
        fun mimeOf(uri: Uri, fallback: String?) = cr.getType(uri) ?: fallback ?: ""
        fun forUri(uri: Uri, mime: String): Screen? = when {
            mime.startsWith("image/") -> Screen.Photo(listOf(uri))
            mime == "application/pdf" -> Screen.Document(uri, mime)
            mime.startsWith("text/") -> cr.openInputStream(uri)?.use { Screen.Text(it.reader().readText()) }
            else -> null
        }
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) {
                    forUri(uri, mimeOf(uri, intent.type))
                } else {
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { Screen.Text(it.toString()) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    .orEmpty()
                    .filter { mimeOf(it, intent.type).startsWith("image/") }
                if (uris.isEmpty()) null else Screen.Photo(uris)
            }
            Intent.ACTION_VIEW -> intent.data?.let { forUri(it, mimeOf(it, intent.type)) }
            else -> null
        }
    }
}
