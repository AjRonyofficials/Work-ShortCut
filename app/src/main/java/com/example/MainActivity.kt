package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppDatabase
import com.example.data.local.WorkShortcutRepository
import com.example.service.OverlayStateManager
import com.example.ui.screens.MainScreen
import com.example.ui.theme.WorkShortcutTheme

class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("NAVIGATE_TO_SHEET", false)) {
            OverlayStateManager.requestTab("EXCEL")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        OverlayStateManager.init(this)

        val database = AppDatabase.getDatabase(this)
        val repository = WorkShortcutRepository(
            excelRowDao = database.excelRowDao(),
            proxyProfileDao = database.proxyProfileDao(),
            twoFactorDao = database.twoFactorDao()
        )

        if (intent?.getBooleanExtra("NAVIGATE_TO_SHEET", false) == true) {
            OverlayStateManager.requestTab("EXCEL")
        }

        setContent {
            val state by OverlayStateManager.uiState.collectAsStateWithLifecycle()
            val savedExcelRows by repository.allExcelRows.collectAsStateWithLifecycle(initialValue = emptyList())
            val savedProxies by repository.allProxies.collectAsStateWithLifecycle(initialValue = emptyList())
            val savedTwoFactorKeys by repository.allTwoFactorKeys.collectAsStateWithLifecycle(initialValue = emptyList())

            WorkShortcutTheme(themeMode = state.appTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        state = state,
                        savedExcelRows = savedExcelRows,
                        savedProxies = savedProxies,
                        savedTwoFactorKeys = savedTwoFactorKeys,
                        repository = repository
                    )
                }
            }
        }
    }
}
