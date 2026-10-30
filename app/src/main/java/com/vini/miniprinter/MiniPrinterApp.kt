package com.vini.miniprinter

import android.app.Application
import androidx.core.content.res.ResourcesCompat
import com.vini.miniprinter.image.TextFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class MiniPrinterApp : Application() {
    /** App-wide so a print keeps going while the user navigates. */
    val printer by lazy {
        PrinterManager(this, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
    val history by lazy { PrintHistory(File(filesDir, "history")) }

    override fun onCreate() {
        super.onCreate()
        TextFont.bundledMono = ResourcesCompat.getFont(this, R.font.courier_prime_family)
    }
}
