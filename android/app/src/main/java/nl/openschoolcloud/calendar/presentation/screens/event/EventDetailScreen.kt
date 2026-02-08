package nl.openschoolcloud.calendar.presentation.screens.event

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.openschoolcloud.calendar.R
import nl.openschoolcloud.calendar.domain.model.Attendee
import nl.openschoolcloud.calendar.domain.model.AttendeeStatus
import nl.openschoolcloud.calendar.domain.model.Event
import nl.openschoolcloud.calendar.domain.model.EventStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    @Suppress("UNUSED_PARAMETER") eventId: String,
    onNavigateBack: () -> Unit,
    onEditEvent: () -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.event?.summary ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.a11y_close)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.event_delete)) },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (uiState.event != null) {
                FloatingActionButton(
                    onClick = onEditEvent,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.a11y_edit),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            uiState.event == null && !uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "Event niet gevonden",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                val event = uiState.event!!
                EventDetailContent(
                    event = event,
                    calendarName = uiState.calendar?.displayName,
                    calendarColor = uiState.calendar?.color,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_event_title)) },
            text = { Text(stringResource(R.string.dialog_delete_event_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteEvent(onDeleted = onNavigateBack)
                    }
                ) {
                    Text(
                        stringResource(R.string.event_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun EventDetailContent(
    event: Event,
    calendarName: String?,
    calendarColor: Int?,
    modifier: Modifier = Modifier
) {
    val nlLocale = Locale("nl", "NL")
    val zone = event.timeZone

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Title
        Text(
            text = event.summary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Status badge (if not confirmed)
        if (event.status != EventStatus.CONFIRMED) {
            val (statusText, statusColor) = when (event.status) {
                EventStatus.TENTATIVE -> stringResource(R.string.event_status_tentative) to MaterialTheme.colorScheme.tertiary
                EventStatus.CANCELLED -> stringResource(R.string.event_status_cancelled) to MaterialTheme.colorScheme.error
                else -> "" to MaterialTheme.colorScheme.primary
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Date & Time
        DetailCard {
            DetailRow(
                icon = Icons.Default.AccessTime,
                iconTint = MaterialTheme.colorScheme.primary
            ) {
                val startZoned = event.dtStart.atZone(zone)

                if (event.allDay) {
                    val dateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", nlLocale)
                    Text(
                        text = startZoned.format(dateFormatter),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.calendar_all_day),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val dateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", nlLocale)
                    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(nlLocale)

                    Text(
                        text = startZoned.format(dateFormatter),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    val timeText = if (event.dtEnd != null) {
                        val endZoned = event.dtEnd.atZone(zone)
                        "${startZoned.format(timeFormatter)} - ${endZoned.format(timeFormatter)}"
                    } else {
                        startZoned.format(timeFormatter)
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Location
        if (!event.location.isNullOrBlank()) {
            DetailCard {
                DetailRow(
                    icon = Icons.Default.LocationOn,
                    iconTint = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = event.location,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Description
        if (!event.description.isNullOrBlank()) {
            DetailCard {
                DetailRow(
                    icon = Icons.Default.Description,
                    iconTint = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Calendar
        if (calendarName != null) {
            DetailCard {
                DetailRow(
                    icon = Icons.Default.CalendarMonth,
                    iconTint = MaterialTheme.colorScheme.primary
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (calendarColor != null) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(calendarColor))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = calendarName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        // Organizer
        if (event.organizer != null) {
            DetailCard {
                DetailRow(
                    icon = Icons.Default.Person,
                    iconTint = MaterialTheme.colorScheme.primary
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.event_organizer),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = event.organizer.name ?: event.organizer.email,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (event.organizer.name != null) {
                            Text(
                                text = event.organizer.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Attendees
        if (event.attendees.isNotEmpty()) {
            DetailCard {
                DetailRow(
                    icon = Icons.Default.People,
                    iconTint = MaterialTheme.colorScheme.primary
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.event_attendees),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        event.attendees.forEach { attendee ->
                            AttendeeRow(attendee = attendee)
                        }
                    }
                }
            }
        }

        // Bottom spacing for FAB
        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun DetailCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    iconTint: Color,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(24.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun AttendeeRow(attendee: Attendee) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        val statusColor = when (attendee.status) {
            AttendeeStatus.ACCEPTED -> Color(0xFF4CAF50)
            AttendeeStatus.DECLINED -> MaterialTheme.colorScheme.error
            AttendeeStatus.TENTATIVE -> Color(0xFFFFA726)
            AttendeeStatus.NEEDS_ACTION -> MaterialTheme.colorScheme.outline
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attendee.name ?: attendee.email,
                style = MaterialTheme.typography.bodyMedium
            )
            if (attendee.name != null) {
                Text(
                    text = attendee.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Status text
        val statusText = when (attendee.status) {
            AttendeeStatus.ACCEPTED -> stringResource(R.string.attendee_status_accepted)
            AttendeeStatus.DECLINED -> stringResource(R.string.attendee_status_declined)
            AttendeeStatus.TENTATIVE -> stringResource(R.string.attendee_status_tentative)
            AttendeeStatus.NEEDS_ACTION -> stringResource(R.string.attendee_status_needs_action)
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor
        )
    }
}
