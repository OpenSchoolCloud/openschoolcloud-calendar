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
package nl.openschoolcloud.calendar.data.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import nl.openschoolcloud.calendar.data.remote.osccloud.OscCloudStatus
import nl.openschoolcloud.calendar.data.remote.osccloud.OscCloudSyncProvider
import nl.openschoolcloud.calendar.domain.model.Family
import nl.openschoolcloud.calendar.domain.model.Profile
import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.repository.FamilyRepository
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import org.junit.Test
import java.time.Instant

/**
 * Verifies the decision rule wired into [OscCloudStatusWorker]: only call
 * [OscCloudSyncProvider.refreshAndMaybeDegrade] when a family exists with at
 * least one profile on OSC_CLOUD sync. The [OscCloudSyncProvider] itself
 * (and [nl.openschoolcloud.calendar.data.remote.osccloud.shouldDegradeToLocal])
 * are covered separately in OscCloudSyncProviderTest / OscCloudModelsTest.
 */
class OscCloudStatusWorkerTest {

    private val familyRepository = mockk<FamilyRepository>()
    private val profileRepository = mockk<ProfileRepository>()
    private val oscCloudSyncProvider = mockk<OscCloudSyncProvider>()

    private val family = Family("f1", "De Vries", "owner-1", Instant.now())

    @Test fun `checkAndDegradeIfNeeded refreshes when a profile is on OSC_CLOUD`() = runTest {
        every { familyRepository.observeFamily() } returns flowOf(family)
        coEvery { profileRepository.observeProfilesOnce("f1") } returns listOf(
            Profile("p1", "f1", "Sanne", 0L, null, true, SyncScope.OSC_CLOUD)
        )
        coEvery { oscCloudSyncProvider.refreshAndMaybeDegrade("f1") } returns
            Result.success(OscCloudStatus("ACTIVE", 0, 1, null))

        OscCloudStatusWorker.checkAndDegradeIfNeeded(familyRepository, profileRepository, oscCloudSyncProvider)

        coVerify { oscCloudSyncProvider.refreshAndMaybeDegrade("f1") }
    }

    @Test fun `checkAndDegradeIfNeeded does nothing when no profile is on OSC_CLOUD`() = runTest {
        every { familyRepository.observeFamily() } returns flowOf(family)
        coEvery { profileRepository.observeProfilesOnce("f1") } returns listOf(
            Profile("p1", "f1", "Sanne", 0L, null, true, SyncScope.LOCAL)
        )

        OscCloudStatusWorker.checkAndDegradeIfNeeded(familyRepository, profileRepository, oscCloudSyncProvider)

        coVerify(exactly = 0) { oscCloudSyncProvider.refreshAndMaybeDegrade(any()) }
    }

    @Test fun `checkAndDegradeIfNeeded does nothing when there is no family`() = runTest {
        every { familyRepository.observeFamily() } returns flowOf(null)

        OscCloudStatusWorker.checkAndDegradeIfNeeded(familyRepository, profileRepository, oscCloudSyncProvider)

        coVerify(exactly = 0) { profileRepository.observeProfilesOnce(any()) }
        coVerify(exactly = 0) { oscCloudSyncProvider.refreshAndMaybeDegrade(any()) }
    }
}
