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
