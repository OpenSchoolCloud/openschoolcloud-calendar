package nl.openschoolcloud.calendar.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ParseNaturalLanguageUseCaseTest {

    private lateinit var useCase: ParseNaturalLanguageUseCase

    @Before
    fun setup() {
        useCase = ParseNaturalLanguageUseCase()
    }

    // --- Dutch date parsing ---

    @Test
    fun `parse morgen returns tomorrow`() {
        val result = useCase.parse("Morgen 14:00 koffie met Lisa")

        assertEquals(LocalDate.now().plusDays(1), result.startDate)
        assertEquals(LocalTime.of(14, 0), result.startTime)
        assertTrue(result.title.contains("koffie", ignoreCase = true) || result.title.contains("Lisa", ignoreCase = true))
    }

    @Test
    fun `parse vandaag returns today`() {
        val result = useCase.parse("vandaag vergadering")

        assertEquals(LocalDate.now(), result.startDate)
    }

    @Test
    fun `parse overmorgen returns day after tomorrow`() {
        val result = useCase.parse("overmorgen 9:00-11:00 workshop")

        assertEquals(LocalDate.now().plusDays(2), result.startDate)
        assertEquals(LocalTime.of(9, 0), result.startTime)
        assertEquals(LocalTime.of(11, 0), result.endTime)
    }

    @Test
    fun `parse vrijdag returns next friday`() {
        val result = useCase.parse("Vrijdag teamoverleg")

        assertEquals(DayOfWeek.FRIDAY, result.startDate?.dayOfWeek)
        assertTrue(result.startDate!! > LocalDate.now() || result.startDate == LocalDate.now())
    }

    @Test
    fun `parse volgende week returns one week from now`() {
        val result = useCase.parse("volgende week brainstorm")

        assertEquals(LocalDate.now().plusWeeks(1), result.startDate)
    }

    // --- English date parsing ---

    @Test
    fun `parse tomorrow returns tomorrow`() {
        val result = useCase.parse("Tomorrow 2pm meeting with John")

        assertEquals(LocalDate.now().plusDays(1), result.startDate)
    }

    @Test
    fun `parse today returns today`() {
        val result = useCase.parse("today standup")

        assertEquals(LocalDate.now(), result.startDate)
    }

    @Test
    fun `parse english weekday`() {
        val result = useCase.parse("monday team sync")

        assertEquals(DayOfWeek.MONDAY, result.startDate?.dayOfWeek)
    }

    // --- Time parsing ---

    @Test
    fun `parse time with colon`() {
        val result = useCase.parse("morgen 14:00 koffie")

        assertEquals(LocalTime.of(14, 0), result.startTime)
    }

    @Test
    fun `parse time range with dash`() {
        val result = useCase.parse("morgen 14:00-15:30 vergadering")

        assertEquals(LocalTime.of(14, 0), result.startTime)
        assertEquals(LocalTime.of(15, 30), result.endTime)
    }

    @Test
    fun `parse time with u suffix`() {
        val result = useCase.parse("Meeting om 10u")

        assertEquals(LocalTime.of(10, 0), result.startTime)
    }

    @Test
    fun `parse am pm time`() {
        val result = useCase.parse("Tomorrow 2pm call with client")

        assertEquals(LocalTime.of(14, 0), result.startTime)
    }

    @Test
    fun `parse am time`() {
        val result = useCase.parse("meeting at 9am")

        assertEquals(LocalTime.of(9, 0), result.startTime)
    }

    @Test
    fun `parse day part ochtend`() {
        val result = useCase.parse("'s ochtends vergadering")

        assertEquals(LocalTime.of(9, 0), result.startTime)
    }

    @Test
    fun `parse day part middags`() {
        val result = useCase.parse("'s middags vergadering")

        assertEquals(LocalTime.of(14, 0), result.startTime)
    }

    @Test
    fun `parse day part afternoon`() {
        val result = useCase.parse("afternoon meeting")

        assertEquals(LocalTime.of(14, 0), result.startTime)
    }

    // --- Location parsing ---

    @Test
    fun `parse location with bij`() {
        val result = useCase.parse("Meeting bij Starbucks om 10:00")

        assertEquals("Starbucks", result.location)
        assertEquals(LocalTime.of(10, 0), result.startTime)
    }

    @Test
    fun `parse location with at`() {
        val result = useCase.parse("meeting at Office om 14:00")

        assertEquals("Office", result.location)
    }

    @Test
    fun `parse location with in`() {
        val result = useCase.parse("vergadering in Lokaal 2.13")

        assertNotNull(result.location)
    }

    // --- Absolute date parsing ---

    @Test
    fun `parse absolute date with dutch month`() {
        val result = useCase.parse("15 januari lunch")

        assertEquals(1, result.startDate?.monthValue)
        assertEquals(15, result.startDate?.dayOfMonth)
    }

    @Test
    fun `parse absolute date with short month`() {
        val result = useCase.parse("3 feb meeting")

        assertEquals(2, result.startDate?.monthValue)
        assertEquals(3, result.startDate?.dayOfMonth)
    }

    @Test
    fun `parse numeric date dd-mm`() {
        val result = useCase.parse("15-01 lunch")

        assertEquals(1, result.startDate?.monthValue)
        assertEquals(15, result.startDate?.dayOfMonth)
    }

    @Test
    fun `parse numeric date dd-mm-yyyy`() {
        val result = useCase.parse("15-06-2026 meeting")

        assertEquals(2026, result.startDate?.year)
        assertEquals(6, result.startDate?.monthValue)
        assertEquals(15, result.startDate?.dayOfMonth)
    }

    @Test
    fun `parse iso date`() {
        val result = useCase.parse("2026-03-20 conference")

        assertEquals(2026, result.startDate?.year)
        assertEquals(3, result.startDate?.monthValue)
        assertEquals(20, result.startDate?.dayOfMonth)
    }

    // --- Title extraction ---

    @Test
    fun `title extracts remaining text after removing date and time`() {
        val result = useCase.parse("morgen 14:00 koffie met Lisa")

        assertTrue(result.title.isNotBlank())
        // Title should contain the event description, not date/time
        assertTrue(
            result.title.contains("koffie", ignoreCase = true) ||
            result.title.contains("Lisa", ignoreCase = true)
        )
    }

    @Test
    fun `title is capitalized`() {
        val result = useCase.parse("morgen vergadering")

        assertTrue(result.title.first().isUpperCase())
    }

    // --- Edge cases ---

    @Test
    fun `handles empty input gracefully`() {
        val result = useCase.parse("")

        assertNotNull(result)
        assertEquals(LocalDate.now(), result.startDate)
        assertEquals(0.0f, result.confidence)
    }

    @Test
    fun `handles gibberish gracefully`() {
        val result = useCase.parse("asdfghjkl")

        assertNotNull(result)
        assertTrue(result.confidence < 0.5f)
    }

    @Test
    fun `handles whitespace only input`() {
        val result = useCase.parse("   ")

        assertNotNull(result)
        assertEquals(0.0f, result.confidence)
    }

    @Test
    fun `no date defaults to today`() {
        val result = useCase.parse("koffie met Lisa")

        assertEquals(LocalDate.now(), result.startDate)
    }

    @Test
    fun `no time returns null startTime`() {
        val result = useCase.parse("morgen vergadering")

        assertNull(result.startTime)
    }

    @Test
    fun `no location returns null`() {
        val result = useCase.parse("morgen 14:00 vergadering")

        assertNull(result.location)
    }

    // --- Confidence ---

    @Test
    fun `full input has high confidence`() {
        val result = useCase.parse("morgen 14:00 koffie met Lisa bij Starbucks")

        assertTrue(result.confidence > 0.5f)
    }

    @Test
    fun `date and time both present gives higher confidence`() {
        val result = useCase.parse("morgen 14:00 meeting")

        assertTrue(result.confidence > 0.6f)
    }

    @Test
    fun `rawInput is preserved`() {
        val input = "Morgen 14:00 koffie"
        val result = useCase.parse(input)

        assertEquals(input, result.rawInput)
    }

    // --- Duration ---

    @Test
    fun `default duration is 1 hour`() {
        val result = useCase.parse("morgen 14:00 meeting")

        assertEquals(java.time.Duration.ofHours(1), result.duration)
    }

    @Test
    fun `time range sets correct duration`() {
        val result = useCase.parse("morgen 14:00-15:30 vergadering")

        assertEquals(java.time.Duration.ofMinutes(90), result.duration)
    }
}
