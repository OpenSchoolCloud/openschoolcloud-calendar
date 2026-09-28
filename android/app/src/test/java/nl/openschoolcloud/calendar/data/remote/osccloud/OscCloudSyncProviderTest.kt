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
package nl.openschoolcloud.calendar.data.remote.osccloud

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.openschoolcloud.calendar.domain.model.Account
import nl.openschoolcloud.calendar.domain.model.Profile
import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class OscCloudSyncProviderTest {

    private val api = mockk<OscCloudApi>()
    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val profileRepository = mockk<ProfileRepository>()
    private val provider = OscCloudSyncProvider(api, accountRepository, profileRepository)

    private val profiles = listOf(
        Profile("p1", "f1", "Sanne", 0L, null, true, SyncScope.LOCAL),
        Profile("p2", "f1", "Milo", 0L, null, false, SyncScope.LOCAL)
    )

    @Test fun `startTrialAndActivate links the caldav account and moves every profile to OSC_CLOUD`() = runTest {
        val status = OscCloudStatus("TRIAL", 30, 2, "https://sync.osc.nl/dav/f1/", "app-pw")
        coEvery { api.startTrial("f1", 2) } returns Result.success(status)
        coEvery { accountRepository.addAccount(any(), any(), any()) } returns Result.success(mockk<Account>())
        coEvery { profileRepository.observeProfilesOnce("f1") } returns profiles
        coEvery { profileRepository.setSyncScope(any(), any()) } returns Unit

        val result = provider.startTrialAndActivate("f1", 2)

        assertTrue(result.isSuccess)
        coVerify { accountRepository.addAccount("https://sync.osc.nl/dav/f1/", any(), "app-pw") }
        coVerify { profileRepository.setSyncScope("p1", SyncScope.OSC_CLOUD) }
        coVerify { profileRepository.setSyncScope("p2", SyncScope.OSC_CLOUD) }
    }

    @Test fun `startTrialAndActivate fails without linking an account when the caldav credentials are missing`() = runTest {
        val status = OscCloudStatus("TRIAL", 30, 2, caldavUrl = null, appPassword = null)
        coEvery { api.startTrial("f1", 2) } returns Result.success(status)
        coEvery { profileRepository.observeProfilesOnce("f1") } returns profiles
        coEvery { profileRepository.setSyncScope(any(), any()) } returns Unit

        val result = provider.startTrialAndActivate("f1", 2)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { accountRepository.addAccount(any(), any(), any()) }
    }

    @Test fun `refreshAndMaybeDegrade soft-degrades every profile to LOCAL when the status is PAUSED`() = runTest {
        val status = OscCloudStatus("PAUSED", 0, 2, "https://sync.osc.nl/dav/f1/")
        coEvery { api.status() } returns Result.success(status)
        coEvery { profileRepository.observeProfilesOnce("f1") } returns profiles
        coEvery { profileRepository.setSyncScope(any(), any()) } returns Unit

        provider.refreshAndMaybeDegrade("f1")

        coVerify { profileRepository.setSyncScope("p1", SyncScope.LOCAL) }
        coVerify { profileRepository.setSyncScope("p2", SyncScope.LOCAL) }
    }

    @Test fun `refreshAndMaybeDegrade leaves profiles untouched when the subscription is ACTIVE`() = runTest {
        val status = OscCloudStatus("ACTIVE", 25, 2, "https://sync.osc.nl/dav/f1/")
        coEvery { api.status() } returns Result.success(status)

        provider.refreshAndMaybeDegrade("f1")

        coVerify(exactly = 0) { profileRepository.observeProfilesOnce(any()) }
        coVerify(exactly = 0) { profileRepository.setSyncScope(any(), any()) }
    }
}
