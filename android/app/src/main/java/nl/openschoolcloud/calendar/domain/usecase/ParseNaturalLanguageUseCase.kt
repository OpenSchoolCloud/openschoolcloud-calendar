package nl.openschoolcloud.calendar.domain.usecase

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import javax.inject.Inject

/**
 * Parsed event result from natural language input
 */
data class ParsedEvent(
    val title: String,
    val startDate: LocalDate?,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val location: String?,
    val duration: Duration = Duration.ofHours(1),
    val confidence: Float,
    val rawInput: String
)

/**
 * Use case for parsing natural language input into event components.
 *
 * Supports Dutch and English input. Runs entirely offline with regex-based parsing.
 */
class ParseNaturalLanguageUseCase @Inject constructor() {

    fun parse(input: String): ParsedEvent {
        if (input.isBlank()) {
            return ParsedEvent(
                title = "",
                startDate = LocalDate.now(),
                startTime = null,
                endTime = null,
                location = null,
                confidence = 0.0f,
                rawInput = input
            )
        }

        val normalized = input.trim()
        val lower = normalized.lowercase()

        val dateResult = extractDate(lower)
        val timeResult = extractTime(lower)
        val locationResult = extractLocation(normalized)
        val title = extractTitle(lower, dateResult, timeResult, locationResult)

        val duration = if (timeResult.endTime != null && timeResult.startTime != null) {
            Duration.between(timeResult.startTime, timeResult.endTime).let {
                if (it.isNegative) it.plusHours(24) else it
            }
        } else {
            Duration.ofHours(1)
        }

        return ParsedEvent(
            title = title,
            startDate = dateResult.date,
            startTime = timeResult.startTime,
            endTime = timeResult.endTime,
            location = locationResult.location,
            duration = duration,
            confidence = calculateConfidence(dateResult, timeResult, title),
            rawInput = input
        )
    }

    // --- Date Extraction ---

    private data class DateResult(
        val date: LocalDate,
        val matched: String?,
        val confidence: Float
    )

