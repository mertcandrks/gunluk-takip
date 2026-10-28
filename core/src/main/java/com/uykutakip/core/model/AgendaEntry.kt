package com.uykutakip.core.model

data class AgendaEntry(
    val id: Long = 0,
    val day: String,
    val text: String,
    val isTask: Boolean,
    val completed: Boolean = false,
    val notDone: Boolean = false,
    val repeatDaily: Boolean = false,
    val streak: Int = 0,
    val notifyEnabled: Boolean = false,
    val notifyHour: Int = 20,
    val notifyMinute: Int = 0,
    val isGeneralGoal: Boolean = false,
    val category: String = "",
    val subcategory: String = "",
    val parts: List<AgendaPart> = emptyList(),
    val dailyCount: Int = 0,
    val totalCompletedDays: Int = 0,
    val elapsedMillis: Long = 0,
    val timerStartedAtMillis: Long = 0,
    val priority: Int = 0,
)

data class AgendaPart(val id: String, val title: String, val progress: Int = 0) {
    init {
        require(id.isNotBlank() && id.length <= 80)
        require(title.isNotBlank() && title.length <= 200)
        require(progress in 0..100)
    }
}

val List<AgendaPart>.progressPercent: Int
    get() = if (isEmpty()) 0 else sumOf { it.progress } / size
