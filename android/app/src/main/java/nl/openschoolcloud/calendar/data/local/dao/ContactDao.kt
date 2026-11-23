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
package nl.openschoolcloud.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import nl.openschoolcloud.calendar.data.local.entity.ContactEntity

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    fun getAll(): Flow<List<ContactEntity>>

    @Query(
        """SELECT * FROM contacts
        WHERE displayName LIKE '%' || :query || '%'
           OR email LIKE '%' || :query || '%'
           OR organization LIKE '%' || :query || '%'
        ORDER BY displayName ASC
        LIMIT 20"""
    )
    fun search(query: String): Flow<List<ContactEntity>>

    @Query(
        """SELECT * FROM contacts
        WHERE displayName LIKE '%' || :query || '%'
           OR email LIKE '%' || :query || '%'
           OR organization LIKE '%' || :query || '%'
        ORDER BY displayName ASC
        LIMIT 20"""
    )
    suspend fun searchSync(query: String): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: String)

    @Query("SELECT * FROM contacts WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): ContactEntity?

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getCount(): Int
}