    private fun extractDate(input: String): DateResult {
        val today = LocalDate.now()

        // Ordered from longest to shortest to match greedy patterns first
        val relativePatterns = listOf(
            // Dutch multi-word relative patterns
            "volgende week maandag" to { nextWeekday(DayOfWeek.MONDAY).plusWeeks(1) },
            "volgende week dinsdag" to { nextWeekday(DayOfWeek.TUESDAY).plusWeeks(1) },
            "volgende week woensdag" to { nextWeekday(DayOfWeek.WEDNESDAY).plusWeeks(1) },
            "volgende week donderdag" to { nextWeekday(DayOfWeek.THURSDAY).plusWeeks(1) },
            "volgende week vrijdag" to { nextWeekday(DayOfWeek.FRIDAY).plusWeeks(1) },
            "volgende week zaterdag" to { nextWeekday(DayOfWeek.SATURDAY).plusWeeks(1) },
            "volgende week zondag" to { nextWeekday(DayOfWeek.SUNDAY).plusWeeks(1) },
            "aanstaande maandag" to { nextWeekday(DayOfWeek.MONDAY) },
            "aanstaande dinsdag" to { nextWeekday(DayOfWeek.TUESDAY) },
            "aanstaande woensdag" to { nextWeekday(DayOfWeek.WEDNESDAY) },
            "aanstaande donderdag" to { nextWeekday(DayOfWeek.THURSDAY) },
            "aanstaande vrijdag" to { nextWeekday(DayOfWeek.FRIDAY) },
            "aanstaande zaterdag" to { nextWeekday(DayOfWeek.SATURDAY) },
            "aanstaande zondag" to { nextWeekday(DayOfWeek.SUNDAY) },
            "komende maandag" to { nextWeekday(DayOfWeek.MONDAY) },
            "komende dinsdag" to { nextWeekday(DayOfWeek.TUESDAY) },
            "komende woensdag" to { nextWeekday(DayOfWeek.WEDNESDAY) },
            "komende donderdag" to { nextWeekday(DayOfWeek.THURSDAY) },
            "komende vrijdag" to { nextWeekday(DayOfWeek.FRIDAY) },
            "komende zaterdag" to { nextWeekday(DayOfWeek.SATURDAY) },
            "komende zondag" to { nextWeekday(DayOfWeek.SUNDAY) },
            "over twee weken" to { today.plusWeeks(2) },
            "over 2 weken" to { today.plusWeeks(2) },
            "over een week" to { today.plusWeeks(1) },
            "volgende week" to { today.plusWeeks(1) },
            // English multi-word
            "next monday" to { nextWeekdayAfterThis(DayOfWeek.MONDAY) },
            "next tuesday" to { nextWeekdayAfterThis(DayOfWeek.TUESDAY) },
            "next wednesday" to { nextWeekdayAfterThis(DayOfWeek.WEDNESDAY) },
            "next thursday" to { nextWeekdayAfterThis(DayOfWeek.THURSDAY) },
            "next friday" to { nextWeekdayAfterThis(DayOfWeek.FRIDAY) },
            "next saturday" to { nextWeekdayAfterThis(DayOfWeek.SATURDAY) },
            "next sunday" to { nextWeekdayAfterThis(DayOfWeek.SUNDAY) },
            "next week" to { today.plusWeeks(1) },
            // Single words - Dutch
            "overmorgen" to { today.plusDays(2) },
            "morgen" to { today.plusDays(1) },
            "vandaag" to { today },
            "gisteren" to { today.minusDays(1) },
            "maandag" to { nextWeekday(DayOfWeek.MONDAY) },
            "dinsdag" to { nextWeekday(DayOfWeek.TUESDAY) },
            "woensdag" to { nextWeekday(DayOfWeek.WEDNESDAY) },
            "donderdag" to { nextWeekday(DayOfWeek.THURSDAY) },
            "vrijdag" to { nextWeekday(DayOfWeek.FRIDAY) },
            "zaterdag" to { nextWeekday(DayOfWeek.SATURDAY) },
            "zondag" to { nextWeekday(DayOfWeek.SUNDAY) },
            // Single words - English
            "tomorrow" to { today.plusDays(1) },
            "today" to { today },
            "yesterday" to { today.minusDays(1) },
            "monday" to { nextWeekday(DayOfWeek.MONDAY) },
            "tuesday" to { nextWeekday(DayOfWeek.TUESDAY) },
            "wednesday" to { nextWeekday(DayOfWeek.WEDNESDAY) },
            "thursday" to { nextWeekday(DayOfWeek.THURSDAY) },
            "friday" to { nextWeekday(DayOfWeek.FRIDAY) },
            "saturday" to { nextWeekday(DayOfWeek.SATURDAY) },
            "sunday" to { nextWeekday(DayOfWeek.SUNDAY) },
        )

        // Check relative patterns (longest match first due to ordering)
        for ((pattern, dateSupplier) in relativePatterns) {
            if (input.contains(pattern)) {
                return DateResult(dateSupplier(), pattern, confidence = 0.9f)
            }
        }

        // Try absolute dates
        val absoluteDate = parseAbsoluteDate(input)
        if (absoluteDate != null) {
            return DateResult(absoluteDate.first, absoluteDate.second, confidence = 0.95f)
        }

        // No date found - default to today
        return DateResult(today, null, confidence = 0.3f)
    }

    private fun nextWeekday(day: DayOfWeek): LocalDate {
        val today = LocalDate.now()
        var date = today.plusDays(1)
        while (date.dayOfWeek != day) {
            date = date.plusDays(1)
        }
        return date
    }

    /**
     * "next monday" means the monday AFTER the coming one
     */
    private fun nextWeekdayAfterThis(day: DayOfWeek): LocalDate {
        return nextWeekday(day).plusWeeks(1)
    }

    private val dutchMonthNames = mapOf(
        "januari" to Month.JANUARY, "jan" to Month.JANUARY,
        "februari" to Month.FEBRUARY, "feb" to Month.FEBRUARY,
        "maart" to Month.MARCH, "mrt" to Month.MARCH,
        "april" to Month.APRIL, "apr" to Month.APRIL,
        "mei" to Month.MAY,
        "juni" to Month.JUNE, "jun" to Month.JUNE,
        "juli" to Month.JULY, "jul" to Month.JULY,
        "augustus" to Month.AUGUST, "aug" to Month.AUGUST,
        "september" to Month.SEPTEMBER, "sep" to Month.SEPTEMBER,
        "oktober" to Month.OCTOBER, "okt" to Month.OCTOBER,
        "november" to Month.NOVEMBER, "nov" to Month.NOVEMBER,
        "december" to Month.DECEMBER, "dec" to Month.DECEMBER,
    )

