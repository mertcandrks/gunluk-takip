package com.uykutakip.feature.nutrition

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
import com.uykutakip.data.MealRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun NutritionRoute(repository: MealRepository) {
    val context = LocalContext.current
    val factory = remember(repository) { NutritionViewModelFactory(repository) }
    val viewModel: NutritionViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formatter = remember { TimeFormatter() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ticking = state.activeMeal != null
    LaunchedEffect(lifecycle, ticking) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                now = System.currentTimeMillis()
                delay(if (ticking) 1_000L else 60_000L - now % 60_000L)
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    MealScreen(
        now = now,
        activeMeal = state.activeMeal,
        records = state.records,
        formatter = formatter,
        onStart = viewModel::start,
        onAddItem = viewModel::addItem,
        onRemoveItem = viewModel::removeItem,
        onFinish = viewModel::finish,
        onQuickAdd = viewModel::quickAdd,
        onAddPastMeal = viewModel::addPastMeal,
        onDelete = viewModel::delete,
        onClear = viewModel::clearRecords,
    )
}
