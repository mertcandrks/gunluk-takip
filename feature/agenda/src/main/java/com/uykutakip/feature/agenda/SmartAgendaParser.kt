package com.uykutakip.feature.agenda

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class AgendaDraft(
    val text: String,
    val day: LocalDate,
    val isTask: Boolean,
    val completed: Boolean = false,
    val notifyEnabled: Boolean = false,
    val notifyHour: Int = 20,
    val notifyMinute: Int = 0,
    val selected: Boolean = true,
)

enum class AgendaIntent { AUTO, COMPLETED, PLANNED }

object SmartAgendaParser {
    private val turkish = Locale.forLanguageTag("tr-TR")
    private val sentenceSeparator = Regex("""(?:[\r\n]+|(?<=[.!?;])\s+)""")
    private val numericDatePattern = Regex("""(?<!\d)(\d{1,2})[./-](\d{1,2})(?:[./-](\d{2,4}))?(?!\d)""")
    private val clockPattern = Regex("""(?<!\d)(?:sa+t\s*)?([01]?\d|2[0-3])[:.]([0-5]\d)(?!\d)""")
    private val hourPattern = Regex("""(?<!\p{L})sa+t\s+([01]?\d|2[0-3])(?!\d)""")

    private val completedWords = setOf(
        "yaptim", "ettim", "gittim", "geldim", "aldim", "odedim", "bitirdim", "tamamladim",
        "okudum", "ictim", "yedim", "uyudum", "calistim", "temizledim", "toparladim", "hazirladim",
        "aradim", "yazdim", "izledim", "yurudum", "kostum", "pisirdim", "yikadim", "duzenledim",
        "basladim", "kalktim", "uyandim", "gezdim", "dinlendim", "ogrendim", "gonderdim",
    )
    private val plannedWords = setOf(
        "yapacagim", "yapicam", "yapcam", "edecegim", "edicem", "gidecegim", "gidicem", "gitcem",
        "gelecegim", "gelicem", "alacagim", "alicam", "alcam", "odeyecegim", "odicem", "bitirecegim",
        "bitiricem", "okuyacagim", "okuycam", "calisacagim", "calisicam", "hazirlayacagim", "hazirlicam",
        "arayacagim", "arayacam", "yazacagim", "yazicam", "izleyecegim", "izlicem", "uyuyacagim",
        "uyuycam", "kosacagim", "kosucam", "temizleyecegim", "temizlicem", "toplayacagim", "toplicam",
        "gerekiyor", "lazim", "unutma", "planliyorum", "yapilacak", "gidecem", "yapacam",
    )
    private val completedHeaders = setOf(
        "yaptiklarim", "yapilanlar", "tamamlananlar", "tamamladiklarim", "bugun yaptim",
    )
    private val plannedHeaders = setOf(
        "yapacaklarim", "yapilacaklar", "planlarim", "planlananlar", "bugun yapacagim",
    )
    private val dateAliases = mapOf("bugun" to 0, "yarin" to 1, "dun" to -1)
    private val weekdays = mapOf(
        "pazartesi" to DayOfWeek.MONDAY,
        "sali" to DayOfWeek.TUESDAY,
        "carsamba" to DayOfWeek.WEDNESDAY,
        "persembe" to DayOfWeek.THURSDAY,
        "cuma" to DayOfWeek.FRIDAY,
        "cumartesi" to DayOfWeek.SATURDAY,
        "pazar" to DayOfWeek.SUNDAY,
    )

    fun parse(
        input: String,
        baseDate: LocalDate = LocalDate.now(),
        intent: AgendaIntent = AgendaIntent.AUTO,
    ): List<AgendaDraft> {
        var activeIntent = intent
        return input
            .replace(Regex("""(?i)(?<=\p{L})\s+(?=(?:bug[uü]n|yar[ıi]n|d[uü]n|[oö]b[uü]r\s+g[uü]n)\b)"""), ". ")
            .split(sentenceSeparator)
            .map(String::trim)
            .filter(String::isNotBlank)
            .flatMap { rawPart ->
                val header = extractHeader(rawPart)
                if (header != null) {
                    activeIntent = if (intent == AgendaIntent.AUTO) header.first else intent
                    if (header.second.isBlank()) emptyList() else parseSentence(header.second, baseDate, activeIntent)
                } else {
                    parseSentence(rawPart.removeListMarker(), baseDate, activeIntent)
                }
            }
            .filter { it.text.isNotBlank() }
            .take(50)
    }

