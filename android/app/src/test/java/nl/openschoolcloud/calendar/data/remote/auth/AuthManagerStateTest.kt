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
package nl.openschoolcloud.calendar.data.remote.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthManagerStateTest {
    private lateinit var manager: AuthManager
    private lateinit var store: OidcTokenStore

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        store = OidcTokenStore(ctx).also { it.clear() }
        manager = AuthManager(ctx, store)
    }

    @Test fun `reports logged out when store empty`() {
        assertFalse(manager.isLoggedIn)
    }

    @Test fun `reflects logged in when store has state and clears on logout`() {
        store.authStateJson = """{"mAuthorizationServiceConfiguration":null}"""
        assertTrue(manager.isLoggedIn)
        manager.logout()
        assertFalse(manager.isLoggedIn)
    }
}
