package com.example.questlog

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.ui.QuestLogRoot
import com.example.questlog.ui.dashboard.DashboardViewModel
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Palette #1 is a single paper-white scheme — force dark system-bar icons regardless
    // of the OS light/dark setting, otherwise they vanish on the paper ground.
    val lightBars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = lightBars, navigationBarStyle = lightBars)
    setContent {
      QuestLogTheme {
        val viewModel: DashboardViewModel = koinViewModel()
        QuestLogRoot(viewModel = viewModel)
      }
    }
  }
}