    private val englishMonthNames = mapOf(
        "january" to Month.JANUARY, "jan" to Month.JANUARY,
        "february" to Month.FEBRUARY, "feb" to Month.FEBRUARY,
        "march" to Month.MARCH, "mar" to Month.MARCH,
        "april" to Month.APRIL, "apr" to Month.APRIL,
        "may" to Month.MAY,
        "june" to Month.JUNE, "jun" to Month.JUNE,
        "july" to Month.JULY, "jul" to Month.JULY,
        "august" to Month.AUGUST, "aug" to Month.AUGUST,
        "september" to Month.SEPTEMBER, "sep" to Month.SEPTEMBER,
        "october" to Month.OCTOBER, "oct" to Month.OCTOBER,
        "november" to Month.NOVEMBER, "nov" to Month.NOVEMBER,
        "december" to Month.DECEMBER, "dec" to Month.DECEMBER,
    )

    private val allMonthNames = dutchMonthNames + englishMonthNames
    private val monthPattern = allMonthNames.keys.sortedByDescending { it.length }.joinToString("|")

    private fun parseAbsoluteDate(input: String): Pair<LocalDate, String>? {
        val today = LocalDate.now()

        // DD monthname (e.g., "15 januari", "3 feb")
        val monthNameRegex = Regex("""(\d{1,2})\s+($monthPattern)""")
        monthNameRegex.find(input)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            val month = allMonthNames[match.groupValues[2]] ?: return@let
            if (day in 1..31) {
                val year = if (LocalDate.of(today.year, month, day.coerceAtMost(month.maxLength()))
                        .isBefore(today)
                ) today.year + 1 else today.year
                return try {
                    LocalDate.of(year, month, day) to match.value
                } catch (e: Exception) {
                    null
                }
            }
        }

