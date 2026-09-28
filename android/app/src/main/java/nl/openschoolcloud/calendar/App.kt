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
package nl.openschoolcloud.calendar

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.data.remote.osccloud.OscCloudSyncProvider
import nl.openschoolcloud.calendar.data.sync.OscCloudStatusWorker
import nl.openschoolcloud.calendar.domain.repository.FamilyRepository
import nl.openschoolcloud.calendar.domain.repository.HolidayRepository
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import nl.openschoolcloud.calendar.notification.NotificationHelper
import nl.openschoolcloud.calendar.notification.PlanningReminderWorker
import nl.openschoolcloud.calendar.notification.ReminderWorker
import nl.openschoolcloud.calendar.util.FeatureFlags
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var holidayRepository: HolidayRepository

    @Inject
    lateinit var familyRepository: FamilyRepository

    @Inject
    lateinit var profileRepository: ProfileRepository

    @Inject
    lateinit var oscCloudSyncProvider: OscCloudSyncProvider

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        initAcra {
            reportFormat = StringFormat.KEY_VALUE_LIST
            reportContent = listOf(
                org.acra.ReportField.APP_VERSION_CODE,
                org.acra.ReportField.APP_VERSION_NAME,
                org.acra.ReportField.ANDROID_VERSION,
                org.acra.ReportField.PHONE_MODEL,
                org.acra.ReportField.STACK_TRACE,
                org.acra.ReportField.USER_COMMENT
            )
            dialog {
                title = getString(R.string.crash_dialog_title)
                text = getString(R.string.crash_dialog_text)
                commentPrompt = getString(R.string.crash_dialog_comment)
                resIcon = R.drawable.ic_osc_logo
            }
            mailSender {
                mailTo = "support@openschoolcloud.nl"
                reportAsFile = true
                reportFileName = "osc_calendar_crash.txt"
                subject = "OSC Calendar - Crashrapport"
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createNotificationChannel()
        notificationHelper.createReflectionChannel()
        notificationHelper.createPlanningChannel()
        ReminderWorker.schedule(this)
        PlanningReminderWorker.schedule(this)

        // Seed holiday calendars on background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                holidayRepository.seedIfNeeded()
            } catch (e: Exception) {
                // Seeding is non-critical; don't crash the app
            }
        }

        // OSC-Cloud soft-degrade: periodic check + one opportunistic check at start.
        // Stays fully dark until ENABLE_OSC_CLOUD flips on for go-live.
        if (FeatureFlags.oscCloudEnabled) {
            OscCloudStatusWorker.schedulePeriodic(this)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    OscCloudStatusWorker.checkAndDegradeIfNeeded(
                        familyRepository, profileRepository, oscCloudSyncProvider
                    )
                } catch (e: Exception) {
                    // Non-critical; the periodic worker will retry later.
                }
            }
        }
    }
}
