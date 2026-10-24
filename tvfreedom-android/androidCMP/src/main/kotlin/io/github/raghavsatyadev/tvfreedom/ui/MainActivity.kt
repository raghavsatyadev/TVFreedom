package io.github.raghavsatyadev.tvfreedom.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.raghavsatyadev.tvfreedom.TvFreedomApp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val vm: TvFreedomViewModel by viewModels {
        viewModelFactory { initializer { TvFreedomViewModel((application as TvFreedomApp).platform) } }
    }

    /** Android 13+: the run's progress notification needs this; the run goes on either way. */
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val s = vm.state
            // a run can take minutes (30 s save wait, reboot): keep the screen on so the phone doesn't sleep mid-run
            LaunchedEffect(s.busy) {
                if (s.busy && Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                if (s.busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            BackHandler(enabled = !s.busy && s.screen != Screen.CONNECT && (s.screen != Screen.SETUP || vm.settings.onboarded)) {
                vm.go(when {
                    s.screen == Screen.SETUP -> Screen.SETTINGS
                    s.info != null && s.screen != Screen.TV -> Screen.TV
                    else -> Screen.CONNECT
                })
            }
            TvFreedomTheme {
                Surface(Modifier.fillMaxSize()) {
                    when (s.screen) {
                        Screen.SETUP -> SetupScreen(vm)
                        Screen.CONNECT -> ConnectScreen(vm)
                        Screen.TV -> TvScreen(vm)
                        Screen.PLAN -> PlanScreen(vm)
                        Screen.RUN -> RunScreen(vm)
                        Screen.RESTORE -> RestoreScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(vm)
                        Screen.HOME -> HomeScreen(vm)
                        Screen.DESIGNS -> DesignsScreen(vm)
                    }
                }
            }
        }
    }
}
