package com.uykutakip.feature.sleep

data class SleepCycleOption(
    val cycleCount: Int,
    val sleepMinutes: Int,
    val wakeTimeMillis: Long,
)

object SleepCycleCalculator {
    const val DEFAULT_CYCLE_MINUTES = 90
    const val DEFAULT_FALL_ASLEEP_MINUTES = 15

    fun calculate(
        bedtimeMillis: Long,
        cycleCounts: IntRange = 4..7,
        cycleMinutes: Int = DEFAULT_CYCLE_MINUTES,
        fallAsleepMinutes: Int = DEFAULT_FALL_ASLEEP_MINUTES,
    ): List<SleepCycleOption> {
        require(cycleMinutes > 0) { "Cycle duration must be positive" }
        require(fallAsleepMinutes >= 0) { "Fall-asleep duration cannot be negative" }

        return cycleCounts.map { cycleCount ->
            require(cycleCount > 0) { "Cycle count must be positive" }
            val sleepMinutes = cycleCount * cycleMinutes
            SleepCycleOption(
                cycleCount = cycleCount,
                sleepMinutes = sleepMinutes,
                wakeTimeMillis = bedtimeMillis + (fallAsleepMinutes + sleepMinutes) * 60_000L,
            )
        }
    }
}
