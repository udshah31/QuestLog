package com.example.questlog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.questlog.ui.dashboard.DashboardScreen
import com.example.questlog.ui.dashboard.DashboardViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
    val dashboardViewModel: DashboardViewModel = koinViewModel()
    DashboardScreen(
        viewModel = dashboardViewModel,
        modifier = modifier,
    )
}
