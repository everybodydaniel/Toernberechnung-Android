package com.example.trnberechnung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.model.PlannerEvent
import com.example.trnberechnung.model.CrewMember
import com.example.trnberechnung.model.isReadyForExternalAction
import com.example.trnberechnung.model.withParticipantIds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlannerEventBottomSheet(
    event: PlannerEvent,
    crewMembers: List<CrewMember>,
    onDismiss: () -> Unit,
    onSave: (PlannerEvent) -> Unit,
    onDelete: () -> Unit,
    onShare: (PlannerEvent) -> Unit,
    onAddToCalendar: (PlannerEvent) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    // Lokale States für alle Felder
    var title by remember { mutableStateOf(event.title) }
    var description by remember { mutableStateOf(event.description) }
    var startDate by remember { mutableStateOf(event.startDate) }
    var endDate by remember { mutableStateOf(event.endDate) }
    var startTime by remember { mutableStateOf(event.startTime ?: "") }
    var endTime by remember { mutableStateOf(event.endTime ?: "") }
    var location by remember { mutableStateOf(event.location ?: "") }
    var selectedParticipantIds by remember(event.id) { mutableStateOf(event.participantIds.toSet()) }
    val editedEvent =
        event.copy(
            title = title,
            description = description,
            startDate = startDate,
            endDate = endDate,
            startTime = startTime.ifBlank { null },
            endTime = endTime.ifBlank { null },
            location = location.ifBlank { null },
        )
            .withParticipantIds(selectedParticipantIds)
    val showExternalActions = event.title.isNotBlank() || title.isNotBlank()
    val canUseExternalActions = editedEvent.isReadyForExternalAction()


    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    // Dynamische Farben passend zum Crewspace-Look
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accentColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val surfaceColor = if (isDark) Color(0xFF161E26) else Color.White
    val cardBgColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val textColor = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .width(40.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(secondaryText.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (event.title.isEmpty()) "Neuer Termin" else "Termin bearbeiten",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                    letterSpacing = (-0.5).sp
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(cardBgColor, CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Schließen",
                        modifier = Modifier.size(20.dp),
                        tint = textColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

                // Titel Input
                EditField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Titel des Termins",
                    icon = Icons.Default.Title,
                    placeholder = "z.B. Ablegen Richtung Norderney",
                    accentColor = accentColor,
                    textColor = textColor,
                    secondaryText = secondaryText,
                    cardBgColor = cardBgColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Datum Row
                Text(
                    text = "ZEITRAUM",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = secondaryText,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).clickable { showStartDatePicker = true }) {
                        ReadOnlyField(
                            label = "VON",
                            value = startDate.format(dateFormatter),
                            icon = Icons.Default.CalendarToday,
                            accentColor = accentColor,
                            textColor = textColor,
                            secondaryText = secondaryText,
                            cardBgColor = cardBgColor
                        )
                    }
                    Box(modifier = Modifier.weight(1f).clickable { showEndDatePicker = true }) {
                        ReadOnlyField(
                            label = "BIS",
                            value = endDate.format(dateFormatter),
                            icon = Icons.Default.CalendarToday,
                            accentColor = accentColor,
                            textColor = textColor,
                            secondaryText = secondaryText,
                            cardBgColor = cardBgColor
                        )
                    }
                }

                if (showStartDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showStartDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = { showStartDatePicker = false }) { Text("OK") }
                        }
                    ) {
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        )
                        DatePicker(state = datePickerState)
                        LaunchedEffect(datePickerState.selectedDateMillis) {
                            datePickerState.selectedDateMillis?.let {
                                startDate = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                if (endDate.isBefore(startDate)) endDate = startDate
                            }
                        }
                    }
                }

                if (showEndDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showEndDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = { showEndDatePicker = false }) { Text("OK") }
                        }
                    ) {
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = endDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        )
                        DatePicker(state = datePickerState)
                        LaunchedEffect(datePickerState.selectedDateMillis) {
                            datePickerState.selectedDateMillis?.let {
                                val selected = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                if (!selected.isBefore(startDate)) {
                                    endDate = selected
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Zeit Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).clickable { showStartTimePicker = true }) {
                        ReadOnlyField(
                            label = "Uhrzeit Start",
                            value = startTime.ifBlank { "--:--" },
                            icon = Icons.Default.AccessTime,
                            accentColor = accentColor,
                            textColor = textColor,
                            secondaryText = secondaryText,
                            cardBgColor = cardBgColor
                        )
                    }
                    Box(modifier = Modifier.weight(1f).clickable { showEndTimePicker = true }) {
                        ReadOnlyField(
                            label = "Uhrzeit Ende",
                            value = endTime.ifBlank { "--:--" },
                            icon = Icons.Default.AccessTime,
                            accentColor = accentColor,
                            textColor = textColor,
                            secondaryText = secondaryText,
                            cardBgColor = cardBgColor
                        )
                    }
                }

                if (showStartTimePicker) {
                    val initialHour = startTime.substringBefore(":").toIntOrNull() ?: 12
                    val initialMinute = startTime.substringAfter(":").toIntOrNull() ?: 0
                    val timePickerState = rememberTimePickerState(initialHour, initialMinute, is24Hour = true)

                    AlertDialog(
                        onDismissRequest = { showStartTimePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                startTime = String.format(java.util.Locale.getDefault(), "%02d:%02d", timePickerState.hour, timePickerState.minute)
                                showStartTimePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showStartTimePicker = false }) { Text("Abbrechen") }
                        },
                        text = {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TimePicker(state = timePickerState)
                            }
                        }
                    )
                }

                if (showEndTimePicker) {
                    val initialHour = endTime.substringBefore(":").toIntOrNull() ?: 12
                    val initialMinute = endTime.substringAfter(":").toIntOrNull() ?: 0
                    val timePickerState = rememberTimePickerState(initialHour, initialMinute, is24Hour = true)

                    AlertDialog(
                        onDismissRequest = { showEndTimePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                endTime = String.format(java.util.Locale.getDefault(), "%02d:%02d", timePickerState.hour, timePickerState.minute)
                                showEndTimePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEndTimePicker = false }) { Text("Abbrechen") }
                        },
                        text = {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TimePicker(state = timePickerState)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Ort Input
                EditField(
                    value = location,
                    onValueChange = { location = it },
                    label = "Ort / Hafen",
                    icon = Icons.Default.LocationOn,
                    placeholder = "z.B. Greetsiel",
                    accentColor = accentColor,
                    textColor = textColor,
                    secondaryText = secondaryText,
                    cardBgColor = cardBgColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Beschreibung
                EditField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Beschreibung (optional)",
                    icon = Icons.AutoMirrored.Outlined.Notes,
                    placeholder = "Details zum Termin...",
                    singleLine = false,
                    minLines = 3,
                    accentColor = accentColor,
                    textColor = textColor,
                    secondaryText = secondaryText,
                    cardBgColor = cardBgColor
                )

                Spacer(modifier = Modifier.height(20.dp))

                ParticipantSelection(
                    crewMembers = crewMembers,
                    selectedParticipantIds = selectedParticipantIds,
                    onSelectionChange = { memberId, selected ->
                        selectedParticipantIds =
                            if (selected) selectedParticipantIds + memberId else selectedParticipantIds - memberId
                    },
                    accentColor = accentColor,
                    textColor = textColor,
                    secondaryText = secondaryText,
                    cardBgColor = cardBgColor,
                )

                Spacer(modifier = Modifier.height(28.dp))

                if (showExternalActions) {
                    Text(
                        text = "TERMIN WEITERGEBEN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = secondaryText,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                    OutlinedButton(
                        onClick = { onShare(editedEvent) },
                        enabled = canUseExternalActions,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Termin teilen", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onAddToCalendar(editedEvent) },
                        enabled = canUseExternalActions,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Zum Kalender hinzufügen", fontWeight = FontWeight.Bold)
                    }
                    if (!canUseExternalActions) {
                        Text(
                            text = "Bitte ergänze einen gültigen Titel und korrekte Datums-/Zeitangaben.",
                            fontSize = 12.sp,
                            color = secondaryText,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (event.title.isNotEmpty()) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.background(Color.Red.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onSave(editedEvent) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(14.dp),
                            enabled = title.isNotBlank(),
                            modifier = Modifier.height(48.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Text("Speichern", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
    }
}

@Composable
private fun ParticipantSelection(
    crewMembers: List<CrewMember>,
    selectedParticipantIds: Set<Int>,
    onSelectionChange: (memberId: Int, selected: Boolean) -> Unit,
    accentColor: Color,
    textColor: Color,
    secondaryText: Color,
    cardBgColor: Color,
) {
    Text(
        text = "Wer ist dabei?",
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = secondaryText,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )

    if (crewMembers.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = cardBgColor,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, secondaryText.copy(alpha = 0.2f)),
        ) {
            Text(
                text = "Für diesen Törn sind derzeit keine Crewmitglieder verfügbar.",
                color = secondaryText,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        crewMembers.forEach { member ->
            val selected = member.id in selectedParticipantIds
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = selected,
                            role = Role.Checkbox,
                            onValueChange = { onSelectionChange(member.id, it) },
                        ),
                color = if (selected) accentColor.copy(alpha = 0.1f) else cardBgColor,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (selected) accentColor.copy(alpha = 0.55f) else secondaryText.copy(alpha = 0.2f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = member.name.trim().take(1).uppercase(),
                            color = accentColor,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = member.name,
                        modifier = Modifier.weight(1f),
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Checkbox(
                        checked = selected,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = accentColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyField(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    textColor: Color,
    secondaryText: Color,
    cardBgColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = secondaryText,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBgColor,
            border = BorderStroke(1.dp, secondaryText.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = accentColor)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            }
        }
    }
}

@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    placeholder: String,
    singleLine: Boolean = true,
    minLines: Int = 1,
    accentColor: Color,
    textColor: Color,
    secondaryText: Color,
    cardBgColor: Color,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = secondaryText,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 15.sp, color = secondaryText.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = accentColor) },
            shape = RoundedCornerShape(16.dp),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = secondaryText.copy(alpha = 0.2f),
                focusedContainerColor = cardBgColor,
                unfocusedContainerColor = cardBgColor,
                cursorColor = accentColor,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}
