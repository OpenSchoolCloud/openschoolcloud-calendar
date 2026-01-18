package nl.openschoolcloud.calendar.data.remote.caldav

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
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
     * @param serverUrl Base server URL (e.g., https://cloud.school.nl)
     * @param username Username
     * @param password App password
     * @return Principal URL or error
     */
    suspend fun discoverPrincipal(
        serverUrl: String,
        username: String,
        password: String
    ): Result<String> {
        // Try well-known first
        val wellKnownResult = propfind(
            url = "$serverUrl$WELL_KNOWN_CALDAV",
            username = username,
            password = password,
            body = CURRENT_USER_PRINCIPAL_REQUEST,
            depth = 0
        )
        
        if (wellKnownResult.isSuccess) {
            val principalUrl = parseCurrentUserPrincipal(wellKnownResult.getOrThrow())
            if (principalUrl != null) {
                return Result.success(resolveUrl(serverUrl, principalUrl))
            }
        }
        
        // Fallback to Nextcloud path
        val nextcloudResult = propfind(
            url = "$serverUrl$NEXTCLOUD_DAV_PATH",
            username = username,
            password = password,
            body = CURRENT_USER_PRINCIPAL_REQUEST,
            depth = 0
        )
        
        return nextcloudResult.mapCatching { response ->
            parseCurrentUserPrincipal(response)
                ?.let { resolveUrl(serverUrl, it) }
                ?: throw CalDavException("Could not find principal URL")
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
            parseCalendarHomeSet(response)
                ?: throw CalDavException("Could not find calendar home set")
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
        return if (relativeUrl.startsWith("http")) {
            relativeUrl
        } else {
            baseUrl.trimEnd('/') + "/" + relativeUrl.trimStart('/')
        }
    }
    
    // ==================== XML Parsing ====================
    // TODO: Implement proper XML parsing
    
    private fun parseCurrentUserPrincipal(response: String): String? {
        // Parse <d:current-user-principal><d:href>...</d:href></d:current-user-principal>
        TODO("Implement XML parsing")
    }
    
    private fun parseCalendarHomeSet(response: String): String? {
        // Parse <cal:calendar-home-set><d:href>...</d:href></cal:calendar-home-set>
        TODO("Implement XML parsing")
    }
    
    private fun parseCalendars(response: String, baseUrl: String): List<CalendarInfo> {
        // Parse multistatus response with calendar properties
        TODO("Implement XML parsing")
    }
    
    private fun parseCtag(response: String): String? {
        // Parse <cs:getctag>...</cs:getctag>
        TODO("Implement XML parsing")
    }
    
    private fun parseSyncCollectionResponse(response: String): SyncCollectionResponse {
        TODO("Implement XML parsing")
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
        <d:sync-collection xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
            <d:sync-token>{{SYNC_TOKEN}}</d:sync-token>
            <d:sync-level>1</d:sync-level>
            <d:prop>
                <d:getetag/>
            </d:prop>
        </d:sync-collection>
    """.trimIndent()
    
    private val SYNC_COLLECTION_INITIAL_REQUEST = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:sync-collection xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
            <d:sync-token/>
            <d:sync-level>1</d:sync-level>
            <d:prop>
                <d:getetag/>
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
    val added: List<String>, // Event URLs
    val modified: List<String>,
    val deleted: List<String>
)

/**
 * CalDAV-specific exception
 */
class CalDavException(
    message: String,
    val httpCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)
