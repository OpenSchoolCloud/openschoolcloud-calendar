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
package nl.openschoolcloud.calendar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FamilyModelTest {
    @Test
    fun `profile defaults are local scope and non-owner constructible`() {
        val profile = Profile(
            id = "p1",
            familyId = "f1",
            displayName = "Sanne",
            color = 0xFF3B9FD9L,
            avatar = null,
            isOwnerProfile = false,
            syncScope = SyncScope.LOCAL
        )
        assertEquals(SyncScope.LOCAL, profile.syncScope)
        assertTrue(!profile.isOwnerProfile)
    }

    @Test
    fun `family carries owner account id`() {
        val family = Family("f1", "Gezin Aldewereld", "acc1", Instant.ofEpochMilli(0))
        assertEquals("acc1", family.ownerAccountId)
    }
}
