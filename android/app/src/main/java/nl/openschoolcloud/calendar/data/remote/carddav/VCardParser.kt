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
package nl.openschoolcloud.calendar.data.remote.carddav

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parsed contact data from a vCard
 */
data class ParsedContact(
    val uid: String?,
    val displayName: String,
    val emails: List<String>,
    val phone: String?,
    val organization: String?,
    val photoUri: String?
)

/**
 * Manual vCard parser for RFC 6350 (vCard 4.0) and RFC 2426 (vCard 3.0)
 *
 * Parses only the fields we need: FN, EMAIL, TEL, ORG, PHOTO, UID
 * No external library to avoid META-INF conflicts.
 */
@Singleton
class VCardParser @Inject constructor() {

    fun parse(vcardData: String): ParsedContact? {
        val unfolded = unfoldLines(vcardData)
        val lines = unfolded.lines()

        var uid: String? = null
        var displayName: String? = null
        val emails = mutableListOf<String>()
        var phone: String? = null
        var organization: String? = null
        var photoUri: String? = null

        for (line in lines) {
            val upper = line.uppercase()

            when {
                upper.startsWith("UID") -> {
                    uid = extractValue(line)
                }
                upper.startsWith("FN") -> {
                    displayName = extractValue(line)
                }
                upper.startsWith("EMAIL") -> {
                    extractValue(line)?.let { email ->
                        if (email.isNotBlank()) emails.add(email.lowercase())
                    }
                }
                upper.startsWith("TEL") -> {
                    if (phone == null) {
                        phone = extractValue(line)
                    }
                }
                upper.startsWith("ORG") -> {
                    organization = extractValue(line)
                        ?.replace(";", " ")
                        ?.trim()
                }
                upper.startsWith("PHOTO") -> {
                    if (photoUri == null) {
                        val value = extractValue(line)
                        // Only store URL references, not inline base64 data
                        if (value != null && (value.startsWith("http://") || value.startsWith("https://"))) {
                            photoUri = value
                        }
                    }
                }
            }
        }

        // FN is required in vCard spec; skip entries without it
        if (displayName.isNullOrBlank()) return null

        return ParsedContact(
            uid = uid,
            displayName = displayName,
            emails = emails,
            phone = phone,
            organization = organization,
            photoUri = photoUri
        )
    }

    /**
     * RFC 6350 line unfolding: lines starting with a space or tab
     * are continuations of the previous line.
     */
    private fun unfoldLines(data: String): String {
        return data.replace("\r\n ", "")
            .replace("\r\n\t", "")
            .replace("\n ", "")
            .replace("\n\t", "")
    }

    /**
     * Extract the value part from a vCard property line.
     * Handles formats like:
     *   PROPERTY:value
     *   PROPERTY;PARAM=x:value
     *   PROPERTY;PARAM=x;PARAM2=y:value
     */
    private fun extractValue(line: String): String? {
        val colonIndex = findValueColon(line)
        if (colonIndex < 0 || colonIndex >= line.length - 1) return null
        return line.substring(colonIndex + 1).trim()
    }

    /**
     * Find the colon that separates parameters from value.
     * Must handle quoted parameter values that might contain colons.
     */
    private fun findValueColon(line: String): Int {
        var inQuotes = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ':' -> if (!inQuotes) return i
            }
        }
        return -1
    }
}
