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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure credential storage using Android's EncryptedSharedPreferences
 *
 * Stores passwords encrypted with AES-256 GCM encryption backed by
 * Android Keystore. Falls back to regular SharedPreferences if the
 * Keystore is unavailable (e.g. some emulators or rooted devices).
 */
@Singleton
class CredentialStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "osc_credentials",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("CredentialStorage", "EncryptedSharedPreferences failed, using fallback", e)
            context.getSharedPreferences("osc_credentials_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Save password for an account
     *
     * @param accountId The account ID
     * @param password The password (or app password) to store
     */
    fun saveCredentials(accountId: String, password: String) {
        prefs.edit().putString(keyForAccount(accountId), password).apply()
    }

    /**
     * Get password for an account
     *
     * @param accountId The account ID
     * @return The stored password or null if not found
     */
    fun getPassword(accountId: String): String? {
        return try {
            prefs.getString(keyForAccount(accountId), null)
        } catch (e: Exception) {
            Log.e("CredentialStorage", "Error reading password for $accountId", e)
            null
        }
    }

    /**
     * Delete credentials for an account
     *
     * @param accountId The account ID
     */
    fun deleteCredentials(accountId: String) {
        prefs.edit().remove(keyForAccount(accountId)).apply()
    }

    /**
     * Check if any accounts have stored credentials
     *
     * @return true if at least one account has stored credentials
     */
    fun hasAnyAccount(): Boolean {
        return prefs.all.keys.any { it.startsWith(PASSWORD_PREFIX) }
    }

    /**
     * Get all account IDs that have stored credentials
     *
     * @return List of account IDs
     */
    fun getAllAccountIds(): List<String> {
        return try {
            prefs.all.keys
                .filter { it.startsWith(PASSWORD_PREFIX) }
                .map { it.removePrefix(PASSWORD_PREFIX) }
        } catch (e: Exception) {
            Log.e("CredentialStorage", "Error reading all account IDs", e)
            emptyList()
        }
    }

    private fun keyForAccount(accountId: String): String {
        return "$PASSWORD_PREFIX$accountId"
    }

    companion object {
        private const val PASSWORD_PREFIX = "pwd_"
    }
}
