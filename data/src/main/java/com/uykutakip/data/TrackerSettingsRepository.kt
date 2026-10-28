package com.uykutakip.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SleepReminderSettings(
    val goalMinutes: Int = 480,
    val bedtimeEnabled: Boolean = false,
    val bedtimeHour: Int = 23,
    val bedtimeMinute: Int = 0,
    val wakeEnabled: Boolean = false,
    val wakeHour: Int = 7,
    val wakeMinute: Int = 0,
)

class TrackerSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("tracker_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val settings: StateFlow<SleepReminderSettings> = state

    fun updateGoal(minutes: Int) { if (minutes in 60..960) save(state.value.copy(goalMinutes = minutes)) }
    fun updateBedtime(enabled: Boolean, hour: Int = state.value.bedtimeHour, minute: Int = state.value.bedtimeMinute) = save(state.value.copy(bedtimeEnabled = enabled, bedtimeHour = hour, bedtimeMinute = minute))
    fun updateWake(enabled: Boolean, hour: Int = state.value.wakeHour, minute: Int = state.value.wakeMinute) = save(state.value.copy(wakeEnabled = enabled, wakeHour = hour, wakeMinute = minute))

    private fun save(value: SleepReminderSettings) { preferences.edit().putInt("goal", value.goalMinutes).putBoolean("bedtime", value.bedtimeEnabled).putInt("bedtimeHour", value.bedtimeHour).putInt("bedtimeMinute", value.bedtimeMinute).putBoolean("wake", value.wakeEnabled).putInt("wakeHour", value.wakeHour).putInt("wakeMinute", value.wakeMinute).apply(); state.value = value }
    private fun load() = SleepReminderSettings(preferences.getInt("goal", 480), preferences.getBoolean("bedtime", false), preferences.getInt("bedtimeHour", 23), preferences.getInt("bedtimeMinute", 0), preferences.getBoolean("wake", false), preferences.getInt("wakeHour", 7), preferences.getInt("wakeMinute", 0))
}
