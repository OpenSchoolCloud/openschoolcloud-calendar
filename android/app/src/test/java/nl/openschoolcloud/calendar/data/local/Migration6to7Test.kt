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

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import nl.openschoolcloud.calendar.data.local.entity.FamilyEntity
import nl.openschoolcloud.calendar.data.local.entity.ProfileEntity

@RunWith(RobolectricTestRunner::class)
class Migration6to7Test {
    private lateinit var context: Context
    private val dbName = "migration67.db"

    @Before fun setup() { context = ApplicationProvider.getApplicationContext() }
    @After fun teardown() { context.deleteDatabase(dbName) }

    /** Build a real v6 database file with every table that exists at schema
     *  version 6 (Room validates the full schema on open, not just the tables
     *  the test touches), seed a legacy account + event row, and close it. */
    private fun createV6Database() {
        val callback = object : SupportSQLiteOpenHelper.Callback(6) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `accounts` (`id` TEXT NOT NULL, `serverUrl` TEXT NOT NULL, " +
                        "`username` TEXT NOT NULL, `displayName` TEXT, `email` TEXT, " +
                        "`principalUrl` TEXT, `calendarHomeSet` TEXT, `isDefault` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE `calendars` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, `color` INTEGER NOT NULL, `url` TEXT NOT NULL, " +
                        "`ctag` TEXT, `syncToken` TEXT, `readOnly` INTEGER NOT NULL, " +
                        "`visible` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "`ownerDisplayName` TEXT DEFAULT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendars_accountId` ON `calendars` (`accountId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `contacts` (
                        `id` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `email` TEXT,
                        `emailsJson` TEXT,
                        `phone` TEXT,
                        `organization` TEXT,
                        `photoUri` TEXT,
                        `vcardEtag` TEXT,
                        `href` TEXT,
                        `lastSynced` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_accountId` ON `contacts` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_email` ON `contacts` (`email`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_displayName` ON `contacts` (`displayName`)")
                db.execSQL(
                    "CREATE TABLE `events` (`uid` TEXT NOT NULL, `calendarId` TEXT NOT NULL, " +
                        "`summary` TEXT NOT NULL, `description` TEXT, `location` TEXT, " +
                        "`dtStart` INTEGER NOT NULL, `dtEnd` INTEGER, `allDay` INTEGER NOT NULL, " +
                        "`timeZone` TEXT NOT NULL, `rrule` TEXT, `colorOverride` INTEGER, " +
                        "`organizerEmail` TEXT, `organizerName` TEXT, `attendeesJson` TEXT, " +
                        "`remindersJson` TEXT, `status` TEXT NOT NULL, `created` INTEGER, " +
                        "`lastModified` INTEGER, `etag` TEXT, `syncStatus` TEXT NOT NULL, " +
                        "`rawIcal` TEXT, `isLearningAgenda` INTEGER NOT NULL DEFAULT 0, " +
                        "`learningGoal` TEXT DEFAULT NULL, `learningNeeds` TEXT DEFAULT NULL, " +
                        "`eventType` TEXT NOT NULL DEFAULT 'STANDARD', " +
                        "`taskCompleted` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`uid`), " +
                        "FOREIGN KEY(`calendarId`) REFERENCES `calendars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_calendarId` ON `events` (`calendarId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_dtStart` ON `events` (`dtStart`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_dtEnd` ON `events` (`dtEnd`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `holiday_calendars` (
                        `id` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
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
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_holiday_events_calendarId` ON `holiday_events` (`calendarId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_holiday_events_date` ON `holiday_events` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_holiday_events_endDate` ON `holiday_events` (`endDate`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reflection_entries` (
                        `id` TEXT NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `mood` INTEGER NOT NULL,
                        `whatWentWell` TEXT,
                        `whatToDoBetter` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`eventId`) REFERENCES `events`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reflection_entries_eventId` ON `reflection_entries` (`eventId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reflection_entries_createdAt` ON `reflection_entries` (`createdAt`)")

                db.execSQL(
                    "INSERT INTO accounts (id, serverUrl, username, isDefault) " +
                        "VALUES ('acc1','https://nc.example','bob', 1)"
                )
                db.execSQL(
                    "INSERT INTO calendars (id, accountId, displayName, color, url, ctag, syncToken, " +
                        "readOnly, visible, sortOrder) VALUES " +
                        "('cal1','acc1','Test Calendar',0,'https://nc.example/cal1',NULL,NULL,0,1,0)"
                )
                db.execSQL(
                    "INSERT INTO events (uid, calendarId, summary, description, location, dtStart, " +
                        "dtEnd, allDay, timeZone, rrule, colorOverride, organizerEmail, organizerName, " +
                        "attendeesJson, remindersJson, status, created, lastModified, etag, syncStatus, rawIcal) " +
                        "VALUES ('evt1','cal1','Legacy Event',NULL,NULL,0,NULL,0,'UTC',NULL,NULL,NULL,NULL," +
                        "NULL,NULL,'CONFIRMED',NULL,NULL,NULL,'SYNCED',NULL)"
                )
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName).callback(callback).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.close() // triggers onCreate at v6
    }

    @Test
    fun realMigration6to7RunsAndPreservesData() = runBlocking {
        createV6Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()

        // Force the DB to open, which runs MIGRATION_6_7 against the real v6 file.
        db.query("SELECT profileId FROM events", emptyArray()).use { c ->
            assertEquals(1, c.count) // legacy event row survived
        }
        db.query("SELECT oscSub, authProvider, mfaEnrolled FROM accounts", emptyArray()).use { c ->
            assertEquals(1, c.count) // legacy account row survived, new columns present
        }

        // New tables + DAOs work post-migration.
        db.familyDao().insert(FamilyEntity("f1", "Gezin Test", "acc1", 0L))
        db.profileDao().insert(ProfileEntity("p1", "f1", "Ouder", 0xFF3B9FD9L, null, true, "LOCAL"))
        assertEquals(1, db.profileDao().getByFamilySync("f1").size)
        assertNotNull(db.familyDao().getByOwner("acc1"))
        db.close()
    }
}
