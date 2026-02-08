package nl.openschoolcloud.calendar.presentation.screens.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.openschoolcloud.calendar.R
import nl.openschoolcloud.calendar.domain.model.Event
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Day view with hourly timeline and event blocks
 */
@Composable
fun DayView(
    date: LocalDate,
    events: List<Event>,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    startHour: Int = 7,
    endHour: Int = 22,
    hourHeight: Dp = 60.dp
) {
    val scrollState = rememberScrollState()
    val zoneId = ZoneId.systemDefault()

    // Separate all-day events from timed events
    val allDayEvents = events.filter { it.allDay }
    val timedEvents = events.filter { !it.allDay }

    Column(modifier = modifier.fillMaxSize()) {
        // All-day events section
        if (allDayEvents.isNotEmpty()) {
            AllDaySection(
                events = allDayEvents,
                onEventClick = onEventClick
            )
            HorizontalDivider()
        }

        // Timed events with hour grid
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            // Hour grid
            Column {
                for (hour in startHour..endHour) {
                    HourRow(
                        hour = hour,
                        height = hourHeight
                    )
                }
            }

            // Event blocks
            timedEvents.forEach { event ->
                val startTime = event.dtStart.atZone(zoneId).toLocalTime()
                val endTime = event.dtEnd?.atZone(zoneId)?.toLocalTime()
                    ?: startTime.plusHours(1)

                // Calculate position and height
                val startMinutes = startTime.hour * 60 + startTime.minute
                val endMinutes = endTime.hour * 60 + endTime.minute
                val startOffset = ((startMinutes - startHour * 60) / 60f * hourHeight.value).dp
                val eventHeight = ((endMinutes - startMinutes) / 60f * hourHeight.value).dp
                    .coerceAtLeast(24.dp) // Minimum height

                EventBlock(
                    event = event,
                    onClick = { onEventClick(event.uid) },
                    modifier = Modifier
                        .padding(start = TIME_COLUMN_WIDTH + 4.dp, end = 4.dp)
                        .offset(y = startOffset)
                        .height(eventHeight)
                        .fillMaxWidth()
                )
            }

            // Current time indicator
            if (date == LocalDate.now()) {
                CurrentTimeIndicator(
                    startHour = startHour,
                    hourHeight = hourHeight
                )
            }
        }
    }
}

@Composable
private fun AllDaySection(
    events: List<Event>,
    onEventClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            text = stringResource(R.string.calendar_all_day),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        events.forEach { event ->
            AllDayEventChip(
                event = event,
                onClick = { onEventClick(event.uid) }
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AllDayEventChip(
    event: Event,
    onClick: () -> Unit
) {
    val eventColor = event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        color = eventColor.copy(alpha = 0.15f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .background(eventColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = event.summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HourRow(
    hour: Int,
    height: Dp
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val timeText = LocalTime.of(hour, 0).format(timeFormatter)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        // Time label
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(TIME_COLUMN_WIDTH)
                .padding(end = 8.dp, top = 4.dp)
        )

        // Grid line
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

@Composable
private fun EventBlock(
    event: Event,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eventColor = event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zoneId = ZoneId.systemDefault()

    val startTime = event.dtStart.atZone(zoneId).toLocalTime().format(timeFormatter)
    val endTime = event.dtEnd?.atZone(zoneId)?.toLocalTime()?.format(timeFormatter) ?: ""

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = eventColor.copy(alpha = 0.15f)
        )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left color bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(eventColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = event.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (endTime.isNotEmpty()) {
                    Text(
                        text = "$startTime - $endTime",
                        style = MaterialTheme.typography.labelSmall,
                        color = eventColor
                    )
                }

                event.location?.let { location ->
                    Text(
                        text = location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentTimeIndicator(
    startHour: Int,
    hourHeight: Dp
) {
    val currentTime = LocalTime.now()
    val currentMinutes = currentTime.hour * 60 + currentTime.minute
    val offset = ((currentMinutes - startHour * 60) / 60f * hourHeight.value).dp

    val indicatorColor = MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = offset)
    ) {
        // Line
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .padding(start = TIME_COLUMN_WIDTH)
        ) {
            drawLine(
                color = indicatorColor,
                start = Offset.Zero,
                end = Offset(size.width, 0f),
                strokeWidth = 2f
            )
        }

        // Circle indicator
        Canvas(
            modifier = Modifier
                .offset(x = TIME_COLUMN_WIDTH - 4.dp, y = (-4).dp)
        ) {
            drawCircle(
                color = indicatorColor,
                radius = 4.dp.toPx()
            )
        }
    }
}

private val TIME_COLUMN_WIDTH = 48.dp

/**
 * Compact event list for a single day (used in week view expansion)
 */
@Composable
fun DayEventList(
    events: List<Event>,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val zoneId = ZoneId.systemDefault()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    // Sort events by time (all-day first, then by start time)
    val sortedEvents = remember(events) {
        events.sortedWith(compareBy({ !it.allDay }, { it.dtStart }))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        if (sortedEvents.isEmpty()) {
            Text(
                text = stringResource(R.string.calendar_no_events),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            sortedEvents.forEach { event ->
                val eventColor = event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                val timeText = if (event.allDay) {
                    stringResource(R.string.calendar_all_day)
                } else {
                    event.dtStart.atZone(zoneId).toLocalTime().format(timeFormatter)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEventClick(event.uid) },
                    color = eventColor.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(40.dp)
                                .background(eventColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            event.location?.let { location ->
                                Text(
                                    text = location,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
