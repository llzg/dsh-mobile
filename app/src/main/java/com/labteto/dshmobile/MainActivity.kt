package com.labteto.dshmobile

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
class MainActivity : AppCompatActivity() {

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
        // Note: MainActivity must extend AppCompatActivity for this to work on
        // Android 12 and below — AppCompat applies the stored app locales to the
        // activity's base context (attachBaseContext) and recreates the activity
        // on change. On Android 13+ the system LocaleManager handles it.
        // Only set locale if it differs from current to avoid recreation loop.
        lifecycleScope.launch {
            hostsStore.settings.collect { settings ->
                val desiredLocales = settings.localeOverride?.let { tag ->
                    LocaleListCompat.forLanguageTags(tag)
                } ?: LocaleListCompat.getEmptyLocaleList()
                
                val currentLocales = AppCompatDelegate.getApplicationLocales()
                
                // Only update if locales actually changed to prevent recreation loop
                if (desiredLocales.toLanguageTags() != currentLocales.toLanguageTags()) {
                    AppCompatDelegate.setApplicationLocales(desiredLocales)
                }
            }
        }

        setContent {
            AppRoot()
        }
    }
}
