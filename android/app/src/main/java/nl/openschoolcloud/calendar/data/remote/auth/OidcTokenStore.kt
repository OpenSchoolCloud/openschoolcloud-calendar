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
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the serialized AppAuth AuthState (tokens) at rest.
 * Uses EncryptedSharedPreferences with a lazy init. If EncryptedSharedPreferences
 * cannot be initialised (rare: corrupt keystore, some custom ROMs), we fall back
 * to an **in-memory only** store rather than persisting the OIDC refresh token
 * in cleartext to disk — mirrors CredentialStorage's hardened fallback. The
 * user will need to log in again after a process death in that case, but the
 * token never touches disk unencrypted.
 * This is the OSC identity store; Nextcloud CalDAV credentials live separately
 * in CredentialStorage and must never be mixed in here.
 */
class OidcTokenStore(private val context: Context) {

    /** In-memory fallback used only when EncryptedSharedPreferences fails. */
    @Volatile
    private var memoryFallback: String? = null

    private val prefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "osc_oidc_tokens",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Do NOT fall back to plaintext SharedPreferences — privacy-first.
            Log.e(
                "OidcTokenStore",
                "EncryptedSharedPreferences unavailable — using in-memory fallback only",
                e
            )
            null
        }
    }

    var authStateJson: String?
        get() {
            val store = prefs
            return if (store != null) store.getString(KEY_AUTH_STATE, null) else memoryFallback
        }
        set(value) {
            val store = prefs
            if (store != null) {
                if (value == null) store.edit().remove(KEY_AUTH_STATE).apply()
                else store.edit().putString(KEY_AUTH_STATE, value).apply()
            } else {
                memoryFallback = value
            }
        }

    val isLoggedIn: Boolean get() = authStateJson != null

    fun clear() {
        prefs?.edit()?.remove(KEY_AUTH_STATE)?.apply()
        memoryFallback = null
    }

    private companion object { const val KEY_AUTH_STATE = "auth_state" }
}
