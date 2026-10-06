package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.util.AppThemeMode
import com.example.ui.navigation.AppNavGraph
import com.example.ui.theme.LearnMateTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(AppThemeMode.SYSTEM) }
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            LearnMateTheme(darkTheme = isDark) {
                val viewModel: MainViewModel = viewModel()
                AppNavGraph(
                    viewModel = viewModel,
                    currentThemeMode = themeMode,
                    onThemeModeChange = { newMode -> themeMode = newMode }
                )
            }
        }
    }
}
