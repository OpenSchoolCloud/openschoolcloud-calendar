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
package nl.openschoolcloud.calendar.presentation.calendar

import nl.openschoolcloud.calendar.presentation.screens.calendar.eventVisibleForProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFilterTest {
    @Test fun `null selection shows all profiles`() {
        assertTrue(eventVisibleForProfile(eventProfileId = "p1", selectedProfileId = null))
        assertTrue(eventVisibleForProfile(eventProfileId = null, selectedProfileId = null))
    }

    @Test fun `specific selection shows only matching or unassigned events`() {
        assertTrue(eventVisibleForProfile(eventProfileId = "p1", selectedProfileId = "p1"))
        assertFalse(eventVisibleForProfile(eventProfileId = "p2", selectedProfileId = "p1"))
        // Legacy/unassigned events (null) belong to the owner view; show when a profile is selected.
        assertTrue(eventVisibleForProfile(eventProfileId = null, selectedProfileId = "p1"))
    }
}