    private fun parseSentence(sentence: String, baseDate: LocalDate, intent: AgendaIntent): List<AgendaDraft> {
        val inheritedDate = resolveDate(sentence, baseDate)
        return splitActionClauses(sentence).map { clause ->
            parseClause(clause, if (containsDateExpression(clause)) baseDate else inheritedDate, intent)
        }
    }

    private fun extractHeader(raw: String): Pair<AgendaIntent, String>? {
        val pieces = raw.split(':', limit = 2)
        val candidate = searchForm(pieces.first()).trim()
        val matched = sequenceOf(
            completedHeaders.map { AgendaIntent.COMPLETED to it },
            plannedHeaders.map { AgendaIntent.PLANNED to it },
        ).flatten()
            .mapNotNull { (intent, header) ->
                val distance = editDistance(candidate, header, 2)
                if (candidate.length >= 4 && kotlin.math.abs(candidate.length - header.length) <= 2 && distance <= 2) {
                    Triple(intent, header, distance)
                } else null
            }
            .minWithOrNull(compareBy<Triple<AgendaIntent, String, Int>> { it.third }.thenBy { kotlin.math.abs(candidate.length - it.second.length) })
            ?.first
            ?: return null
        return matched to pieces.getOrElse(1) { "" }.trim()
    }

    private fun splitActionClauses(sentence: String): List<String> {
        val candidates = sentence.split(Regex(""",\s+|\s+(?:ve|sonra|ardindan|ardından)\s+""", RegexOption.IGNORE_CASE))
            .map(String::trim)
            .filter(String::isNotBlank)
        return if (candidates.size > 1 && candidates.all(::containsAction)) candidates else listOf(sentence)
    }

    private fun containsAction(text: String): Boolean {
        val words = words(text)
        return words.any { fuzzyContains(it, completedWords) || fuzzyContains(it, plannedWords) }
    }

    private fun parseClause(raw: String, fallbackDate: LocalDate, intent: AgendaIntent): AgendaDraft {
        val normalized = searchForm(raw)
        val tokens = words(raw)
        val day = resolveDate(raw, fallbackDate)
        val time = resolveTime(normalized)
        val detectedCompleted = tokens.any { fuzzyContains(it, completedWords) }
        val detectedPlanned = tokens.any { fuzzyContains(it, plannedWords) }
        val completed = when (intent) {
            AgendaIntent.COMPLETED -> true
            AgendaIntent.PLANNED -> false
            AgendaIntent.AUTO -> detectedCompleted && !detectedPlanned
        }
        val isTask = intent != AgendaIntent.AUTO || detectedCompleted || detectedPlanned
        return AgendaDraft(
            text = cleanText(raw),
            day = day,
            isTask = isTask,
            completed = completed,
            notifyEnabled = isTask && !completed && time.explicit,
            notifyHour = time.hour,
            notifyMinute = time.minute,
        )
    }

    private fun resolveDate(text: String, fallback: LocalDate): LocalDate {
        val normalized = searchForm(text)
        if (Regex("""(?<!\p{L})obur\s+gun(?!\p{L})""").containsMatchIn(normalized)) return fallback.plusDays(2)
        val tokens = normalized.split(Regex("""[^a-z0-9]+""")).filter(String::isNotBlank)
        dateAliases.entries.firstOrNull { alias -> tokens.any { fuzzyWord(it, alias.key, if (alias.key.length >= 5) 1 else 0) } }?.let {
            return fallback.plusDays(it.value.toLong())
        }
        numericDatePattern.find(normalized)?.let { match ->
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val rawYear = match.groupValues[3].toIntOrNull()
            val year = when {
                rawYear == null -> fallback.year
                rawYear < 100 -> 2000 + rawYear
                else -> rawYear
            }
            try {
                return LocalDate.of(year, month, day)
            } catch (_: java.time.DateTimeException) {
                // Invalid dates remain on the selected agenda day.
            }
        }
        weekdays.entries.firstOrNull { day -> tokens.any { fuzzyWord(it, day.key, if (day.key.length > 5) 2 else 1) } }?.let {
            return fallback.with(TemporalAdjusters.nextOrSame(it.value))
        }
        return fallback
    }

