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
