package com.uykutakip.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class TimeFormatter(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.forLanguageTag("tr-TR"),
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val fullDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", locale)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)

    fun time(millis: Long): String = atZone(millis).format(timeFormatter)
    fun fullDate(millis: Long): String = atZone(millis).format(fullDateFormatter)
    fun shortDate(millis: Long): String = atZone(millis).format(shortDateFormatter)
    fun localDate(millis: Long): LocalDate = atZone(millis).toLocalDate()
    fun today(): LocalDate = LocalDate.now(zone)

    fun duration(millis: Long): String {
        val minutes = max(0L, millis / 60_000L)
        return if (minutes < 60L) "$minutes dk" else "${minutes / 60} sa ${minutes % 60} dk"
    }

    fun timer(millis: Long): String {
        val seconds = max(0L, millis / 1000L)
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
    }

    private fun atZone(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone)
}

