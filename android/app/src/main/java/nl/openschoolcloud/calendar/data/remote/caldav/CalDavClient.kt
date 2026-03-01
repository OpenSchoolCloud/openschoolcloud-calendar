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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.StringReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CalDAV client for communicating with Nextcloud
 * 
 * Implements:
 * - RFC 4791 (CalDAV)
 * - RFC 5545 (iCalendar)
 * - RFC 6638 (CalDAV Scheduling)
 */
@Singleton
class CalDavClient @Inject constructor(
    private val httpClient: OkHttpClient
) {
    companion object {
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val ICAL_MEDIA_TYPE = "text/calendar; charset=utf-8".toMediaType()
        
        // Well-known CalDAV endpoint for Nextcloud
        const val WELL_KNOWN_CALDAV = "/.well-known/caldav"
        const val NEXTCLOUD_DAV_PATH = "/remote.php/dav"
    }
    
    /**
     * Discover the CalDAV principal URL
     *
     * Tries multiple discovery paths in order:
     * 1. /.well-known/caldav (RFC 6764)
     * 2. /remote.php/dav (Nextcloud)
     * 3. /dav.php (generic)
     * 4. /caldav.php (generic)
     * 5. /calendar/dav (some implementations)
     *
     * @param serverUrl Base server URL (e.g., https://cloud.school.nl)
     * @param username Username
     * @param password App password
     * @return Principal URL or error
     */
    suspend fun discoverPrincipal(
        serverUrl: String,
        username: String,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val discoveryPaths = listOf(
            WELL_KNOWN_CALDAV,
            NEXTCLOUD_DAV_PATH,
            "/dav.php",
            "/caldav.php",
            "/calendar/dav"
        )

        var lastError: Exception? = null

        for (path in discoveryPaths) {
            try {
                val result = tryDiscoverPrincipal("$serverUrl$path", username, password)
                if (result.isSuccess) {
                    val principalUrl = result.getOrThrow()
                    return@withContext Result.success(resolveUrl(serverUrl, principalUrl))
                }
            } catch (e: Exception) {
                lastError = e
                // Continue to next path
            }
        }

        Result.failure(lastError ?: CalDavException("Could not discover CalDAV endpoint"))
    }

    /**
     * Try to discover principal at a specific URL
     */
    private suspend fun tryDiscoverPrincipal(
        url: String,
        username: String,
        password: String
    ): Result<String> {
        val result = propfind(
            url = url,
            username = username,
            password = password,
            body = CURRENT_USER_PRINCIPAL_REQUEST,
            depth = 0
        )

        return result.mapCatching { response ->
            parseCurrentUserPrincipal(response)
                ?: throw CalDavException("No principal URL found at $url")
        }
    }
    
    /**
     * Discover the calendar home set URL
     */
    suspend fun discoverCalendarHome(
        principalUrl: String,
        username: String,
        password: String
    ): Result<String> {
        return propfind(
            url = principalUrl,
            username = username,
            password = password,
            body = CALENDAR_HOME_SET_REQUEST,
            depth = 0
        ).mapCatching { response ->
            val href = parseCalendarHomeSet(response)
                ?: throw CalDavException("Could not find calendar home set")
            resolveUrl(principalUrl, href)
        }
    }
    
    /**
     * List all calendars in a calendar home
     */
    suspend fun listCalendars(
        calendarHomeUrl: String,
        username: String,
        password: String
    ): Result<List<CalendarInfo>> {
        return propfind(
            url = calendarHomeUrl,
            username = username,
            password = password,
            body = LIST_CALENDARS_REQUEST,
            depth = 1
        ).mapCatching { response ->
            parseCalendars(response, calendarHomeUrl)
        }
    }
    
    /**
     * Get calendar CTag (for change detection)
     */
    suspend fun getCtag(
        calendarUrl: String,
        username: String,
        password: String
    ): Result<String> {
        return propfind(
            url = calendarUrl,
            username = username,
            password = password,
            body = CTAG_REQUEST,
            depth = 0
        ).mapCatching { response ->
            parseCtag(response) ?: throw CalDavException("Could not get ctag")
        }
    }

    /**
     * Get calendar CTag and SyncToken together (for change detection + incremental sync)
     */
    suspend fun getCtagInfo(
        calendarUrl: String,
        username: String,
        password: String
    ): Result<CtagInfo> {
        return propfind(
            url = calendarUrl,
            username = username,
            password = password,
            body = CTAG_REQUEST,
            depth = 0
        ).mapCatching { response ->
            parseCtagInfo(response)
        }
    }
    
    /**
     * Sync collection (get changed events since last sync)
     */
    suspend fun syncCollection(
        calendarUrl: String,
        username: String,
        password: String,
        syncToken: String?
    ): Result<SyncCollectionResponse> {
        val body = buildSyncCollectionRequest(syncToken)
        
        return report(
            url = calendarUrl,
            username = username,
            password = password,
            body = body
        ).mapCatching { response ->
            parseSyncCollectionResponse(response)
        }
    }
    
    /**
     * Get a single event
     */
    suspend fun getEvent(
        eventUrl: String,
        username: String,
        password: String
    ): Result<String> {
        val request = Request.Builder()
            .url(eventUrl)
            .header("Authorization", Credentials.basic(username, password))
            .get()
            .build()
        
        return executeRequest(request)
    }
    
    /**
     * Create a new event
     */
    suspend fun createEvent(
        calendarUrl: String,
        eventUid: String,
        icalData: String,
        username: String,
        password: String
    ): Result<String> {
        val eventUrl = "$calendarUrl$eventUid.ics"
        
        val request = Request.Builder()
            .url(eventUrl)
            .header("Authorization", Credentials.basic(username, password))
            .header("If-None-Match", "*") // Fail if exists
            .put(icalData.toRequestBody(ICAL_MEDIA_TYPE))
            .build()
        
        return executeRequest(request).map { eventUrl }
    }
    
    /**
     * Update an existing event
     */
    suspend fun updateEvent(
        eventUrl: String,
        icalData: String,
        etag: String?,
        username: String,
        password: String
    ): Result<String> {
        val requestBuilder = Request.Builder()
            .url(eventUrl)
            .header("Authorization", Credentials.basic(username, password))
            .put(icalData.toRequestBody(ICAL_MEDIA_TYPE))
        
        // Use ETag for optimistic locking
        etag?.let {
            requestBuilder.header("If-Match", it)
        }
        
        return executeRequest(requestBuilder.build()).mapCatching { response ->
            // Extract new ETag from response
            response // TODO: parse ETag
        }
    }
    
    /**
     * Delete an event
     */
    suspend fun deleteEvent(
        eventUrl: String,
        etag: String?,
        username: String,
        password: String
    ): Result<Unit> {
        val requestBuilder = Request.Builder()
            .url(eventUrl)
            .header("Authorization", Credentials.basic(username, password))
            .delete()

        etag?.let {
            requestBuilder.header("If-Match", it)
        }

        return executeRequest(requestBuilder.build()).map { }
    }

    /**
     * Fetch all events from a calendar via REPORT request
     * Uses calendar-query for initial sync or fetching events in a date range
     *
     * @param calendarUrl The calendar URL
     * @param username Username
     * @param password Password
     * @param startDate Optional start date for filtering
     * @param endDate Optional end date for filtering
     * @return List of EventData containing href, etag, and raw iCal data
     */
    suspend fun fetchEvents(
        calendarUrl: String,
        username: String,
        password: String,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null
    ): Result<List<EventData>> = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildCalendarQueryRequest(startDate, endDate)

            val request = Request.Builder()
                .url(calendarUrl)
                .method("REPORT", requestBody.toRequestBody(XML_MEDIA_TYPE))
                .header("Authorization", Credentials.basic(username, password))
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful && response.code != 207) {
                return@withContext Result.failure(
                    CalDavException("Failed to fetch events: ${response.code}", response.code)
                )
            }

            val body = response.body?.string() ?: ""
            val events = parseCalendarQueryResponse(body, calendarUrl)

            Result.success(events)
        } catch (e: Exception) {
            Result.failure(CalDavException("Failed to fetch events: ${e.message}", cause = e))
        }
    }

    /**
     * Build calendar-query REPORT request body
     */
    private fun buildCalendarQueryRequest(
        startDate: LocalDate?,
        endDate: LocalDate?
    ): String {
        val timeRange = if (startDate != null && endDate != null) {
            """
                        <c:time-range start="${formatICalDate(startDate)}"
                                      end="${formatICalDate(endDate)}"/>
            """.trimIndent()
        } else {
            ""
        }

        return """
            <?xml version="1.0" encoding="utf-8"?>
            <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                <d:prop>
                    <d:getetag/>
                    <c:calendar-data/>
                </d:prop>
                <c:filter>
                    <c:comp-filter name="VCALENDAR">
                        <c:comp-filter name="VEVENT">
                            $timeRange
                        </c:comp-filter>
                    </c:comp-filter>
                </c:filter>
            </c:calendar-query>
        """.trimIndent()
    }

    /**
     * Format LocalDate to iCal date format (YYYYMMDDTHHMMSSZ)
     */
    private fun formatICalDate(date: LocalDate): String {
        return date.format(DateTimeFormatter.BASIC_ISO_DATE) + "T000000Z"
    }

    /**
     * Parse calendar-query REPORT response
     * Extracts href, etag, and raw iCal data for each event
     */
    private fun parseCalendarQueryResponse(response: String, baseUrl: String): List<EventData> {
        val events = mutableListOf<EventData>()

        try {
            val parser = createParser(response)
            var eventType = parser.eventType

            var inResponse = false
            var inPropstat = false
            var currentHref: String? = null
            var currentEtag: String? = null
            var currentIcalData: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val localName = parser.name.substringAfter(":")

                        when (localName) {
                            "response" -> {
                                inResponse = true
                                currentHref = null
                                currentEtag = null
                                currentIcalData = null
                            }
                            "propstat" -> inPropstat = true
                            "href" -> {
                                if (inResponse && !inPropstat) {
                                    currentHref = parser.nextText()
                                }
                            }
                            "getetag" -> {
                                if (inPropstat) {
                                    currentEtag = parser.nextText()?.trim('"')
                                }
                            }
                            "calendar-data" -> {
                                if (inPropstat) {
                                    currentIcalData = parser.nextText()
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val localName = parser.name.substringAfter(":")

                        when (localName) {
                            "response" -> {
                                if (currentHref != null && currentIcalData != null) {
                                    events.add(
                                        EventData(
                                            href = resolveUrl(baseUrl, currentHref),
                                            etag = currentEtag ?: "",
                                            icalData = currentIcalData
                                        )
                                    )
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
            // Return what we've parsed so far
        }

        return events
    }

    // ==================== Private helpers ====================
    
    private suspend fun propfind(
        url: String,
        username: String,
        password: String,
        body: String,
        depth: Int
    ): Result<String> {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .header("Depth", depth.toString())
            .method("PROPFIND", body.toRequestBody(XML_MEDIA_TYPE))
            .build()
        
        return executeRequest(request)
    }
    
    private suspend fun report(
        url: String,
        username: String,
        password: String,
        body: String
    ): Result<String> {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .method("REPORT", body.toRequestBody(XML_MEDIA_TYPE))
            .build()
        
        return executeRequest(request)
    }
    
    private fun executeRequest(request: Request): Result<String> {
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 207) { // 207 = Multi-Status
                    Result.success(response.body?.string() ?: "")
                } else {
                    Result.failure(
                        CalDavException(
                            "Request failed: ${response.code} ${response.message}",
                            response.code
                        )
                    )
                }
            }
        } catch (e: IOException) {
            Result.failure(CalDavException("Network error: ${e.message}", cause = e))
        }
    }
    
    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http")) {
            return relativeUrl
        }
        // Root-relative path (e.g., /remote.php/dav/...) → use origin only
        if (relativeUrl.startsWith("/")) {
            val origin = extractOrigin(baseUrl)
            return origin + relativeUrl
        }
        // Relative path → append to base
        return baseUrl.trimEnd('/') + "/" + relativeUrl.trimStart('/')
    }

    private fun extractOrigin(url: String): String {
        // Extract scheme + host + port from a full URL
        val schemeEnd = url.indexOf("://")
        if (schemeEnd == -1) return url
        val pathStart = url.indexOf('/', schemeEnd + 3)
        return if (pathStart == -1) url else url.substring(0, pathStart)
    }
    
    // ==================== XML Parsing ====================

    /**
     * Parse current-user-principal from PROPFIND response
     * Extracts href from <d:current-user-principal><d:href>...</d:href></d:current-user-principal>
     */
    private fun parseCurrentUserPrincipal(response: String): String? {
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
    private fun parseCalendarHomeSet(response: String): String? {
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
    private fun parseCalendars(response: String, baseUrl: String): List<CalendarInfo> {
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
                                    val resolvedUrl = resolveUrl(baseUrl, currentHref)
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
    private fun parseCtag(response: String): String? {
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
     * Parse both CTag and SyncToken from PROPFIND response
     */
    private fun parseCtagInfo(response: String): CtagInfo {
        var ctag: String? = null
        var syncToken: String? = null

        try {
            val parser = createParser(response)
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val localName = parser.name.substringAfter(":")
                    when (localName) {
                        "getctag" -> ctag = parser.nextText()
                        "sync-token" -> syncToken = parser.nextText()
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}

        return CtagInfo(ctag = ctag, syncToken = syncToken)
    }

    /**
     * Parse sync-collection response
     * Extracts new sync token, changed events with iCal data, and deleted event hrefs
     *
     * Changed events have propstat with 200 status containing etag + calendar-data.
     * Deleted events have a response-level 404 status (no propstat).
     */
    private fun parseSyncCollectionResponse(response: String): SyncCollectionResponse {
        val changed = mutableListOf<EventData>()
        val deleted = mutableListOf<String>()
        var syncToken = ""

        try {
            val parser = createParser(response)
            var eventType = parser.eventType

            var inResponse = false
            var inPropstat = false
            var currentHref: String? = null
            var currentEtag: String? = null
            var currentIcalData: String? = null
            var propstatStatusCode: Int? = null
            var responseStatusCode: Int? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val localName = parser.name.substringAfter(":")

                        when (localName) {
                            "response" -> {
                                inResponse = true
                                currentHref = null
                                currentEtag = null
                                currentIcalData = null
                                propstatStatusCode = null
                                responseStatusCode = null
                            }
                            "propstat" -> inPropstat = true
                            "href" -> {
                                if (inResponse && !inPropstat) {
                                    currentHref = parser.nextText()
                                }
                            }
                            "status" -> {
                                val statusText = parser.nextText()
                                val code = statusText.split(" ").getOrNull(1)?.toIntOrNull()
                                if (inPropstat) {
                                    propstatStatusCode = code
                                } else if (inResponse) {
                                    responseStatusCode = code
                                }
                            }
                            "getetag" -> {
                                if (inPropstat) {
                                    currentEtag = parser.nextText()?.trim('"')
                                }
                            }
                            "calendar-data" -> {
                                if (inPropstat) {
                                    currentIcalData = parser.nextText()
                                }
                            }
                            "sync-token" -> {
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
                                currentHref?.let { href ->
                                    when {
                                        // Deleted: response-level 404 status
                                        responseStatusCode == 404 -> deleted.add(href)
                                        // Changed: has iCal data from propstat
                                        currentIcalData != null -> {
                                            changed.add(
                                                EventData(
                                                    href = href,
                                                    etag = currentEtag ?: "",
                                                    icalData = currentIcalData
                                                )
                                            )
                                        }
                                        // Propstat 404 also means deleted
                                        propstatStatusCode == 404 -> deleted.add(href)
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
        } catch (_: Exception) {
            // Return whatever we parsed
        }

        return SyncCollectionResponse(
            syncToken = syncToken,
            changed = changed,
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
     * Normalize color string to proper hex format
     */
    private fun normalizeColor(color: String?): String? {
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
    
    private fun buildSyncCollectionRequest(syncToken: String?): String {
        return if (syncToken != null) {
            SYNC_COLLECTION_REQUEST.replace("{{SYNC_TOKEN}}", syncToken)
        } else {
            SYNC_COLLECTION_INITIAL_REQUEST
        }
    }
    
    // ==================== XML Request Templates ====================
    
    private val CURRENT_USER_PRINCIPAL_REQUEST = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:propfind xmlns:d="DAV:">
            <d:prop>
                <d:current-user-principal/>
            </d:prop>
        </d:propfind>
    """.trimIndent()
    
    private val CALENDAR_HOME_SET_REQUEST = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:propfind xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
            <d:prop>
                <cal:calendar-home-set/>
            </d:prop>
        </d:propfind>
    """.trimIndent()
    
    private val LIST_CALENDARS_REQUEST = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:propfind xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/" xmlns:oc="http://owncloud.org/ns">
            <d:prop>
                <d:resourcetype/>
                <d:displayname/>
                <cal:calendar-color/>
                <cs:getctag/>
                <d:sync-token/>
                <cal:supported-calendar-component-set/>
                <d:current-user-privilege-set/>
            </d:prop>
        </d:propfind>
    """.trimIndent()
    
    private val CTAG_REQUEST = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:propfind xmlns:d="DAV:" xmlns:cs="http://calendarserver.org/ns/">
            <d:prop>
                <cs:getctag/>
                <d:sync-token/>
            </d:prop>
        </d:propfind>
    """.trimIndent()
    
    private val SYNC_COLLECTION_REQUEST = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:sync-collection xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
            <d:sync-token>{{SYNC_TOKEN}}</d:sync-token>
            <d:sync-level>1</d:sync-level>
            <d:prop>
                <d:getetag/>
                <c:calendar-data/>
            </d:prop>
        </d:sync-collection>
    """.trimIndent()

    private val SYNC_COLLECTION_INITIAL_REQUEST = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:sync-collection xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
            <d:sync-token/>
            <d:sync-level>1</d:sync-level>
            <d:prop>
                <d:getetag/>
                <c:calendar-data/>
            </d:prop>
        </d:sync-collection>
    """.trimIndent()
}

/**
 * Information about a discovered calendar
 */
data class CalendarInfo(
    val url: String,
    val displayName: String,
    val color: String?,
    val ctag: String?,
    val syncToken: String?,
    val readOnly: Boolean,
    val supportsEvents: Boolean
)

/**
 * Response from sync-collection report
 */
data class SyncCollectionResponse(
    val syncToken: String,
    val changed: List<EventData>,  // Added or modified events with iCal data
    val deleted: List<String>      // Deleted event hrefs
)

/**
 * CTag and SyncToken info from a calendar collection
 */
data class CtagInfo(
    val ctag: String?,
    val syncToken: String?
)

/**
 * Event data returned from calendar-query REPORT
 */
data class EventData(
    val href: String,
    val etag: String,
    val icalData: String
)

/**
 * CalDAV-specific exception
 */
class CalDavException(
    message: String,
    val httpCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)
