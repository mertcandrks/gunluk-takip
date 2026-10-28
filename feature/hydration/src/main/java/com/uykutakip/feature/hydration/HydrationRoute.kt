package com.uykutakip.feature.hydration

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uykutakip.core.time.TimeFormatter
import com.uykutakip.data.HydrationRepository

@Composable
fun HydrationRoute(repository: HydrationRepository) {
    val context = LocalContext.current
    val factory = remember(repository) { HydrationViewModelFactory(repository) }
    val viewModel: HydrationViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formatter = remember { TimeFormatter() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    HydrationScreen(
        records = state.records,
        dailyGoalMl = state.dailyGoalMl,
        formatter = formatter,
        onAdd = viewModel::add,
        onUpdateGoal = viewModel::updateGoal,
        onDelete = viewModel::delete,
        onClear = viewModel::clearRecords,
    )
}
