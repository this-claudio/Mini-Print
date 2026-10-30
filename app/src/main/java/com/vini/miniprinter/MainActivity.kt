package com.vini.miniprinter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.vini.miniprinter.ui.AppRoot
import com.vini.miniprinter.ui.MiniPrinterTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) vm.handleIntent(intent)
        if (intent.getBooleanExtra(EXTRA_PROBE, false)) vm.printer.probe()
        setContent {
            MiniPrinterTheme { AppRoot(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleIntent(intent)
        if (intent.getBooleanExtra(EXTRA_PROBE, false)) vm.printer.probe()
    }

    companion object {
        /** adb shell am start -n com.vini.miniprinter/.MainActivity --ez probe true */
        private const val EXTRA_PROBE = "probe"
    }
}
