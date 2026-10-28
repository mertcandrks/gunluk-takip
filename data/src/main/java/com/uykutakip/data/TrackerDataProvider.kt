package com.uykutakip.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.uykutakip.data.local.TrackerDatabase

class TrackerDataProvider(context: Context) {
    private val appContext = context.applicationContext
    val settingsRepository = TrackerSettingsRepository(appContext)
    val reminderScheduler = ReminderScheduler(appContext)
    private val database = Room.databaseBuilder(
        appContext,
        TrackerDatabase::class.java,
        DATABASE_NAME,
    ).setDriver(BundledSQLiteDriver()).build()

    val sleepRepository: SleepRepository = OfflineSleepRepository(database.sleepDao(), database.syncDeletionDao())
    val mealRepository: MealRepository = OfflineMealRepository(database.mealDao(), database.syncDeletionDao())
    val hydrationRepository: HydrationRepository = OfflineHydrationRepository(database.hydrationDao(), database.syncDeletionDao())
    val agendaRepository: AgendaRepository = OfflineAgendaRepository(database.agendaDao(), database.syncDeletionDao())
    val ideasRepository: IdeasRepository = OfflineIdeasRepository(database.ideaDao())
    val shoppingRepository: ShoppingRepository = OfflineShoppingRepository(database.shoppingDao())
    val privateActivityRepository: PrivateActivityRepository = OfflinePrivateActivityRepository(database.privateActivityDao(), database.syncDeletionDao())
    val mcpRepository = McpRepository(database, this)
    val syncSnapshotRepository = SyncSnapshotRepository(
        database.sleepDao(),
        database.mealDao(),
        database.hydrationDao(),
        database.agendaDao(),
        database.privateActivityDao(),
        database.syncDeletionDao(),
    )

    suspend fun migrateLegacyData() {
        LegacyDataMigrator(appContext, database).migrateIfNeeded()
    }

    private companion object {
        const val DATABASE_NAME = "daily_tracker.db"
    }
}
