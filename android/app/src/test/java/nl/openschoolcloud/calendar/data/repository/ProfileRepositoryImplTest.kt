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
package nl.openschoolcloud.calendar.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import nl.openschoolcloud.calendar.data.local.dao.ProfileDao
import nl.openschoolcloud.calendar.data.local.entity.ProfileEntity
import nl.openschoolcloud.calendar.domain.model.SyncScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryImplTest {

    private val dao = mockk<ProfileDao>(relaxed = true)
    private val repo = ProfileRepositoryImpl(dao)

    @Test fun `addProfile inserts a profile with given scope defaulting to LOCAL`() = runTest {
        val captured = slot<ProfileEntity>()
        coEvery { dao.insert(capture(captured)) } returns Unit

        val profile = repo.addProfile("f1", "Sanne", 0xFF123456L, isOwner = false)

        assertEquals("f1", captured.captured.familyId)
        assertEquals("Sanne", captured.captured.displayName)
        assertEquals("LOCAL", captured.captured.syncScope)
        assertTrue(!captured.captured.isOwnerProfile)
        assertEquals(SyncScope.LOCAL, profile.syncScope)
    }

    @Test fun `setSyncScope updates the stored profile scope`() = runTest {
        coEvery { dao.getById("p1") } returns
            ProfileEntity("p1", "f1", "Sanne", 0L, null, false, "LOCAL")

        repo.setSyncScope("p1", SyncScope.OSC_CLOUD)

        coVerify { dao.update(match { it.syncScope == "OSC_CLOUD" }) }
    }
}