        // ISO format: YYYY-MM-DD
        val isoRegex = Regex("""(\d{4})-(\d{2})-(\d{2})""")
        isoRegex.find(input)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return@let
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            val day = match.groupValues[3].toIntOrNull() ?: return@let
            return try {
                LocalDate.of(year, month, day) to match.value
            } catch (e: Exception) {
                null
            }
        }

        // DD/MM/YYYY or DD-MM-YYYY or DD.MM.YYYY (with optional year)
        val numericDateRegex = Regex("""(\d{1,2})[/\-.](\d{1,2})(?:[/\-.](\d{2,4}))?""")
        numericDateRegex.find(input)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            val yearStr = match.groupValues[3]
            if (day !in 1..31 || month !in 1..12) return@let

            val year = when {
                yearStr.isBlank() -> {
                    if (LocalDate.of(today.year, month, day.coerceAtMost(Month.of(month).maxLength()))
                            .isBefore(today)
                    ) today.year + 1 else today.year
                }
                yearStr.length == 2 -> 2000 + (yearStr.toIntOrNull() ?: return@let)
                yearStr.length == 4 -> yearStr.toIntOrNull() ?: return@let
                else -> return@let
            }

            return try {
                LocalDate.of(year, month, day) to match.value
            } catch (e: Exception) {
                null
            }
        }

        return null
    }

    // --- Time Extraction ---

    private data class TimeResult(
        val startTime: LocalTime?,
        val endTime: LocalTime?,
        val matched: String?,
        val confidence: Float
    )

    private fun extractTime(input: String): TimeResult {
        // Time range: "14:00-15:30", "14:00 tot 15:00", "14.00-15.00", "9:00 - 11:00"
        val rangeRegex = Regex("""(\d{1,2})[:.](\d{2})\s*[-–]\s*(\d{1,2})[:.](\d{2})""")
        rangeRegex.find(input)?.let { match ->
            val startHour = match.groupValues[1].toIntOrNull() ?: return@let
            val startMin = match.groupValues[2].toIntOrNull() ?: return@let
            val endHour = match.groupValues[3].toIntOrNull() ?: return@let
            val endMin = match.groupValues[4].toIntOrNull() ?: return@let
            if (startHour in 0..23 && startMin in 0..59 && endHour in 0..23 && endMin in 0..59) {
                return TimeResult(
                    startTime = LocalTime.of(startHour, startMin),
                    endTime = LocalTime.of(endHour, endMin),
                    matched = match.value,
                    confidence = 0.95f
                )
            }
        }

        // Range with "tot": "14:00 tot 15:30"
        val rangeTotRegex = Regex("""(\d{1,2})[:.](\d{2})\s+tot\s+(\d{1,2})[:.](\d{2})""")
        rangeTotRegex.find(input)?.let { match ->
            val startHour = match.groupValues[1].toIntOrNull() ?: return@let
            val startMin = match.groupValues[2].toIntOrNull() ?: return@let
            val endHour = match.groupValues[3].toIntOrNull() ?: return@let
            val endMin = match.groupValues[4].toIntOrNull() ?: return@let
            if (startHour in 0..23 && startMin in 0..59 && endHour in 0..23 && endMin in 0..59) {
                return TimeResult(
                    startTime = LocalTime.of(startHour, startMin),
                    endTime = LocalTime.of(endHour, endMin),
                    matched = match.value,
                    confidence = 0.95f
                )
            }
        }

        // "om 14:00" / "at 14:00" / "@ 14:00"
        val prefixedTimeRegex = Regex("""(?:om|at|@)\s*(\d{1,2})[:.](\d{2})""")
        prefixedTimeRegex.find(input)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val min = match.groupValues[2].toIntOrNull() ?: return@let
            if (hour in 0..23 && min in 0..59) {
                return TimeResult(
                    startTime = LocalTime.of(hour, min),
                    endTime = null,
                    matched = match.value,
                    confidence = 0.9f
                )
            }
        }

        // Plain "14:00" or "14.00"
        val plainTimeRegex = Regex("""(\d{1,2})[:.](\d{2})""")
        plainTimeRegex.find(input)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val min = match.groupValues[2].toIntOrNull() ?: return@let
            if (hour in 0..23 && min in 0..59) {
                return TimeResult(
                    startTime = LocalTime.of(hour, min),
                    endTime = null,
                    matched = match.value,
                    confidence = 0.9f
                )
            }
        }

        // AM/PM: "2pm", "2 pm", "3:30pm"
        val ampmRegex = Regex("""(\d{1,2})(?::(\d{2}))?\s*([ap]m)""", RegexOption.IGNORE_CASE)
        ampmRegex.find(input)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: return@let
            val min = match.groupValues[2].toIntOrNull() ?: 0
            val ampm = match.groupValues[3].lowercase()
            if (hour !in 1..12 || min !in 0..59) return@let
            if (ampm == "pm" && hour != 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return TimeResult(
                startTime = LocalTime.of(hour, min),
                endTime = null,
                matched = match.value,
                confidence = 0.85f
            )
        }

        // "14u" or "14 uur" or "14h"
        val hourOnlyRegex = Regex("""(\d{1,2})\s*[uUhH](?:ur|our)?""")
        hourOnlyRegex.find(input)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            if (hour in 0..23) {
                return TimeResult(
                    startTime = LocalTime.of(hour, 0),
                    endTime = null,
                    matched = match.value,
                    confidence = 0.8f
                )
            }
        }

        // Day parts - Dutch
        val dayPart = when {
            Regex("""'s\s*ochtends|'s\s*morgens""").containsMatchIn(input) -> LocalTime.of(9, 0) to "'s ochtends"
            Regex("""'s\s*middags""").containsMatchIn(input) -> LocalTime.of(14, 0) to "'s middags"
            Regex("""'s\s*avonds""").containsMatchIn(input) -> LocalTime.of(19, 0) to "'s avonds"
            input.contains("ochtend") -> LocalTime.of(9, 0) to "ochtend"
            input.contains("middag") -> LocalTime.of(14, 0) to "middag"
            input.contains("avond") -> LocalTime.of(19, 0) to "avond"
            input.contains("lunch") -> LocalTime.of(12, 30) to "lunch"
            // English
            input.contains("morning") -> LocalTime.of(9, 0) to "morning"
            input.contains("afternoon") -> LocalTime.of(14, 0) to "afternoon"
            input.contains("evening") -> LocalTime.of(19, 0) to "evening"
            input.contains("noon") -> LocalTime.of(12, 0) to "noon"
            else -> null
        }

        if (dayPart != null) {
            return TimeResult(dayPart.first, null, dayPart.second, 0.6f)
        }

        return TimeResult(null, null, null, 0.0f)
    }

    // --- Location Extraction ---

    private data class LocationResult(
        val location: String?,
        val matched: String?,
        val confidence: Float
    )

    private fun extractLocation(input: String): LocationResult {
        // Words that signal end of location
        val stopWords = setOf(
            "om", "at", "morgen", "vandaag", "tomorrow", "today",
            "maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "overmorgen", "gisteren", "yesterday",
            "'s", "volgende", "aanstaande", "komende", "next"
        )

        // "bij X", "in X", "@ X", "at X" - take everything after the preposition until a stop word or end
        val locationRegex = Regex(
            """(?:^|\s)(?:bij|in|@|at)\s+(?:de\s+|het\s+)?(.+)""",
            RegexOption.IGNORE_CASE
        )

        locationRegex.find(input.lowercase())?.let { match ->
            var locationRaw = match.groupValues[1].trim()

            // Remove time patterns from location
            locationRaw = locationRaw
                .replace(Regex("""(?:om|at|@)\s*\d{1,2}[:.]?\d{0,2}.*"""), "")
                .replace(Regex("""\d{1,2}[:.](\d{2}).*"""), "")
                .replace(Regex("""\d{1,2}\s*[uUhH](?:ur|our)?.*"""), "")
                .replace(Regex("""\d{1,2}\s*[ap]m.*""", RegexOption.IGNORE_CASE), "")
                .trim()

            // Remove trailing stop words
            val words = locationRaw.split("\\s+".toRegex())
            val cleanedWords = mutableListOf<String>()
            for (word in words) {
                if (word in stopWords) break
                cleanedWords.add(word)
            }
            locationRaw = cleanedWords.joinToString(" ").trim()

            if (locationRaw.length >= 2) {
                val formatted = locationRaw.split("\\s+".toRegex())
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                return LocationResult(
                    location = formatted,
                    matched = match.value.trim(),
                    confidence = 0.8f
                )
            }
        }

        return LocationResult(null, null, 0.0f)
    }

    // --- Title Extraction ---

    private fun extractTitle(
        input: String,
        dateResult: DateResult,
        timeResult: TimeResult,
        locationResult: LocationResult
    ): String {
        var title = input

        // Remove matched components
        dateResult.matched?.let { title = title.replace(it, " ", ignoreCase = true) }
        timeResult.matched?.let { title = title.replace(it, " ", ignoreCase = true) }
        locationResult.matched?.let { title = title.replace(it, " ", ignoreCase = true) }

        // Remove leading prepositions/noise that become orphaned
        val noisePatterns = listOf(
            Regex("""^\s*(?:om|at|@|op|on)\s+""", RegexOption.IGNORE_CASE),
            Regex("""\s+(?:om|at|@)\s*$""", RegexOption.IGNORE_CASE),
        )
        for (pattern in noisePatterns) {
            title = pattern.replace(title, " ")
        }

        // Clean up whitespace
        title = title
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()

        // Capitalize first letter
        title = title.replaceFirstChar { it.uppercase() }

        // If title is empty, use the raw input
        if (title.isBlank()) {
            title = input.trim().replaceFirstChar { it.uppercase() }
        }

        return title
    }

    // --- Confidence ---

    private fun calculateConfidence(
        dateResult: DateResult,
        timeResult: TimeResult,
        title: String
    ): Float {
        var confidence = 0.0f
        var components = 0

        // Date confidence
        if (dateResult.matched != null) {
            confidence += dateResult.confidence * 0.35f
            components++
        }

        // Time confidence
        if (timeResult.startTime != null) {
            confidence += timeResult.confidence * 0.35f
            components++
        }

        // Title quality
        if (title.isNotBlank() && title.length >= 3) {
            confidence += 0.3f
            components++
        }

        // Bonus for having multiple components
        if (components >= 2) {
            confidence = (confidence + 0.1f).coerceAtMost(1.0f)
        }

        return confidence
    }
}
