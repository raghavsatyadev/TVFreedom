package com.gathin.tvgo.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val vm: TvGoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val s = vm.state
            // a run can take minutes (30 s save wait, reboot): keep the screen on so the phone doesn't sleep mid-run
            LaunchedEffect(s.busy) {
                if (s.busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            BackHandler(enabled = !s.busy && s.screen !in setOf(Screen.CONNECT, Screen.SETUP)) {
                vm.go(if (s.info != null && s.screen != Screen.TV) Screen.TV else Screen.CONNECT)
            }
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    when (s.screen) {
                        Screen.SETUP -> SetupScreen(vm)
                        Screen.CONNECT -> ConnectScreen(vm)
                        Screen.TV -> TvScreen(vm)
                        Screen.PLAN -> PlanScreen(vm)
                        Screen.RUN -> RunScreen(vm)
                        Screen.RESTORE -> RestoreScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(vm)
                    }
                }
            }
        }
    }
}
