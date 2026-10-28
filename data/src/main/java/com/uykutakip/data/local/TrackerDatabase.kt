package com.uykutakip.data.local

import androidx.room3.Database
import androidx.room3.AutoMigration
import androidx.room3.RoomDatabase

@Database(
    entities = [
        SleepRecordEntity::class,
        ActiveSleepEntity::class,
        MealRecordEntity::class,
        ActiveMealEntity::class,
        WaterIntakeEntity::class,
        HydrationSettingsEntity::class,
        AgendaEntity::class,
        AgendaTaskTemplateEntity::class,
        AgendaCategoryEntity::class,
        AgendaHiddenTaskSuggestionEntity::class,
        PrivateActivityEntity::class,
        SyncDeletionEntity::class,
        IdeaEntity::class,
        ShoppingEntity::class,
        McpReceiptEntity::class,
    ],
    version = 22,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9), AutoMigration(from = 9, to = 10), AutoMigration(from = 10, to = 11), AutoMigration(from = 11, to = 12), AutoMigration(from = 12, to = 13), AutoMigration(from = 13, to = 14), AutoMigration(from = 14, to = 15), AutoMigration(from = 15, to = 16), AutoMigration(from = 16, to = 17), AutoMigration(from = 17, to = 18), AutoMigration(from = 18, to = 19), AutoMigration(from = 19, to = 20), AutoMigration(from = 20, to = 21), AutoMigration(from = 21, to = 22)],
    exportSchema = true,
)
internal abstract class TrackerDatabase : RoomDatabase() {
    abstract fun sleepDao(): SleepDao
    abstract fun mealDao(): MealDao
    abstract fun hydrationDao(): HydrationDao
    abstract fun agendaDao(): AgendaDao
    abstract fun privateActivityDao(): PrivateActivityDao
    abstract fun syncDeletionDao(): SyncDeletionDao
    abstract fun ideaDao(): IdeaDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun mcpCommandDao(): McpCommandDao
}
