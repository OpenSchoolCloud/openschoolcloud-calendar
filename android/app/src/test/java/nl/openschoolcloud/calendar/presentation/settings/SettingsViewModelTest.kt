package nl.openschoolcloud.calendar.presentation.settings

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.openschoolcloud.calendar.data.remote.auth.AuthManager
import nl.openschoolcloud.calendar.presentation.screens.settings.SettingsViewModel
import org.junit.After
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
}
