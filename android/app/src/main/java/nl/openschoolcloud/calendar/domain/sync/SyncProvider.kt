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
package nl.openschoolcloud.calendar.domain.sync

import nl.openschoolcloud.calendar.domain.model.SyncScope

/**
 * The seam between the app and a sync destination. The betaalde OSC-sync
 * (subsysteem C) fills OscCloudProvider later; the app architecture does not
 * change when it lands.
 */
interface SyncProvider {
    val scope: SyncScope
    fun isAvailable(): Boolean
}
