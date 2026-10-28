package com.uykutakip.feature.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SleepCycleCalculatorTest {
    @Test
    fun `calculates four to seven cycles including fall asleep time`() {
        val options = SleepCycleCalculator.calculate(bedtimeMillis = 0L)

        assertEquals(listOf(4, 5, 6, 7), options.map { it.cycleCount })
        assertEquals(listOf(360, 450, 540, 630), options.map { it.sleepMinutes })
        assertEquals(
            listOf(375L, 465L, 555L, 645L).map { it * 60_000L },
            options.map { it.wakeTimeMillis },
        )
    }

    @Test
    fun `supports a custom cycle and fall asleep duration`() {
        val result = SleepCycleCalculator.calculate(
            bedtimeMillis = 1_000L,
            cycleCounts = 1..1,
            cycleMinutes = 80,
            fallAsleepMinutes = 10,
        ).single()

        assertEquals(1_000L + 90L * 60_000L, result.wakeTimeMillis)
    }

    @Test
    fun `rejects invalid durations`() {
        assertThrows(IllegalArgumentException::class.java) {
            SleepCycleCalculator.calculate(bedtimeMillis = 0L, cycleMinutes = 0)
        }
    }
}
