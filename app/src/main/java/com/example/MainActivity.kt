package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.example.data.network.HuamiApiService
import com.example.data.repository.PreferencesManager
import com.example.ui.FirstStartWizardScreen
import com.example.ui.HuafetcherScreen
import com.example.ui.HuafetcherViewModel
import com.example.ui.HuafetcherViewModelFactory
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefsManager = PreferencesManager(applicationContext)
        val apiService = HuamiApiService(applicationContext, prefsManager)
        val viewModelFactory = HuafetcherViewModelFactory(apiService, prefsManager)
        val viewModel = ViewModelProvider(this, viewModelFactory)[HuafetcherViewModel::class.java]

        setContent {
            MyApplicationTheme {
                val isFirstStartCompleted by viewModel.isFirstStartCompleted.collectAsState()
                var showSettings by remember { mutableStateOf(false) }

                when {
                    !isFirstStartCompleted -> {
                        FirstStartWizardScreen(
                            viewModel = viewModel,
                            onWizardCompleted = {
                                // First start completed, state will update automatically via ViewModel StateFlow
                            }
                        )
                    }
                    showSettings -> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { showSettings = false },
                            onRunWizardAgain = { showSettings = false }
                        )
                    }
                    else -> {
                        HuafetcherScreen(
                            viewModel = viewModel,
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
}
