package com.uykutakip.feature.sleep

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uykutakip.core.time.TimeFormatter
import com.uykutakip.data.SleepRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SleepRoute(repository: SleepRepository) {
    val context = LocalContext.current
    val factory = remember(repository) { SleepViewModelFactory(repository) }
    val viewModel: SleepViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formatter = remember { TimeFormatter() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                now = System.currentTimeMillis()
                delay(60_000L - now % 60_000L)
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    SleepScreen(
        now = now,
        activeStartMillis = state.activeStartMillis ?: 0L,
        records = state.records,
        formatter = formatter,
        onStart = viewModel::start,
        onFinish = viewModel::finish,
        onFinishAt = viewModel::finishAt,
        onUpdateActiveStart = viewModel::updateActiveStart,
        onAddRecord = viewModel::addRecord,
        onUpdateRecord = viewModel::updateRecord,
        onDeleteRecord = viewModel::deleteRecord,
        onClear = viewModel::clearRecords,
    )
}
