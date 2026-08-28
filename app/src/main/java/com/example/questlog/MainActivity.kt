package com.example.questlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.ui.dashboard.DashboardScreen
import com.example.questlog.ui.dashboard.DashboardViewModel
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      QuestLogTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          val viewModel: DashboardViewModel = koinViewModel()
          DashboardScreen(viewModel = viewModel)
        }
      }
    }
  }
}
