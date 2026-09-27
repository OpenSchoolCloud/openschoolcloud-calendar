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
