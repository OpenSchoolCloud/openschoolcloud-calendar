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
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.openschoolcloud.calendar.domain.model.Family
import nl.openschoolcloud.calendar.domain.model.Profile
import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import nl.openschoolcloud.calendar.domain.repository.FamilyRepository
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import nl.openschoolcloud.calendar.presentation.screens.family.FamilySetupViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FamilySetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val familyRepo = mockk<FamilyRepository>(relaxed = true)
    private val profileRepo = mockk<ProfileRepository>(relaxed = true)
    private val accountRepo = mockk<AccountRepository>(relaxed = true)

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `save creates family with owner profile and children`() = runTest {
        coEvery { accountRepo.getDefaultAccountId() } returns "acc1"
        coEvery { familyRepo.createFamily("Gezin X", "acc1") } returns
            Family("f1", "Gezin X", "acc1", Instant.ofEpochMilli(0))
        coEvery { profileRepo.addProfile(any(), any(), any(), any()) } returns
            Profile("p", "f1", "x", 0L, null, false, SyncScope.LOCAL)

        val vm = FamilySetupViewModel(familyRepo, profileRepo, accountRepo)
        vm.onFamilyNameChange("Gezin X")
        vm.addChild("Sanne")
        var createdId = ""
        vm.save { createdId = it }
        advanceUntilIdle()

        assertEquals("f1", createdId)
        // one owner profile + one child = 2 addProfile calls
        io.mockk.coVerify(exactly = 2) { profileRepo.addProfile(any(), any(), any(), any()) }
    }
}
