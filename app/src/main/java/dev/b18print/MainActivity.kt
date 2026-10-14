package dev.b18print

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.b18print.data.Store
import dev.b18print.printer.PrinterManager
import dev.b18print.ui.AppTheme
import dev.b18print.ui.EditorScreen
import dev.b18print.ui.EditorViewModel
import dev.b18print.ui.LogScreen
import dev.b18print.ui.ProfilesScreen
import dev.b18print.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    /** Applies the language chosen in the settings (see [L10n]). */
    override fun attachBaseContext(newBase: Context) {
        Store.init(newBase)
        super.attachBaseContext(L10n.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val vm: EditorViewModel = viewModel()
                var screen by rememberSaveable { mutableStateOf("editor") }
                val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
                    if (res.values.all { it }) maybeAutoConnect()
                }
                LaunchedEffect(Unit) {
                    if (!PrinterManager.hasPermissions(this@MainActivity)) {
                        permLauncher.launch(PrinterManager.requiredPermissions())
                    } else {
                        maybeAutoConnect()
                    }
                }
                val requestPermissions = { permLauncher.launch(PrinterManager.requiredPermissions()) }

                BackHandler(enabled = screen != "editor") {
                    screen = if (screen == "profiles" || screen == "log") "settings" else "editor"
                }
                Box(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding().imePadding()) {
                    when (screen) {
                        "settings" -> SettingsScreen(
                            onBack = { screen = "editor" },
                            onOpenProfiles = { screen = "profiles" },
                            onOpenLog = { screen = "log" },
                            requestPermissions = requestPermissions,
                        )
                        "profiles" -> ProfilesScreen(onBack = { screen = "editor" }) { p ->
                            vm.setProfile(p)
                            screen = "editor"
                        }
                        "log" -> LogScreen(onBack = { screen = "settings" })
                        else -> EditorScreen(
                            vm,
                            onOpenSettings = { screen = "settings" },
                            onOpenProfiles = { screen = "profiles" },
                        )
                    }
                }
            }
        }
    }

    private fun maybeAutoConnect() {
        val s = Store.settings.value
        val adapter = PrinterManager.adapter(this)
        if (adapter != null && !adapter.isEnabled) {
            try {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: SecurityException) {}
            return
        }
        if (s.autoConnect && s.deviceAddress != null && !PrinterManager.isConnected) PrinterManager.connectSaved(this)
    }
}
