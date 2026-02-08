package nl.openschoolcloud.calendar.presentation.screens.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.openschoolcloud.calendar.R
import nl.openschoolcloud.calendar.domain.usecase.ParsedEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun QuickCaptureBar(
    onEventParsed: (ParsedEvent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickCaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus when the bar appears
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val canConfirm = uiState.parsedEvent != null &&
        uiState.parsedEvent!!.title.length >= 2 &&
        uiState.input.length >= 3

    Column(modifier = modifier) {
        OutlinedTextField(
            value = uiState.input,
            onValueChange = viewModel::onInputChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.quick_capture_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (uiState.input.isNotEmpty()) {
                    IconButton(onClick = viewModel::clear) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.a11y_close)
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (canConfirm) {
                        keyboardController?.hide()
                        uiState.parsedEvent?.let {
                            onEventParsed(it)
                            viewModel.clear()
                        }
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        AnimatedVisibility(
            visible = uiState.parsedEvent != null && uiState.input.length >= 3,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            uiState.parsedEvent?.let { parsed ->
                QuickCapturePreview(
                    parsedEvent = parsed,
                    canConfirm = canConfirm,
                    onConfirm = {
                        keyboardController?.hide()
                        onEventParsed(parsed)
                        viewModel.clear()
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickCapturePreview(
    parsedEvent: ParsedEvent,
    canConfirm: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parsedEvent.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = formatDateTimePreview(parsedEvent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    parsedEvent.location?.let { location ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = location,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                FilledIconButton(
                    onClick = onConfirm,
                    enabled = canConfirm,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.quick_capture_confirm),
                        tint = if (canConfirm) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Confidence indicator
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { parsedEvent.confidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = when {
                    parsedEvent.confidence >= 0.7f -> MaterialTheme.colorScheme.primary
                    parsedEvent.confidence >= 0.4f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.outline
                },
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }
    }
}

private fun formatDateTimePreview(parsed: ParsedEvent): String {
    val today = LocalDate.now()
    val dateStr = when (parsed.startDate) {
        today -> "Vandaag"
        today.plusDays(1) -> "Morgen"
        today.plusDays(2) -> "Overmorgen"
        else -> parsed.startDate?.format(
            DateTimeFormatter.ofPattern("EEE d MMM", Locale("nl"))
        ) ?: "Geen datum"
    }

    val timeStr = parsed.startTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""
    val endTimeStr = parsed.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""

    return buildString {
        append(dateStr)
        if (timeStr.isNotEmpty()) {
            append(", ")
            append(timeStr)
            if (endTimeStr.isNotEmpty()) {
                append(" - ")
                append(endTimeStr)
            }
        }
    }
}
