package nl.openschoolcloud.calendar.data.remote.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OidcTokenStoreTest {
    private lateinit var store: OidcTokenStore

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        store = OidcTokenStore(ctx)
        store.clear()
    }

    @Test fun startsLoggedOut() {
        assertFalse(store.isLoggedIn)
        assertNull(store.authStateJson)
    }

    @Test fun persistsAndClears() {
        store.authStateJson = """{"state":"x"}"""
        assertTrue(store.isLoggedIn)
        assertEquals("""{"state":"x"}""", store.authStateJson)
        store.clear()
        assertFalse(store.isLoggedIn)
    }
}
