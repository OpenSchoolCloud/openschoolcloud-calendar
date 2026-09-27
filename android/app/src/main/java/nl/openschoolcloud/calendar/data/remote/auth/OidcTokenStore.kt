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
 * Uses EncryptedSharedPreferences with a lazy init + fallback to regular
 * SharedPreferences, because MasterKey creation fails on some devices/emulators.
 * This is the OSC identity store; Nextcloud CalDAV credentials live separately
 * in CredentialStorage and must never be mixed in here.
 */
class OidcTokenStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
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
            Log.w("OidcTokenStore", "Encrypted prefs unavailable, falling back (${e.javaClass.simpleName})")
            context.getSharedPreferences("osc_oidc_tokens_plain", Context.MODE_PRIVATE)
        }
    }

    var authStateJson: String?
        get() = prefs.getString(KEY_AUTH_STATE, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_AUTH_STATE).apply()
            else prefs.edit().putString(KEY_AUTH_STATE, value).apply()
        }

    val isLoggedIn: Boolean get() = authStateJson != null

    fun clear() { prefs.edit().remove(KEY_AUTH_STATE).apply() }

    private companion object { const val KEY_AUTH_STATE = "auth_state" }
}
