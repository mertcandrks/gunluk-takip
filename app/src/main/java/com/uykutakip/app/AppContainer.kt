package com.uykutakip.app

import android.content.Context
import com.uykutakip.data.MealRepository
import com.uykutakip.data.HydrationRepository
import com.uykutakip.data.SleepRepository
import com.uykutakip.data.TrackerDataProvider
import com.uykutakip.data.PrivateActivityRepository

class AppContainer(context: Context) {
    private val dataProvider = TrackerDataProvider(context)

    val sleepRepository: SleepRepository = dataProvider.sleepRepository
    val mealRepository: MealRepository = dataProvider.mealRepository
    val hydrationRepository: HydrationRepository = dataProvider.hydrationRepository
    val agendaRepository = dataProvider.agendaRepository
    val ideasRepository = dataProvider.ideasRepository
    val shoppingRepository = dataProvider.shoppingRepository
    val privateActivityRepository: PrivateActivityRepository = dataProvider.privateActivityRepository
    val settingsRepository = dataProvider.settingsRepository
    val reminderScheduler = dataProvider.reminderScheduler
    val syncSnapshotRepository = dataProvider.syncSnapshotRepository
    val mcpRepository = dataProvider.mcpRepository

    suspend fun migrateLegacyData() = dataProvider.migrateLegacyData()
}
