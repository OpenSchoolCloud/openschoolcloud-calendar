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
package nl.openschoolcloud.calendar.data.remote.osccloud

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Wire shape returned by /v1/sync/trial, /v1/sync/status and /v1/sync/members. */
@JsonClass(generateAdapter = true)
data class OscCloudStatusJson(
    val status: String,
    @Json(name = "trial_days_left") val trialDaysLeft: Int? = null,
    @Json(name = "member_count") val memberCount: Int = 0,
    @Json(name = "caldav_url") val caldavUrl: String? = null,
    // Only present in the /v1/sync/trial response, and only once.
    @Json(name = "app_password") val appPassword: String? = null
)

@JsonClass(generateAdapter = true)
data class TrialRequestJson(
    @Json(name = "family_id") val familyId: String,
    @Json(name = "member_count") val memberCount: Int
)

@JsonClass(generateAdapter = true)
data class MembersRequestJson(
    @Json(name = "member_count") val memberCount: Int
)

/** Domain-level view of the family's OSC-Cloud subscription status. */
data class OscCloudStatus(
    val status: String,
    val trialDaysLeft: Int,
    val memberCount: Int,
    val caldavUrl: String?,
    val appPassword: String? = null
)

fun OscCloudStatusJson.toDomain(): OscCloudStatus = OscCloudStatus(
    status = status,
    trialDaysLeft = trialDaysLeft ?: 0,
    memberCount = memberCount,
    caldavUrl = caldavUrl,
    appPassword = appPassword
)

/**
 * True when the family's OSC-Cloud subscription no longer entitles them to
 * sync and profiles must be soft-degraded back to [nl.openschoolcloud.calendar.domain.model.SyncScope.LOCAL].
 * Local data is never deleted when this happens — only the sync destination changes.
 */
fun shouldDegradeToLocal(status: String): Boolean =
    status == "PAUSED" || status == "NONE" || status == "EXPIRED"
