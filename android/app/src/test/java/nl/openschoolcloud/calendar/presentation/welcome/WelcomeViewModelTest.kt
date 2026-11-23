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
package nl.openschoolcloud.calendar.presentation.welcome

import android.content.Intent
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.openschoolcloud.calendar.data.remote.auth.AuthManager
import nl.openschoolcloud.calendar.data.remote.auth.OidcIdentity
import nl.openschoolcloud.calendar.presentation.screens.welcome.WelcomeViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = mockk<AuthManager>(relaxed = true)

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `successful auth sets authenticated state`() = runTest {
        val intent = mockk<Intent>()
        coEvery { auth.handleAuthResponse(intent) } returns Result.success(OidcIdentity("sub123", "a@b.nl"))
        val vm = WelcomeViewModel(auth, mockk(relaxed = true), mockk(relaxed = true))

        vm.onAuthResult(intent)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.authenticated)
    }

    @Test fun `failed auth sets error state`() = runTest {
        val intent = mockk<Intent>()
        coEvery { auth.handleAuthResponse(intent) } returns Result.failure(RuntimeException("no"))
        val vm = WelcomeViewModel(auth, mockk(relaxed = true), mockk(relaxed = true))

        vm.onAuthResult(intent)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error)
        assertTrue(!vm.uiState.value.authenticated)
    }
}
