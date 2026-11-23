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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OscCloudModelsTest {

    @Test fun `toDomain maps json fields and defaults missing trial days to zero`() {
        val json = OscCloudStatusJson(
            status = "TRIAL",
            trialDaysLeft = 12,
            memberCount = 3,
            caldavUrl = "https://sync.openschoolcloud.nl/remote.php/dav/family_1/",
            nextcloudUsername = "fam-abc123",
            appPassword = "app-pw"
        )

        val domain = json.toDomain()

        assertEquals("TRIAL", domain.status)
        assertEquals(12, domain.trialDaysLeft)
        assertEquals(3, domain.memberCount)
        assertEquals("https://sync.openschoolcloud.nl/remote.php/dav/family_1/", domain.caldavUrl)
        assertEquals("fam-abc123", domain.nextcloudUsername)
        assertEquals("app-pw", domain.appPassword)
    }

    @Test fun `toDomain parses nextcloud_username from the moshi wire adapter`() {
        val moshi = com.squareup.moshi.Moshi.Builder().build()
        val adapter = moshi.adapter(OscCloudStatusJson::class.java)
        val body = """
            {"status":"TRIAL","trial_days_left":30,"member_count":2,
             "caldav_url":"https://sync.osc.nl/dav/f1/",
             "nextcloud_username":"fam-abc123","app_password":"s3cr3t"}
        """.trimIndent()

        val domain = adapter.fromJson(body)!!.toDomain()

        assertEquals("fam-abc123", domain.nextcloudUsername)
    }

    @Test fun `toDomain defaults trialDaysLeft to zero when absent`() {
        val json = OscCloudStatusJson(status = "ACTIVE", trialDaysLeft = null, memberCount = 2)

        assertEquals(0, json.toDomain().trialDaysLeft)
    }

    @Test fun `shouldDegradeToLocal is true for paused, none and expired`() {
        assertTrue(shouldDegradeToLocal("PAUSED"))
        assertTrue(shouldDegradeToLocal("NONE"))
        assertTrue(shouldDegradeToLocal("EXPIRED"))
    }

    @Test fun `shouldDegradeToLocal is false for active statuses`() {
        assertFalse(shouldDegradeToLocal("TRIAL"))
        assertFalse(shouldDegradeToLocal("ACTIVE"))
        assertFalse(shouldDegradeToLocal("PAST_DUE"))
    }
}
