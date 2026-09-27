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
