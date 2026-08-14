package com.labteto.dshmobile

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.labteto.dshmobile.connection.HostsStore
import com.labteto.dshmobile.notify.DshNotifications
import com.labteto.dshmobile.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var hostsStore: HostsStore
    @Inject lateinit var notifications: DshNotifications

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not — notifications degrade gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notifications.ensureChannels()
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)

        // Apply the persisted in-app language (11 locales, incl. Thai/RTL).
        lifecycleScope.launch {
            hostsStore.settings.collect { settings ->
                val locales = settings.localeOverride?.let { tag ->
                    LocaleListCompat.forLanguageTags(tag)
                } ?: LocaleListCompat.getEmptyLocaleList()
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }

        setContent {
            AppRoot()
        }
    }
}
