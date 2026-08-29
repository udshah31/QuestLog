package com.example.questlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.ui.QuestLogRoot
import com.example.questlog.ui.dashboard.DashboardViewModel
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      QuestLogTheme {
        val viewModel: DashboardViewModel = koinViewModel()
        QuestLogRoot(viewModel = viewModel)
      }
    }
  }
}
