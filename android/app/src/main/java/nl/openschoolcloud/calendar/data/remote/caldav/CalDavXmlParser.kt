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
package nl.openschoolcloud.calendar.data.remote.caldav

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * XML parser for CalDAV responses
 *
 * Separated from CalDavClient for testability
 */
@Singleton
class CalDavXmlParser @Inject constructor() {

    /**
     * Parse current-user-principal from PROPFIND response
     * Extracts href from <d:current-user-principal><d:href>...</d:href></d:current-user-principal>
     */
    fun parseCurrentUserPrincipal(response: String): String? {
        return try {
            val parser = createParser(response)
            var inCurrentUserPrincipal = false
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val localName = parser.name.substringAfter(":")
                        if (localName == "current-user-principal") {
                            inCurrentUserPrincipal = true
                        } else if (localName == "href" && inCurrentUserPrincipal) {
                            return parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val localName = parser.name.substringAfter(":")
                        if (localName == "current-user-principal") {
                            inCurrentUserPrincipal = false
                        }
                    }
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse calendar-home-set from PROPFIND response
     * Extracts href from <cal:calendar-home-set><d:href>...</d:href></cal:calendar-home-set>
     */
    fun parseCalendarHomeSet(response: String): String? {
        return try {
            val parser = createParser(response)
            var inCalendarHomeSet = false
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val localName = parser.name.substringAfter(":")
                        if (localName == "calendar-home-set") {
                            inCalendarHomeSet = true
                        } else if (localName == "href" && inCalendarHomeSet) {
                            return parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val localName = parser.name.substringAfter(":")
                        if (localName == "calendar-home-set") {
                            inCalendarHomeSet = false
                        }
                    }
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse calendars from PROPFIND multistatus response
     * Filters only responses containing <cal:calendar/> resourcetype
     */
    fun parseCalendars(response: String, baseUrl: String): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()

        try {
            val parser = createParser(response)
            var eventType = parser.eventType

            // State for current response being parsed
            var currentHref: String? = null
            var currentDisplayName: String? = null
            var currentColor: String? = null
            var currentCtag: String? = null
            var currentSyncToken: String? = null
            var isCalendar = false
            var hasWritePrivilege = false
            var supportsEvents = false

            // Track nesting
            var inResponse = false
            var inPropstat = false
            var inProp = false
            var inResourceType = false
            var inPrivilegeSet = false
            var inPrivilege = false
            var inSupportedComponents = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val localName = parser.name.substringAfter(":")

                        when (localName) {
                            "response" -> {
                                inResponse = true
                                // Reset state
                                currentHref = null
                                currentDisplayName = null
                                currentColor = null
                                currentCtag = null
                                currentSyncToken = null
                                isCalendar = false
                                hasWritePrivilege = false
                                supportsEvents = false
                            }
                            "propstat" -> inPropstat = true
                            "prop" -> inProp = true
                            "resourcetype" -> inResourceType = true
                            "current-user-privilege-set" -> inPrivilegeSet = true
                            "privilege" -> inPrivilege = true
                            "supported-calendar-component-set" -> inSupportedComponents = true
                            "href" -> {
                                if (inResponse && !inPropstat) {
                                    currentHref = parser.nextText()
                                }
                            }
                            "displayname" -> {
                                if (inProp) {
                                    currentDisplayName = parser.nextText()
                                }
                            }
                            "calendar-color" -> {
                                if (inProp) {
                                    currentColor = parser.nextText()
                                }
                            }
                            "getctag" -> {
                                if (inProp) {
                                    currentCtag = parser.nextText()
                                }
                            }
                            "sync-token" -> {
                                if (inProp) {
                                    currentSyncToken = parser.nextText()
                                }
                            }
                            "calendar" -> {
                                if (inResourceType) {
                                    isCalendar = true
                                }
                            }
                            "write" -> {
                                if (inPrivilege) {
                                    hasWritePrivilege = true
                                }
                            }
                            "comp" -> {
                                if (inSupportedComponents) {
                                    val name = parser.getAttributeValue(null, "name")
                                    if (name == "VEVENT") {
                                        supportsEvents = true
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val localName = parser.name.substringAfter(":")

                        when (localName) {
                            "response" -> {
                                // End of response - add calendar if it's valid
                                if (isCalendar && currentHref != null) {
                                    val resolvedUrl = resolveUrl(baseUrl.substringBefore("/remote.php"), currentHref)
                                    calendars.add(
                                        CalendarInfo(
                                            url = resolvedUrl,
                                            displayName = currentDisplayName ?: "Calendar",
                                            color = normalizeColor(currentColor),
                                            ctag = currentCtag,
                                            syncToken = currentSyncToken,
                                            readOnly = !hasWritePrivilege,
                                            supportsEvents = supportsEvents
                                        )
                                    )
                                }
                                inResponse = false
                            }
                            "propstat" -> inPropstat = false
                            "prop" -> inProp = false
                            "resourcetype" -> inResourceType = false
                            "current-user-privilege-set" -> inPrivilegeSet = false
                            "privilege" -> inPrivilege = false
                            "supported-calendar-component-set" -> inSupportedComponents = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Return whatever we've parsed so far
        }

        return calendars
    }

    /**
     * Parse CTag from PROPFIND response
     */
    fun parseCtag(response: String): String? {
        return try {
            val parser = createParser(response)
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val localName = parser.name.substringAfter(":")
                    if (localName == "getctag") {
                        return parser.nextText()
                    }
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse sync-collection response
     * Extracts new sync token and list of changed/deleted events
     */
    fun parseSyncCollectionResponse(response: String): SyncCollectionResponse {
        val added = mutableListOf<String>()
        val modified = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var syncToken = ""

        try {
            val parser = createParser(response)
            var eventType = parser.eventType

            var inResponse = false
            var inPropstat = false
            var currentHref: String? = null
            var currentStatus: String? = null
            var currentEtag: String? = null
            var statusCode: Int? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val localName = parser.name.substringAfter(":")

                        when (localName) {
                            "response" -> {
                                inResponse = true
                                currentHref = null
                                currentStatus = null
                                currentEtag = null
                                statusCode = null
                            }
                            "propstat" -> inPropstat = true
                            "href" -> {
                                if (inResponse && !inPropstat) {
                                    currentHref = parser.nextText()
                                }
                            }
                            "status" -> {
                                if (inPropstat) {
                                    currentStatus = parser.nextText()
                                    // Parse status code from "HTTP/1.1 200 OK" or "HTTP/1.1 404 Not Found"
                                    statusCode = currentStatus.split(" ").getOrNull(1)?.toIntOrNull()
                                }
                            }
                            "getetag" -> {
                                if (inPropstat) {
                                    currentEtag = parser.nextText()
                                }
                            }
                            "sync-token" -> {
                                // Top-level sync-token (new sync token)
                                if (!inResponse) {
                                    syncToken = parser.nextText()
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val localName = parser.name.substringAfter(":")

                        when (localName) {
                            "response" -> {
                                // Determine if added, modified, or deleted
                                currentHref?.let { href ->
                                    when {
                                        statusCode == 404 -> deleted.add(href)
                                        currentEtag != null -> {
                                            // Has etag = exists, could be new or modified
                                            // For simplicity, treat all as modified (caller can check existence)
                                            modified.add(href)
                                        }
                                        else -> { /* Unknown status, skip */ }
                                    }
                                }
                                inResponse = false
                            }
                            "propstat" -> inPropstat = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Return whatever we parsed
        }

        return SyncCollectionResponse(
            syncToken = syncToken,
            added = added,
            modified = modified,
            deleted = deleted
        )
    }

    /**
     * Create an XmlPullParser from a string
     */
    private fun createParser(xmlString: String): XmlPullParser {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xmlString))
        return parser
    }

    /**
     * Resolve a relative URL against a base URL
     */
    internal fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return when {
            relativeUrl.startsWith("http") -> relativeUrl
            relativeUrl.startsWith("/") -> {
                // Root-relative path: extract origin (scheme + host + port) from baseUrl
                val uri = java.net.URI(baseUrl)
                val port = if (uri.port != -1) ":${uri.port}" else ""
                "${uri.scheme}://${uri.host}$port${relativeUrl}"
            }
            else -> baseUrl.trimEnd('/') + "/" + relativeUrl.trimStart('/')
        }
    }

    /**
     * Normalize color string to proper hex format
     */
    internal fun normalizeColor(color: String?): String? {
        if (color == null) return null

        // Remove alpha if present (e.g., #0082c9FF -> #0082c9)
        var normalized = color.trim()
        if (normalized.startsWith("#") && normalized.length == 9) {
            normalized = "#" + normalized.substring(1, 7)
        }

        return if (normalized.startsWith("#") && normalized.length == 7) {
            normalized
        } else {
            null
        }
    }
}
