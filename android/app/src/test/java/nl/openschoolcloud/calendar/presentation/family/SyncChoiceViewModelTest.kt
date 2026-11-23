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
package nl.openschoolcloud.calendar.presentation.family

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.data.remote.osccloud.OscCloudCoordinator
import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.repository.CalendarRepository
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import nl.openschoolcloud.calendar.presentation.screens.family.SyncChoiceViewModel
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncChoiceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val profileRepo = mockk<ProfileRepository>(relaxed = true)
    private val prefs = mockk<AppPreferences>(relaxed = true)
    private val coordinator = mockk<OscCloudCoordinator>(relaxed = true)
    private val calendarRepo = mockk<CalendarRepository>(relaxed = true)

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SyncChoiceViewModel(profileRepo, prefs, coordinator, calendarRepo)

    @Test fun `choosing local creates the local calendar before finishing`() = runTest {
        coEvery { profileRepo.observeProfilesOnce("f1") } returns emptyList()
        vm().choose(SyncScope.LOCAL, "f1") {}
        advanceUntilIdle()
        coVerify(exactly = 1) { calendarRepo.ensureLocalCalendarExists() }
    }

    @Test fun `choosing nextcloud does not create the local calendar`() = runTest {
        coEvery { profileRepo.observeProfilesOnce("f1") } returns emptyList()
        vm().choose(SyncScope.OWN_NEXTCLOUD, "f1") {}
        advanceUntilIdle()
        coVerify(exactly = 0) { calendarRepo.ensureLocalCalendarExists() }
    }
}
