/*
 * OSC Calendar - Privacy-first calendar for Dutch education
 * Copyright (C) 2025 Aldewereld Consultancy (OpenSchoolCloud)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 */
package nl.openschoolcloud.calendar.data.repository

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.data.local.HolidayCalendarJson
import nl.openschoolcloud.calendar.data.local.dao.HolidayDao
import nl.openschoolcloud.calendar.data.local.entity.HolidayCalendarEntity
import nl.openschoolcloud.calendar.data.local.entity.HolidayEventEntity
import nl.openschoolcloud.calendar.domain.model.DateCalculationType
import nl.openschoolcloud.calendar.domain.model.HolidayCalendar
import nl.openschoolcloud.calendar.domain.model.HolidayCategory
import nl.openschoolcloud.calendar.domain.model.HolidayEvent
import nl.openschoolcloud.calendar.domain.repository.HolidayRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HolidayRepositoryImpl @Inject constructor(
    private val holidayDao: HolidayDao,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context
) : HolidayRepository {

    override fun getHolidayCalendars(): Flow<List<HolidayCalendar>> {
        return holidayDao.getAllCalendars().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEnabledCalendars(): Flow<List<HolidayCalendar>> {
        return holidayDao.getEnabledCalendars().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getHolidayEvents(start: LocalDate, end: LocalDate): Flow<List<HolidayEvent>> {
        val startMillis = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endMillis = end.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return holidayDao.getEventsInRange(startMillis, endMillis).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getHolidayEvent(id: String): HolidayEvent? {
        return holidayDao.getEventById(id)?.toDomain()
    }

    override suspend fun setCalendarEnabled(id: String, enabled: Boolean) {
        holidayDao.setCalendarEnabled(id, enabled)
    }

    override suspend fun seedIfNeeded() {
        if (appPreferences.holidaySeedVersion >= CURRENT_HOLIDAY_SEED_VERSION) return

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(HolidayCalendarJson::class.java)

        val jsonFiles = listOf(
            "holidays/dutch_national.json",
            "holidays/christian.json",
            "holidays/islamic.json",
            "holidays/jewish.json",
            "holidays/hindu.json",
            "holidays/chinese_asian.json",
            "holidays/international.json"
        )

        val calendarEntities = mutableListOf<HolidayCalendarEntity>()
        val eventEntities = mutableListOf<HolidayEventEntity>()

        for (file in jsonFiles) {
            val json = context.assets.open(file).bufferedReader().use { it.readText() }
            val calendarJson = adapter.fromJson(json) ?: continue

            val isDefault = calendarJson.calendarId == "dutch_national"

            calendarEntities.add(
                HolidayCalendarEntity(
                    id = calendarJson.calendarId,
                    category = calendarJson.category,
                    displayName = calendarJson.displayName,
                    description = calendarJson.description,
                    enabled = isDefault
                )
            )

            for (eventJson in calendarJson.events) {
                val calcType = try {
                    DateCalculationType.valueOf(eventJson.dateCalculationType)
                } catch (e: IllegalArgumentException) {
                    DateCalculationType.FIXED
                }

                val dates = generateDates(calcType, eventJson, YEAR_START, YEAR_END)

                for (date in dates) {
                    eventEntities.add(
                        HolidayEventEntity(
                            id = "${calendarJson.calendarId}_${eventJson.titleKey}_${date.year}",
                            calendarId = calendarJson.calendarId,
                            category = calendarJson.category,
                            title = eventJson.title,
                            description = eventJson.description,
                            classroomTip = eventJson.classroomTip,
                            date = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                            endDate = null,
                            dateCalculationType = eventJson.dateCalculationType,
                            easterOffset = eventJson.easterOffset,
                            fixedMonth = eventJson.fixedMonth,
                            fixedDay = eventJson.fixedDay
                        )
                    )
                }
            }
        }

        // Clear old data and insert fresh
        holidayDao.deleteAllEvents()
        holidayDao.insertCalendars(calendarEntities)
        holidayDao.insertEvents(eventEntities)

        appPreferences.holidaySeedVersion = CURRENT_HOLIDAY_SEED_VERSION
    }

    private fun generateDates(
        calcType: DateCalculationType,
        eventJson: nl.openschoolcloud.calendar.data.local.HolidayEventJson,
        yearStart: Int,
        yearEnd: Int
    ): List<LocalDate> {
        return when (calcType) {
            DateCalculationType.FIXED -> {
                val month = eventJson.fixedMonth ?: return emptyList()
                val day = eventJson.fixedDay ?: return emptyList()
                (yearStart..yearEnd).map { year -> LocalDate.of(year, month, day) }
            }
            DateCalculationType.EASTER_BASED -> {
                val offset = eventJson.easterOffset ?: return emptyList()
                (yearStart..yearEnd).map { year ->
                    calculateEasterSunday(year).plusDays(offset.toLong())
                }
            }
            DateCalculationType.PRECOMPUTED -> {
                val dates = eventJson.precomputedDates ?: return emptyList()
                (yearStart..yearEnd).mapNotNull { year ->
                    dates[year.toString()]?.let { LocalDate.parse(it) }
                }
            }
        }
    }

    companion object {
        const val CURRENT_HOLIDAY_SEED_VERSION = 1
        private const val YEAR_START = 2025
        private const val YEAR_END = 2030

        /**
         * Anonymous Gregorian algorithm for computing Easter Sunday.
         * Verified: 2025=Apr 20, 2026=Apr 5, 2027=Mar 28
         */
        fun calculateEasterSunday(year: Int): LocalDate {
            val a = year % 19
            val b = year / 100
            val c = year % 100
            val d = b / 4
            val e = b % 4
            val f = (b + 8) / 25
            val g = (b - f + 1) / 3
            val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4
            val k = c % 4
            val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val month = (h + l - 7 * m + 114) / 31
            val day = ((h + l - 7 * m + 114) % 31) + 1
            return LocalDate.of(year, month, day)
        }
    }
}

private fun HolidayCalendarEntity.toDomain(): HolidayCalendar {
    return HolidayCalendar(
        id = id,
        category = try {
            HolidayCategory.valueOf(category)
        } catch (e: IllegalArgumentException) {
            HolidayCategory.INTERNATIONAL
        },
        displayName = displayName,
        description = description,
        enabled = enabled
    )
}

private fun HolidayEventEntity.toDomain(): HolidayEvent {
    return HolidayEvent(
        id = id,
        calendarId = calendarId,
        category = try {
            HolidayCategory.valueOf(category)
        } catch (e: IllegalArgumentException) {
            HolidayCategory.INTERNATIONAL
        },
        title = title,
        description = description,
        classroomTip = classroomTip,
        date = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate(),
        endDate = endDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() },
        dateCalculationType = try {
            DateCalculationType.valueOf(dateCalculationType)
        } catch (e: IllegalArgumentException) {
            DateCalculationType.FIXED
        },
        easterOffset = easterOffset,
        fixedMonth = fixedMonth,
        fixedDay = fixedDay
    )
}
