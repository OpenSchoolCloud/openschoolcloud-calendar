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

import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.sync.SyncProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProviderTest {
    @Test fun `local provider is always available and local scope`() {
        val p: SyncProvider = LocalSyncProvider()
        assertEquals(SyncScope.LOCAL, p.scope)
        assertTrue(p.isAvailable())
    }

    @Test fun `nextcloud provider reports own-nextcloud scope`() {
        assertEquals(SyncScope.OWN_NEXTCLOUD, NextcloudCalDavProvider().scope)
    }

    @Test fun `osc cloud provider availability follows the feature flag`() {
        // OscCloudProvider takes availability explicitly so it is testable
        // without depending on BuildConfig in a unit test.
        assertEquals(SyncScope.OSC_CLOUD, OscCloudProvider(enabled = false).scope)
        assertTrue(!OscCloudProvider(enabled = false).isAvailable())
        assertTrue(OscCloudProvider(enabled = true).isAvailable())
    }
}
