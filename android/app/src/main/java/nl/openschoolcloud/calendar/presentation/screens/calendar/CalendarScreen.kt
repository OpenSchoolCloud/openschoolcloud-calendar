package nl.openschoolcloud.calendar.presentation.screens.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.openschoolcloud.calendar.R
import nl.openschoolcloud.calendar.domain.model.Event
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Main calendar screen with week view
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onEventClick: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onCreateEvent: (String?) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showQuickCapture by remember { mutableStateOf(false) }

    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Show sync message
    LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSyncMessage()
        }
    }

    // Show snackbar message (e.g., after quick capture event creation)
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbarMessage()
        }
    }

    Scaffold(
        topBar = {
            CalendarTopBar(
                weekStart = uiState.weekStart,
                isSyncing = uiState.isSyncing,
                onPreviousWeek = viewModel::previousWeek,
                onNextWeek = viewModel::nextWeek,
                onToday = viewModel::goToToday,
                onSync = viewModel::syncAll,
                onSettingsClick = onSettingsClick
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickCapture = !showQuickCapture }
            ) {
                Icon(
                    imageVector = if (showQuickCapture) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = stringResource(R.string.a11y_create_event)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Quick Capture bar (expandable)
            AnimatedVisibility(
                visible = showQuickCapture,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                QuickCaptureBar(
                    onEventParsed = { parsed ->
                        viewModel.createEventFromParsed(parsed)
                        showQuickCapture = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            // Week header with day names and event dots
            WeekHeader(
                weekDays = uiState.getWeekDays(),
                selectedDate = uiState.selectedDate,
                eventsByDay = uiState.eventsByDay,
                onDayClick = viewModel::selectDate
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Week content - day columns + selected day detail
            if (uiState.isLoading && uiState.eventsByDay.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                // Week grid (compact overview)
                WeekContent(
                    weekDays = uiState.getWeekDays(),
                    eventsByDay = uiState.eventsByDay,
                    selectedDate = uiState.selectedDate,
                    onDayClick = viewModel::selectDate,
                    onEventClick = onEventClick,
                    modifier = Modifier.weight(0.4f)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Selected day detail panel
                SelectedDayPanel(
                    date = uiState.selectedDate,
                    events = uiState.getEventsForDay(uiState.selectedDate),
                    onEventClick = onEventClick,
                    modifier = Modifier.weight(0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTopBar(
    weekStart: LocalDate,
    isSyncing: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
    onSync: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val weekEnd = weekStart.plusDays(6)
    val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    // Show month/year for the week
    val displayMonth = if (weekStart.monthValue == weekEnd.monthValue) {
        weekStart.format(monthYearFormatter)
    } else {
        "${weekStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} - ${weekEnd.format(monthYearFormatter)}"
    }

    // Week number (ISO standard, used in NL schools)
    val weekNumber = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear())

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousWeek) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.a11y_previous)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = displayMonth,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.calendar_week, weekNumber),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onNextWeek) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.a11y_next)
                    )
                }
            }
        },
        actions = {
            TextButton(onClick = onToday) {
                Text(
                    text = stringResource(R.string.calendar_today),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton(onClick = onSync, enabled = !isSyncing) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.settings_sync_now)
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.a11y_settings)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun WeekHeader(
    weekDays: List<LocalDate>,
    selectedDate: LocalDate,
    eventsByDay: Map<LocalDate, List<Event>>,
    onDayClick: (LocalDate) -> Unit
) {
    val today = LocalDate.now()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        weekDays.forEach { date ->
            val isToday = date == today
            val isSelected = date == selectedDate
            val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            val hasEvents = (eventsByDay[date]?.size ?: 0) > 0

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onDayClick(date) }
                    .padding(vertical = 4.dp)
            ) {
                // Day name (Ma, Di, etc.)
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isToday -> MaterialTheme.colorScheme.primary
                        isWeekend -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Day number
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isToday -> MaterialTheme.colorScheme.primary
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                else -> Color.Transparent
                            }
                        )
                        .then(
                            if (isSelected && !isToday) {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isToday -> MaterialTheme.colorScheme.onPrimary
                            isSelected -> MaterialTheme.colorScheme.primary
                            isWeekend -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Event dots
                if (hasEvents) {
                    val eventCount = eventsByDay[date]?.size ?: 0
                    val dotCount = eventCount.coerceAtMost(3)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.height(6.dp)
                    ) {
                        repeat(dotCount) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isToday) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                            )
                        }
                    }
                } else {
                    // Placeholder for consistent height
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun WeekContent(
    weekDays: List<LocalDate>,
    eventsByDay: Map<LocalDate, List<Event>>,
    selectedDate: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
    ) {
        weekDays.forEach { date ->
            val events = eventsByDay[date] ?: emptyList()
            val isSelected = date == selectedDate

            DayColumn(
                date = date,
                events = events,
                isSelected = isSelected,
                onDayClick = { onDayClick(date) },
                onEventClick = onEventClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DayColumn(
    date: LocalDate,
    events: List<Event>,
    isSelected: Boolean,
    onDayClick: () -> Unit,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    isWeekend -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            .clickable(onClick = onDayClick)
    ) {
        if (events.isEmpty()) {
            // Empty state - just subtle empty space
            Box(modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(events, key = { it.uid }) { event ->
                    EventChip(
                        event = event,
                        onClick = { onEventClick(event.uid) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EventChip(
    event: Event,
    onClick: () -> Unit
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val startTime = event.dtStart.atZone(ZoneId.systemDefault()).toLocalTime()
    val eventColor = event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = eventColor.copy(alpha = 0.15f)
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left color indicator
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (event.allDay) 24.dp else 36.dp)
                    .background(eventColor)
            )

            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                // Time for non-all-day events
                if (!event.allDay) {
                    Text(
                        text = startTime.format(timeFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = eventColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Event title
                Text(
                    text = event.summary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Selected day detail panel - shows expanded event list for the selected day
 */
@Composable
private fun SelectedDayPanel(
    date: LocalDate,
    events: List<Event>,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nlLocale = Locale("nl", "NL")
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", nlLocale)
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val zoneId = ZoneId.systemDefault()
    val isToday = date == LocalDate.now()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Day header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isToday) {
                    stringResource(R.string.calendar_today) + " - " + date.format(dateFormatter)
                } else {
                    date.format(dateFormatter)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (events.isNotEmpty()) {
                Text(
                    text = "${events.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (events.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.empty_events_today),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.empty_events_today_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Event list
            val sortedEvents = remember(events) {
                events.sortedWith(compareBy({ !it.allDay }, { it.dtStart }))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(sortedEvents, key = { it.uid }) { event ->
                    SelectedDayEventCard(
                        event = event,
                        timeFormatter = timeFormatter,
                        zoneId = zoneId,
                        onClick = { onEventClick(event.uid) }
                    )
                }
                // Bottom spacing for FAB
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun SelectedDayEventCard(
    event: Event,
    timeFormatter: DateTimeFormatter,
    zoneId: ZoneId,
    onClick: () -> Unit
) {
    val eventColor = event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = eventColor.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Color bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(eventColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Title
                Text(
                    text = event.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Time
                val timeText = if (event.allDay) {
                    stringResource(R.string.calendar_all_day)
                } else {
                    val start = event.dtStart.atZone(zoneId).toLocalTime().format(timeFormatter)
                    val end = event.dtEnd?.atZone(zoneId)?.toLocalTime()?.format(timeFormatter)
                    if (end != null) "$start - $end" else start
                }
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = eventColor
                )

                // Location
                event.location?.let { location ->
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
