/*
 * OSC Calendar - Privacy-first calendar for Dutch education
 * Copyright (C) 2025 Aldewereld Consultancy (OpenSchoolCloud)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 */
package nl.openschoolcloud.calendar.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.openschoolcloud.calendar.data.local.dao.AccountDao
import nl.openschoolcloud.calendar.data.local.dao.ContactDao
import nl.openschoolcloud.calendar.data.local.entity.ContactEntity
import nl.openschoolcloud.calendar.data.remote.auth.CredentialStorage
import nl.openschoolcloud.calendar.data.remote.caldav.CalDavClient
import nl.openschoolcloud.calendar.data.remote.carddav.VCardParser
import nl.openschoolcloud.calendar.domain.model.Contact
import nl.openschoolcloud.calendar.domain.repository.ContactRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao,
    private val accountDao: AccountDao,
    private val calDavClient: CalDavClient,
    private val credentialStorage: CredentialStorage,
    private val vcardParser: VCardParser
) : ContactRepository {

    companion object {
        private const val TAG = "ContactRepository"
    }

    override fun getContacts(): Flow<List<Contact>> {
        return contactDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchContacts(query: String): Flow<List<Contact>> {
        return contactDao.search(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun searchContactsSync(query: String): List<Contact> {
        return contactDao.searchSync(query).map { it.toDomain() }
    }

    override suspend fun syncContacts(accountId: String): Result<Int> {
        return try {
            val account = accountDao.getById(accountId)
                ?: return Result.failure(IllegalArgumentException("Account not found: $accountId"))

            val password = credentialStorage.getPassword(accountId)
                ?: return Result.failure(IllegalStateException("No credentials for account: $accountId"))

            val principalUrl = account.principalUrl
                ?: return Result.failure(IllegalStateException("No principal URL for account: $accountId"))

            // Discover organizer email and store it
            discoverAndStoreOrganizerEmail(accountId, principalUrl, account.username, password)

            // Discover addressbook home
            val homeResult = calDavClient.discoverAddressbookHome(principalUrl, account.username, password)
            val homeUrl = homeResult.getOrElse { e ->
                Log.w(TAG, "No addressbook home for $accountId: ${e.message}")
                return Result.success(0)
            }

            // List addressbooks
            val addressbooksResult = calDavClient.listAddressbooks(homeUrl, account.username, password)
            val addressbooks = addressbooksResult.getOrElse { e ->
                Log.w(TAG, "Failed to list addressbooks: ${e.message}")
                return Result.success(0)
            }

            if (addressbooks.isEmpty()) {
                Log.d(TAG, "No addressbooks found for account $accountId")
                return Result.success(0)
            }

            var totalContacts = 0

            for (addressbook in addressbooks) {
                val contactsResult = calDavClient.fetchContacts(addressbook.url, account.username, password)
                val contactDataList = contactsResult.getOrNull()
                if (contactDataList == null) {
                    Log.w(TAG, "Failed to fetch contacts from ${addressbook.displayName}: ${contactsResult.exceptionOrNull()?.message}")
                    continue
                }

                val now = Instant.now().toEpochMilli()
                val entities = contactDataList.mapNotNull { contactData ->
                    val parsed = vcardParser.parse(contactData.vcardData) ?: return@mapNotNull null

                    ContactEntity(
                        id = parsed.uid ?: UUID.randomUUID().toString(),
                        accountId = accountId,
                        displayName = parsed.displayName,
                        email = parsed.emails.firstOrNull(),
                        emailsJson = if (parsed.emails.size > 1) {
                            parsed.emails.joinToString(",") { "\"$it\"" }.let { "[$it]" }
                        } else null,
                        phone = parsed.phone,
                        organization = parsed.organization,
                        photoUri = parsed.photoUri,
                        vcardEtag = contactData.etag,
                        href = contactData.href,
                        lastSynced = now
                    )
                }

                // Full replace: delete old contacts for this account, then insert new
                contactDao.deleteByAccount(accountId)
                contactDao.insertAll(entities)
                totalContacts += entities.size
            }

            Log.d(TAG, "Synced $totalContacts contacts for account $accountId")
            Result.success(totalContacts)
        } catch (e: Exception) {
            Log.e(TAG, "Contact sync failed for $accountId", e)
            Result.failure(e)
        }
    }

    override suspend fun syncAllContacts(): Result<Int> {
        val accounts = accountDao.getAllSync()
        var totalContacts = 0
        var lastError: Exception? = null

        for (account in accounts) {
            val result = syncContacts(account.id)
            result.fold(
                onSuccess = { totalContacts += it },
                onFailure = { lastError = it as? Exception }
            )
        }

        return if (totalContacts > 0 || lastError == null) {
            Result.success(totalContacts)
        } else {
            Result.failure(lastError!!)
        }
    }

    private suspend fun discoverAndStoreOrganizerEmail(
        accountId: String,
        principalUrl: String,
        username: String,
        password: String
    ) {
        try {
            val account = accountDao.getById(accountId) ?: return
            // Skip if we already have an email
            if (!account.email.isNullOrBlank()) return

            val emailResult = calDavClient.discoverCalendarUserAddress(principalUrl, username, password)
            emailResult.onSuccess { email ->
                accountDao.update(account.copy(email = email))
                Log.d(TAG, "Discovered organizer email for $accountId: $email")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to discover organizer email: ${e.message}")
        }
    }
}

private fun ContactEntity.toDomain(): Contact {
    val allEmails = mutableListOf<String>()
    email?.let { allEmails.add(it) }
    // Parse additional emails from JSON array
    emailsJson?.let { json ->
        try {
            json.removeSurrounding("[", "]")
                .split("\",\"")
                .map { it.removeSurrounding("\"") }
                .filter { it.isNotBlank() && it !in allEmails }
                .forEach { allEmails.add(it) }
        } catch (_: Exception) {}
    }

    return Contact(
        id = id,
        accountId = accountId,
        displayName = displayName,
        email = email,
        allEmails = allEmails,
        phone = phone,
        organization = organization,
        photoUri = photoUri
    )
}
