package nl.openschoolcloud.calendar.presentation.settings

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.openschoolcloud.calendar.data.remote.auth.AuthManager
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import nl.openschoolcloud.calendar.presentation.screens.settings.SettingsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `refresh picks up login that happened while settings was on the back stack`() = runTest {
        val auth = mockk<AuthManager>(relaxed = true)
        every { auth.isLoggedIn } returns false
        val vm = SettingsViewModel(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), auth)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isOscLoggedIn)

        every { auth.isLoggedIn } returns true
        vm.refreshOscLoginState()

        assertTrue(vm.uiState.value.isOscLoggedIn)
    }

    @Test fun `delete osc account logs out and returns delete url`() = runTest {
        val auth = mockk<AuthManager>(relaxed = true)
        every { auth.isLoggedIn } returns true
        val accounts = mockk<AccountRepository>(relaxed = true)
        val vm = SettingsViewModel(mockk(relaxed = true), accounts, mockk(relaxed = true), mockk(relaxed = true), auth)
        advanceUntilIdle()

        every { auth.isLoggedIn } returns false
        val url = vm.deleteOscAccount()

        assertEquals("https://openschoolcloud.nl/account-verwijderen", url)
        verify { auth.logout() }
        coVerify(exactly = 0) { accounts.removeAccount(any()) }
        assertFalse(vm.uiState.value.isOscLoggedIn)
    }

    @Test fun `not logged in hides deletion`() = runTest {
        val auth = mockk<AuthManager>(relaxed = true)
        every { auth.isLoggedIn } returns false
        val vm = SettingsViewModel(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), auth)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isOscLoggedIn)
    }
}
