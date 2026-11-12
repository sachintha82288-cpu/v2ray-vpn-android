package com.v2rayvpn.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.v2rayvpn.app.ui.AppRoot
import com.v2rayvpn.app.ui.AppViewModel
import com.v2rayvpn.app.ui.screens.OnboardingScreen
import com.v2rayvpn.app.ui.theme.V2RayVpnTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            V2RayVpnTheme {
                val viewModel: AppViewModel = viewModel()
                val settings by viewModel.settings.collectAsState()

                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* the VPN still runs without it; the notification is simply hidden */ }

                LaunchedEffect(settings.onboardingDone) {
                    if (settings.onboardingDone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                if (!settings.onboardingDone) {
                    OnboardingScreen(onContinue = { viewModel.completeOnboarding() })
                } else {
                    AppRoot(viewModel)
                }
            }
        }
    }
}