    private fun containsDateExpression(text: String): Boolean {
        val normalized = searchForm(text)
        val tokens = normalized.split(Regex("""[^a-z0-9]+""")).filter(String::isNotBlank)
        return Regex("""(?<!\p{L})obur\s+gun(?!\p{L})""").containsMatchIn(normalized) ||
            dateAliases.keys.any { alias -> tokens.any { fuzzyWord(it, alias, if (alias.length >= 5) 1 else 0) } } ||
            numericDatePattern.containsMatchIn(normalized) ||
            weekdays.keys.any { day -> tokens.any { fuzzyWord(it, day, if (day.length > 5) 2 else 1) } }
    }

    private fun resolveTime(text: String): ParsedTime {
        clockPattern.find(text)?.let { return ParsedTime(it.groupValues[1].toInt(), it.groupValues[2].toInt(), true) }
        hourPattern.find(text)?.let { return ParsedTime(it.groupValues[1].toInt(), 0, true) }
        val tokens = text.split(Regex("""[^a-z]+""")).filter(String::isNotBlank)
        return when {
            tokens.any { fuzzyWord(it, "sabah", 1) } -> ParsedTime(9, 0, true)
            tokens.any { fuzzyWord(it, "oglen", 1) } -> ParsedTime(12, 30, true)
            tokens.any { fuzzyWord(it, "aksam", 1) } -> ParsedTime(19, 0, true)
            tokens.any { fuzzyWord(it, "gece", 1) } -> ParsedTime(22, 0, true)
            else -> ParsedTime(20, 0, false)
        }
    }

    private fun cleanText(raw: String): String {
        var value = raw
            .removeListMarker()
            .replace(Regex("""(?i)(?<!\p{L})(bugün|bugun|bugn|bgun|yarın|yarin|yarn|yrin|dün|dun|öbür\s+gün|obur\s+gun)(?!\p{L})"""), " ")
            .replace(numericDatePattern, " ")
            .replace(clockPattern, " ")
            .replace(hourPattern, " ")
            .replace(Regex("""(?i)(?<!\p{L})sa+t(?!\p{L})"""), " ")
            .replace(Regex("""(?i)(?<!\p{L})(sabah|öğlen|oglen|akşam|aksam|gece)(?!\p{L})"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '.', ',', ';', ':', '-', '–')
        value = value.replaceFirst(Regex("""(?i)^(ve|sonra|ardından|ardindan)\s+"""), "")
        if (value.isBlank()) return ""
        return value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(turkish) else it.toString() }
    }

    private fun words(text: String): List<String> = searchForm(text)
        .split(Regex("""[^a-z0-9]+"""))
        .filter { it.length >= 3 }

    private fun String.removeListMarker(): String = replace(Regex("""^\s*(?:[-*•]|\d+[.)])\s*"""), "").trim()

    private fun searchForm(value: String): String {
        val lowered = value.lowercase(turkish)
            .replace('ı', 'i')
            .replace('ş', 's')
            .replace('ğ', 'g')
            .replace('ç', 'c')
            .replace('ö', 'o')
            .replace('ü', 'u')
        return Normalizer.normalize(lowered, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""([a-z])\1{2,}"""), "$1$1")
    }

    private fun fuzzyContains(word: String, dictionary: Set<String>): Boolean = dictionary.any { target ->
        val allowed = when {
            target.length >= 9 -> 2
            target.length >= 5 -> 1
            else -> 0
        }
        fuzzyWord(word, target, allowed)
    }

    private fun fuzzyWord(value: String, target: String, maxDistance: Int): Boolean {
        if (value == target) return true
        if (value.length < 3 || kotlin.math.abs(value.length - target.length) > maxDistance) return false
        return editDistance(value, target, maxDistance) <= maxDistance
    }

    private fun editDistance(left: String, right: String, stopAfter: Int): Int {
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            var rowMin = current[0]
            for (j in right.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1,
                )
                rowMin = minOf(rowMin, current[j + 1])
            }
            if (rowMin > stopAfter) return stopAfter + 1
            previous = current
        }
        return previous[right.length]
    }

    private data class ParsedTime(val hour: Int, val minute: Int, val explicit: Boolean)
}
