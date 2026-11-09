package com.nexora.commandcenter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.nexora.commandcenter.core.datastore.NexoraPreferencesDataStore
import com.nexora.commandcenter.core.designsystem.NexoraTheme
import com.nexora.commandcenter.navigation.NexoraMainScaffold
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesDataStore: NexoraPreferencesDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs by preferencesDataStore.userPreferencesFlow.collectAsState(
                initial = NexoraPreferencesDataStore.UserPreferences()
            )
            val layoutDirection = if (prefs.languageCode == "fa") {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                NexoraTheme(darkTheme = prefs.isDarkMode) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        NexoraMainScaffold(
                            languageCode = prefs.languageCode,
                            isDarkMode = prefs.isDarkMode
                        )
                    }
                }
            }
        }
    }
}
