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
