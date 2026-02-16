/*
 * OpenSchoolCloud Calendar
 * Copyright (C) 2025 OpenSchoolCloud / Aldewereld Consultancy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package nl.openschoolcloud.calendar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import nl.openschoolcloud.calendar.data.local.dao.AccountDao
import nl.openschoolcloud.calendar.data.local.dao.CalendarDao
import nl.openschoolcloud.calendar.data.local.dao.EventDao
import nl.openschoolcloud.calendar.data.local.dao.HolidayDao
import nl.openschoolcloud.calendar.data.local.entity.AccountEntity
import nl.openschoolcloud.calendar.data.local.entity.CalendarEntity
import nl.openschoolcloud.calendar.data.local.entity.EventEntity
import nl.openschoolcloud.calendar.data.local.entity.HolidayCalendarEntity
import nl.openschoolcloud.calendar.data.local.entity.HolidayEventEntity

/**
 * Room database for OpenSchoolCloud Calendar
 */
@Database(
    entities = [
        AccountEntity::class,
        CalendarEntity::class,
        EventEntity::class,
        HolidayCalendarEntity::class,
        HolidayEventEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao
    abstract fun holidayDao(): HolidayDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `holiday_calendars` (
                        `id` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `holiday_events` (
                        `id` TEXT NOT NULL,
                        `calendarId` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `classroomTip` TEXT,
                        `date` INTEGER NOT NULL,
                        `endDate` INTEGER,
                        `dateCalculationType` TEXT NOT NULL,
                        `easterOffset` INTEGER,
                        `fixedMonth` INTEGER,
                        `fixedDay` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`calendarId`) REFERENCES `holiday_calendars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                database.execSQL("CREATE INDEX IF NOT EXISTS `index_holiday_events_calendarId` ON `holiday_events` (`calendarId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_holiday_events_date` ON `holiday_events` (`date`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_holiday_events_endDate` ON `holiday_events` (`endDate`)")
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun instantToTimestamp(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        return value?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    @TypeConverter
    fun toStringList(list: List<String>): String {
        return list.joinToString(",")
    }
}
