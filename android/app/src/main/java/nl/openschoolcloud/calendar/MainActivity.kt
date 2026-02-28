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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.data.remote.auth.CredentialStorage
import nl.openschoolcloud.calendar.domain.repository.CalendarRepository
import nl.openschoolcloud.calendar.notification.NotificationHelper
import nl.openschoolcloud.calendar.presentation.navigation.AppNavigation
import nl.openschoolcloud.calendar.presentation.theme.OpenSchoolCloudCalendarTheme
import nl.openschoolcloud.calendar.widget.NextEventWidget
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var credentialStorage: CredentialStorage

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var calendarRepository: CalendarRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val hasAccount = try {
            credentialStorage.hasAnyAccount()
        } catch (e: Exception) {
            false
        }
        val onboardingCompleted = appPreferences.onboardingCompleted
        val hasCompletedModeSelection = appPreferences.hasCompletedModeSelection
        val isStandaloneMode = appPreferences.isStandaloneMode

        // Ensure local calendar exists for standalone users
        if (isStandaloneMode) {
            lifecycleScope.launch {
                calendarRepository.ensureLocalCalendarExists()
            }
        }

        val themeMode = appPreferences.themeMode

        // Handle deep link intents from widgets
        val deepLinkEventId = when (intent?.action) {
            NotificationHelper.ACTION_VIEW_EVENT,
            NotificationHelper.ACTION_REFLECT_EVENT,
            NextEventWidget.ACTION_REFLECT_EVENT ->
                intent.getStringExtra(NotificationHelper.EXTRA_EVENT_ID)
            else -> null
        }

        setContent {
            OpenSchoolCloudCalendarTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        hasAccount = hasAccount,
                        onboardingCompleted = onboardingCompleted,
                        hasCompletedModeSelection = hasCompletedModeSelection,
                        isStandaloneMode = isStandaloneMode,
                        deepLinkEventId = deepLinkEventId
                    )
                }
            }
        }
    }
}
