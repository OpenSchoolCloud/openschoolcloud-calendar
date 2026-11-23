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
 * Secure credential storage using Android's EncryptedSharedPreferences.
 *
 * Stores passwords encrypted with AES-256 GCM, key wrapped by the Android
 * Keystore. If EncryptedSharedPreferences cannot be initialised (rare:
 * corrupt keystore, some custom ROMs), we fall back to an **in-memory only**
 * store rather than persisting passwords in cleartext to disk. The user will
 * be asked to re-enter their credentials on next launch, but the app will
 * never silently downgrade to plaintext storage.
 */
@Singleton
class CredentialStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Tracks whether the active backing store is hardware-backed encryption
     * or the in-memory fallback. Surfaces via [isEncryptedBacking].
     */
    @Volatile
    private var encryptedBackingActive: Boolean = true

    /**
     * In-memory fallback used only when EncryptedSharedPreferences fails.
     * Cleared on process death — credentials must be re-entered.
     */
    private val memoryFallback: MutableMap<String, String> = mutableMapOf()

    private val prefs: SharedPreferences? by lazy {
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
            // Do NOT fall back to plaintext SharedPreferences — privacy-first.
            encryptedBackingActive = false
            Log.e(
                "CredentialStorage",
                "EncryptedSharedPreferences unavailable — using in-memory fallback only",
                e
            )
            null
        }
    }

    /**
     * Whether credentials are persisted with hardware-backed encryption.
     * If false, credentials live only in process memory and will be lost
     * on app restart — the UI can warn the user.
     */
    fun isEncryptedBacking(): Boolean {
        // Force lazy init so encryptedBackingActive reflects reality.
        prefs
        return encryptedBackingActive
    }

    /**
     * Save password for an account.
     */
    fun saveCredentials(accountId: String, password: String) {
        val store = prefs
        if (store != null) {
            store.edit().putString(keyForAccount(accountId), password).apply()
        } else {
            synchronized(memoryFallback) {
                memoryFallback[keyForAccount(accountId)] = password
            }
        }
    }

    /**
     * Get password for an account, or null if not stored.
     */
    fun getPassword(accountId: String): String? {
        return try {
            val store = prefs
            if (store != null) {
                store.getString(keyForAccount(accountId), null)
            } else {
                synchronized(memoryFallback) { memoryFallback[keyForAccount(accountId)] }
            }
        } catch (e: Exception) {
            Log.e("CredentialStorage", "Error reading password", e)
            null
        }
    }

    /**
     * Delete credentials for an account.
     */
    fun deleteCredentials(accountId: String) {
        val store = prefs
        if (store != null) {
            store.edit().remove(keyForAccount(accountId)).apply()
        }
        synchronized(memoryFallback) {
            memoryFallback.remove(keyForAccount(accountId))
        }
    }

    /**
     * True if at least one account has stored credentials.
     */
    fun hasAnyAccount(): Boolean {
        val store = prefs
        val persistedHasAny = store?.all?.keys?.any { it.startsWith(PASSWORD_PREFIX) } == true
        val memoryHasAny = synchronized(memoryFallback) {
            memoryFallback.keys.any { it.startsWith(PASSWORD_PREFIX) }
        }
        return persistedHasAny || memoryHasAny
    }

    /**
     * All account IDs that have stored credentials.
     */
    fun getAllAccountIds(): List<String> {
        return try {
            val store = prefs
            val persisted = store?.all?.keys
                ?.filter { it.startsWith(PASSWORD_PREFIX) }
                ?.map { it.removePrefix(PASSWORD_PREFIX) }
                .orEmpty()
            val inMemory = synchronized(memoryFallback) {
                memoryFallback.keys
                    .filter { it.startsWith(PASSWORD_PREFIX) }
                    .map { it.removePrefix(PASSWORD_PREFIX) }
            }
            (persisted + inMemory).distinct()
        } catch (e: Exception) {
            Log.e("CredentialStorage", "Error reading account IDs", e)
            emptyList()
        }
    }

    private fun keyForAccount(accountId: String): String = "$PASSWORD_PREFIX$accountId"

    companion object {
        private const val PASSWORD_PREFIX = "pwd_"
    }
}
