package com.example.trnberechnung.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.trnberechnung.model.*
import com.example.trnberechnung.viewmodel.CrewspaceViewModel
import com.example.trnberechnung.logic.ValidationUtils
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

import androidx.compose.ui.graphics.luminance
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ══════════════════════════════════════════════════════════════
// Crewspace Farbpalette (Moderneres Look & Feel)
// ══════════════════════════════════════════════════════════════
private val CrewspaceBg: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF0A0F14) else Color(0xFFF8F9FA)

private val CrewspaceSurface: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF161E26) else Color.White

private val CrewspacePrimary: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF3B82F6) else Color(0xFF1E3A8A)

private val CrewspaceAccent: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF60A5FA) else Color(0xFF2563EB)

private val CrewspaceTextPrimary: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFF1F5F9) else Color(0xFF111827)

private val CrewspaceTextSecondary: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF94A3B8) else Color(0xFF6B7280)

private val CrewspaceTabBg: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF1E293B) else Color(0xFFF1F5F9)

private val CrewspaceTabActive: Color
    @Composable get() = CrewspaceAccent

private val CrewspaceTabActiveText: Color = Color.White

private val CrewspaceTabInactiveText: Color
    @Composable get() = CrewspaceTextSecondary

private val CrewspaceDivider: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF1E293B) else Color(0xFFE5E7EB)

private val CrewspaceCardBg: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF1E293B) else Color(0xFFF3F4F6)

private val CrewspaceUnreadBadge = Color(0xFFEF4444)

/**
 * Haupt-Composable für den gesamten Crewspace-Screen.
 * Enthält Header, Segmented Tabs und routet zu den drei Sub-Screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewspaceScreen(
    viewModel: CrewspaceViewModel,
    topOverlayClearance: Dp = 0.dp,
    bottomOverlayClearance: Dp = 0.dp
) {
    val uiState by viewModel.uiState.collectAsState()
    val adaptiveLayout = currentAdaptiveLayout()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_crewspace")
            .background(CrewspaceBg)
            .padding(bottom = bottomOverlayClearance),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(topOverlayClearance + 4.dp))
        // ── Header ──
        CrewspaceHeader(adaptiveLayout)

        // ── Segmented Tab Row ──
        CrewspaceSegmentedTabs(
            selectedTab = uiState.selectedTab,
            onTabSelected = { viewModel.selectTab(it) },
            adaptiveLayout = adaptiveLayout,
        )

        // ── Tab-Content ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier =
                    if (adaptiveLayout.isTablet) {
                        Modifier
                            .fillMaxHeight()
                            .widthIn(max = adaptiveLayout.mainContentMaxWidth)
                            .fillMaxWidth()
                    } else {
                        Modifier.fillMaxSize()
                    },
            ) {
                when (uiState.selectedTab) {
                    CrewspaceTab.PLANUNG -> {
                        PlanungTabContent(uiState = uiState, viewModel = viewModel)
                    }
                    CrewspaceTab.CREW -> {
                        CrewTabContent(uiState = uiState, viewModel = viewModel)
                    }
                }
            }
        }
    }

    // ── Deletion Confirmation Dialogs ──
    uiState.memberToDelete?.let { member ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteCrew() },
            title = { Text("Crewmitglied entfernen") },
            text = { Text("Möchtest du ${member.name} wirklich von Bord entfernen?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteCrew() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("Entfernen", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteCrew() }) {
                    Text("Abbrechen")
                }
            },
            containerColor = CrewspaceSurface,
            titleContentColor = CrewspaceTextPrimary,
            textContentColor = CrewspaceTextSecondary
        )
    }

    uiState.eventToDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeletePlannerEvent() },
            title = { Text("Termin löschen") },
            text = { Text("Möchtest du den Termin '${event.title}' wirklich unwiderruflich löschen?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeletePlannerEvent() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("Löschen", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeletePlannerEvent() }) {
                    Text("Abbrechen")
                }
            },
            containerColor = CrewspaceSurface,
            titleContentColor = CrewspaceTextPrimary,
            textContentColor = CrewspaceTextSecondary
        )
    }

    uiState.editingMember?.let { member ->
        EditCrewMemberDialog(
            member = member,
            onDismiss = { viewModel.cancelEditingCrew() },
            onSave = { updated ->
                viewModel.updateCrew(updated)
                viewModel.cancelEditingCrew()
            }
        )
    }
}

@Composable
private fun EditCrewMemberDialog(
    member: CrewMember,
    onDismiss: () -> Unit,
    onSave: (CrewMember) -> Unit
) {
    val adaptiveLayout = currentAdaptiveLayout()
    var name by remember { mutableStateOf(member.name) }
    var emergencyContact by remember { mutableStateOf(member.emergencyContact) }
    var phone by remember { mutableStateOf(member.phone) }
    var medicalNotes by remember { mutableStateOf(member.medicalNotes) }
    var role by remember { mutableStateOf(member.crewRole) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crewmitglied bearbeiten") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CrewspaceTextField(value = name, onValueChange = { name = it }, placeholder = "Name")

                Text(
                    "Rolle",
                    fontSize = if (adaptiveLayout.isTablet) 15.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrewspaceTextSecondary,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(CrewRole.SKIPPER, CrewRole.CO_SKIPPER, CrewRole.NAVIGATION).forEach { r ->
                        val isSelected = role == r
                        Surface(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .then(if (adaptiveLayout.isTablet) Modifier.heightIn(min = 48.dp) else Modifier)
                                    .clickable { role = r },
                            color = if (isSelected) CrewspaceAccent else CrewspaceCardBg,
                            shape = RoundedCornerShape(8.dp),
                            border = if (!isSelected) BorderStroke(1.dp, CrewspaceDivider) else null
                        ) {
                            Text(
                                r.label,
                                modifier = Modifier.padding(vertical = if (adaptiveLayout.isTablet) 12.dp else 8.dp),
                                textAlign = TextAlign.Center,
                                fontSize = if (adaptiveLayout.isTablet) 14.sp else 11.sp,
                                color = if (isSelected) Color.White else CrewspaceTextPrimary,
                            )
                        }
                    }
                }

                CrewspaceTextField(value = emergencyContact, onValueChange = { emergencyContact = it }, placeholder = "Notfallkontakt")
                CrewspaceTextField(value = phone, onValueChange = { phone = it }, placeholder = "Telefon", keyboardType = KeyboardType.Phone)
                CrewspaceTextField(value = medicalNotes, onValueChange = { medicalNotes = it }, placeholder = "Medizinische Hinweise")
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(member.copy(
                    name = name,
                    emergencyContact = emergencyContact,
                    phone = phone,
                    medicalNotes = medicalNotes,
                    role = role.name,
                    rank = role.label
                ))
            }, colors = ButtonDefaults.buttonColors(containerColor = CrewspaceAccent)) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
        containerColor = CrewspaceSurface,
        titleContentColor = CrewspaceTextPrimary
    )
}
// Header: "Crewspace" Titel + Untertitel + Edit-Button
// ══════════════════════════════════════════════════════════════

@Composable
private fun CrewspaceHeader(adaptiveLayout: AdaptiveLayout) {
    val isLandscape = adaptiveLayout.isLandscape
    val widthModifier =
        if (adaptiveLayout.isTablet) {
            Modifier.widthIn(max = adaptiveLayout.mainContentMaxWidth).fillMaxWidth()
        } else {
            Modifier.fillMaxWidth()
        }
    Column(
        modifier =
            widthModifier.padding(
                horizontal = if (adaptiveLayout.isTablet) adaptiveLayout.horizontalScreenPadding else 20.dp,
                vertical = if (adaptiveLayout.isTablet) 10.dp else if (isLandscape) 2.dp else 12.dp,
            ),
    ) {
        Text(
            text = "Crewspace",
            fontSize = if (adaptiveLayout.isTablet) 36.sp else if (isLandscape) 20.sp else 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CrewspacePrimary,
            letterSpacing = (-0.5).sp
        )
        if (adaptiveLayout.isTablet || !isLandscape) {
            Text(
                text = "Crew und Termine",
                fontSize = if (adaptiveLayout.isTablet) 17.sp else 14.sp,
                color = CrewspaceTextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Segmented Tab Row (iOS-Stil)
// ══════════════════════════════════════════════════════════════

private data class TabItem(
    val tab: CrewspaceTab,
    val icon: ImageVector,
    val label: String
)

private val tabItems = listOf(
    TabItem(CrewspaceTab.PLANUNG, Icons.Outlined.DateRange, "Planung"),
    TabItem(CrewspaceTab.CREW, Icons.Outlined.Groups, "Crew"),
)

@Composable
private fun CrewspaceSegmentedTabs(
    selectedTab: CrewspaceTab,
    onTabSelected: (CrewspaceTab) -> Unit,
    adaptiveLayout: AdaptiveLayout,
) {
    val isLandscape = adaptiveLayout.isLandscape
    val widthModifier =
        if (adaptiveLayout.isTablet) {
            Modifier.widthIn(max = adaptiveLayout.mainContentMaxWidth).fillMaxWidth()
        } else {
            Modifier.fillMaxWidth()
        }
    Surface(
        modifier =
            widthModifier.padding(
                horizontal = if (adaptiveLayout.isTablet) adaptiveLayout.horizontalScreenPadding else 20.dp,
                vertical = if (adaptiveLayout.isTablet) 8.dp else if (isLandscape) 2.dp else 8.dp,
            ),
        color = CrewspaceTabBg,
        shape = RoundedCornerShape(if (adaptiveLayout.isTablet) 20.dp else 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabItems.forEach { item ->
                val isSelected = selectedTab == item.tab

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (adaptiveLayout.isTablet) 56.dp else 40.dp)
                        .clip(RoundedCornerShape(if (adaptiveLayout.isTablet) 16.dp else 12.dp))
                        .background(
                            if (isSelected) CrewspaceTabActive else Color.Transparent
                        )
                        .clickable { onTabSelected(item.tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) CrewspaceTabActiveText else CrewspaceTabInactiveText,
                            modifier = Modifier.size(if (adaptiveLayout.isTablet) 24.dp else 18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.label,
                            fontSize = if (adaptiveLayout.isTablet) 17.sp else 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CrewspaceTabActiveText else CrewspaceTabInactiveText
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Suchleiste
// ══════════════════════════════════════════════════════════════

@Composable
private fun CrewspaceSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CrewspaceSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = "Suchen",
            tint = CrewspaceTextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        if (query.isEmpty()) {
            Text(
                text = "Crew durchsuchen",
                fontSize = 15.sp,
                color = CrewspaceTextSecondary
            )
        }
        // Hinweis: In einer vollständigen Implementierung würde hier ein
        // BasicTextField stehen. Für das Grundgerüst reicht der visuelle Platzhalter.
    }
}

// ══════════════════════════════════════════════════════════════
// Planung Tab – Vollständige Implementierung
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanungTabContent(uiState: CrewspaceUiState, viewModel: CrewspaceViewModel) {
    val scrollState = rememberScrollState()
    val eventsForDay = viewModel.eventsForSelectedDate()
    val adaptiveLayout = currentAdaptiveLayout()

    var eventToEdit by remember { mutableStateOf<PlannerEvent?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = if (adaptiveLayout.isTablet) adaptiveLayout.horizontalScreenPadding else 16.dp,
                    vertical = if (adaptiveLayout.isTablet) 12.dp else 8.dp,
                ),
        verticalArrangement =
            Arrangement.spacedBy(if (adaptiveLayout.isTablet) TabletLayoutTokens.SectionSpacing else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Kalender-Card ──
        CalendarCard(uiState = uiState, viewModel = viewModel)

        // ── Tages-Detail-Card ──
        DayDetailCard(
            selectedDate = uiState.selectedDate,
            events = eventsForDay,
            onAddEvent = {
                eventToEdit = PlannerEvent(startDate = uiState.selectedDate, endDate = uiState.selectedDate, title = "")
            },
            onDeleteEvent = { viewModel.deletePlannerEvent(it) },
            onEventClick = { eventToEdit = it }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    val context = LocalContext.current
    val shareEventExternally = { event: PlannerEvent ->
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val shareText = buildString {
            append("⚓ *TIDE NODE TERMIN* ⚓\n\n")
            append("📍 *${event.title.uppercase()}*\n")
            append("──────────────────\n")

            if (event.startDate == event.endDate) {
                append("📅 *Datum:* ${event.startDate.format(dateFormatter)}\n")
            } else {
                append("📅 *Zeitraum:* ${event.startDate.format(dateFormatter)} bis ${event.endDate.format(dateFormatter)}\n")
            }

            if (!event.startTime.isNullOrBlank()) {
                append("⏰ *Zeit:* ${event.startTime}")
                if (!event.endTime.isNullOrBlank()) append(" - ${event.endTime}")
                append(" Uhr\n")
            }

            if (!event.location.isNullOrBlank()) {
                append("🗺️ *Ort:* ${event.location}\n")
            }

            if (event.description.isNotBlank()) {
                append("\n📝 *Details:*\n")
                append(event.description)
                append("\n")
            }

            append("──────────────────\n")
            append("_Gesendet via Tide Node_")
        }
        val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = android.content.Intent.createChooser(sendIntent, "Termin teilen")
        context.startActivity(shareIntent)
    }

    if (eventToEdit != null) {
        EditPlannerEventBottomSheet(
            event = eventToEdit!!,
            onDismiss = { eventToEdit = null },
            onSave = { updatedEvent ->
                if (uiState.plannerEvents.any { it.id == updatedEvent.id }) {
                    viewModel.updatePlannerEvent(updatedEvent)
                } else {
                    viewModel.addPlannerEvent(updatedEvent)
                }
                eventToEdit = null
            },
            onDelete = {
                viewModel.deletePlannerEvent(eventToEdit!!)
                eventToEdit = null
            },
            onExternalShare = { event -> shareEventExternally(event) }
        )
    }
}

// ── Custom Kalender-Card ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarCard(uiState: CrewspaceUiState, viewModel: CrewspaceViewModel) {
    val adaptiveLayout = currentAdaptiveLayout()
    val yearMonth = YearMonth.from(uiState.currentMonth)
    val germanLocale = Locale.GERMAN
    val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, germanLocale)
        .replaceFirstChar { it.titlecase(germanLocale) }
    val year = yearMonth.year

    Surface(
        modifier =
            if (adaptiveLayout.isTablet) {
                Modifier
                    .widthIn(max = if (adaptiveLayout.isLargeTablet) 720.dp else 640.dp)
                    .fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            },
        color = CrewspaceSurface,
        shape = RoundedCornerShape(if (adaptiveLayout.isTablet) TabletLayoutTokens.CardCornerRadius else 24.dp),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier =
                Modifier.padding(if (adaptiveLayout.isTablet) TabletLayoutTokens.CardPadding else 20.dp),
        ) {
            // ── Monat-Navigation ──
            var showMonthYearPicker by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateMonth(false) },
                    modifier =
                        Modifier
                            .size(if (adaptiveLayout.isTablet) 52.dp else 40.dp)
                            .background(CrewspaceCardBg, CircleShape),
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = CrewspaceAccent)
                }

                Surface(
                    onClick = { showMonthYearPicker = true },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$monthName $year",
                            fontSize = if (adaptiveLayout.isTablet) 24.sp else 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CrewspaceTextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = CrewspaceTextSecondary,
                            modifier = Modifier.size(if (adaptiveLayout.isTablet) 24.dp else 20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.navigateMonth(true) },
                    modifier =
                        Modifier
                            .size(if (adaptiveLayout.isTablet) 52.dp else 40.dp)
                            .background(CrewspaceCardBg, CircleShape),
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CrewspaceAccent)
                }
            }

            if (showMonthYearPicker) {
                val initialDate = uiState.currentMonth
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = initialDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )

                DatePickerDialog(
                    onDismissRequest = { showMonthYearPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
            datePickerState.selectedDateMillis?.let {
                                val selectedDate = Instant.ofEpochMilli(it)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                viewModel.setCurrentMonth(selectedDate)
                                viewModel.selectDate(selectedDate)
                            }
                            showMonthYearPicker = false
                        }) { Text("Auswählen") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showMonthYearPicker = false }) { Text("Abbrechen") }
                    }
                ) {
                    DatePicker(
                        state = datePickerState,
                        title = {
                            Text(
                                "Monat & Jahr wählen",
                                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrewspaceAccent
                            )
                        },
                        showModeToggle = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Wochentag-Header ──
            val dayLabels = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                dayLabels.forEach { day ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day,
                            fontSize = if (adaptiveLayout.isTablet) 15.sp else 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CrewspaceTextSecondary.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Kalender-Grid ──
            val firstDayOfMonth = yearMonth.atDay(1)
            val daysInMonth = yearMonth.lengthOfMonth()
            val startDayOfWeek = firstDayOfMonth.dayOfWeek.value
            val totalCells = startDayOfWeek - 1 + daysInMonth
            val rows = (totalCells + 6) / 7
            val today = LocalDate.now()

            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - (startDayOfWeek - 1) + 1

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayNumber in 1..daysInMonth) {
                                val date = yearMonth.atDay(dayNumber)
                                val isSelected = date == uiState.selectedDate
                                val isToday = date == today
                                val hasEvents = uiState.plannerEvents.any { event ->
                                    !date.isBefore(event.startDate) && !date.isAfter(event.endDate)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(if (adaptiveLayout.isTablet) 48.dp else 40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isSelected -> CrewspaceAccent
                                                isToday -> CrewspaceAccent.copy(alpha = 0.15f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable { viewModel.selectDate(date) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$dayNumber",
                                            fontSize = if (adaptiveLayout.isTablet) 17.sp else 15.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                isSelected -> Color.White
                                                isToday -> CrewspaceAccent
                                                else -> CrewspaceTextPrimary
                                            }
                                        )
                                        if (hasEvents && !isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(CrewspaceAccent)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Tages-Detail-Card ─────────────────────────────────────────

@Composable
private fun DayDetailCard(
    selectedDate: LocalDate,
    events: List<PlannerEvent>,
    onAddEvent: () -> Unit,
    onDeleteEvent: (PlannerEvent) -> Unit,
    onEventClick: (PlannerEvent) -> Unit
) {
    val adaptiveLayout = currentAdaptiveLayout()
    val germanLocale = Locale.GERMAN
    val dayOfWeek = selectedDate.dayOfWeek
        .getDisplayName(TextStyle.FULL, germanLocale)
        .replaceFirstChar { it.titlecase(germanLocale) }
    val day = selectedDate.dayOfMonth
    val month = selectedDate.month
        .getDisplayName(TextStyle.FULL, germanLocale)
        .replaceFirstChar { it.titlecase(germanLocale) }

    Surface(
        modifier =
            if (adaptiveLayout.isTablet) {
                Modifier.widthIn(max = adaptiveLayout.compactContentMaxWidth).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            },
        color = CrewspaceSurface,
        shape = RoundedCornerShape(if (adaptiveLayout.isTablet) TabletLayoutTokens.CardCornerRadius else 24.dp),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier =
                Modifier.padding(if (adaptiveLayout.isTablet) TabletLayoutTokens.CardPadding else 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dayOfWeek,
                        fontSize = if (adaptiveLayout.isTablet) 17.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrewspaceAccent,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$day. $month",
                        fontSize = if (adaptiveLayout.isTablet) 24.sp else 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CrewspaceTextPrimary
                    )
                }

                Button(
                    onClick = onAddEvent,
                    colors = ButtonDefaults.buttonColors(containerColor = CrewspaceAccent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(if (adaptiveLayout.isTablet) 52.dp else 40.dp),
                    contentPadding =
                        PaddingValues(horizontal = if (adaptiveLayout.isTablet) 20.dp else 16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(if (adaptiveLayout.isTablet) 24.dp else 18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Termin",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (adaptiveLayout.isTablet) 17.sp else 14.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = CrewspaceDivider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            if (events.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (adaptiveLayout.isTablet) 68.dp else 56.dp)
                            .clip(CircleShape)
                            .background(CrewspaceCardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.DateRange,
                            contentDescription = null,
                            tint = CrewspaceTextSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.size(if (adaptiveLayout.isTablet) 34.dp else 28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Alles ruhig heute",
                        fontSize = if (adaptiveLayout.isTablet) 19.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrewspaceTextSecondary
                    )
                }
            } else {
                events.forEach { event ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onEventClick(event) },
                        color = CrewspaceCardBg,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(32.dp)
                                    .clip(CircleShape)
                                    .background(CrewspaceAccent)
                            )
                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = event.title,
                                        fontSize = if (adaptiveLayout.isTablet) 18.sp else 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CrewspaceTextPrimary
                                    )
                                    if (event.startTime != null) {
                                        Text(
                                            text = " • ${event.startTime}",
                                            fontSize = if (adaptiveLayout.isTablet) 15.sp else 13.sp,
                                            color = CrewspaceAccent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                val subText = buildString {
                                    if (!event.location.isNullOrBlank()) {
                                        append(event.location)
                                        if (event.description.isNotBlank()) append(" • ")
                                    }
                                    append(event.description)
                                }

                                if (subText.isNotBlank()) {
                                    Text(
                                        text = subText,
                                        fontSize = if (adaptiveLayout.isTablet) 14.sp else 12.sp,
                                        color = CrewspaceTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDeleteEvent(event) },
                                modifier = Modifier.size(if (adaptiveLayout.isTablet) 48.dp else 32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Löschen",
                                    tint = Color(0xFFEF4444).copy(alpha = 0.5f),
                                    modifier = Modifier.size(if (adaptiveLayout.isTablet) 20.dp else 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Crew Tab – Vollständige Implementierung
// ══════════════════════════════════════════════════════════════

@Composable
private fun CrewTabContent(uiState: CrewspaceUiState, viewModel: CrewspaceViewModel) {
    val scrollState = rememberScrollState()
    val adaptiveLayout = currentAdaptiveLayout()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .imePadding() // Sicherstellen, dass die Tastatur die Felder nicht verdeckt
                .verticalScroll(scrollState)
                .padding(
                    horizontal = if (adaptiveLayout.isTablet) adaptiveLayout.horizontalScreenPadding else 16.dp,
                    vertical = if (adaptiveLayout.isTablet) 12.dp else 8.dp,
                ),
        verticalArrangement =
            Arrangement.spacedBy(if (adaptiveLayout.isTablet) TabletLayoutTokens.SectionSpacing else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Card 1: "X an Bord" Übersicht ──
        CrewOnBoardCard(uiState = uiState, viewModel = viewModel)

        // ── Card 2: "Crewmitglied hinzufügen" Formular ──
        CrewAddMemberCard(uiState = uiState, viewModel = viewModel)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Card: "X an Bord" ──────────────────────────────────────────

@Composable
private fun CrewOnBoardCard(uiState: CrewspaceUiState, viewModel: CrewspaceViewModel) {
    val adaptiveLayout = currentAdaptiveLayout()
    Surface(
        modifier =
            if (adaptiveLayout.isTablet) {
                Modifier.widthIn(max = adaptiveLayout.compactContentMaxWidth).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            },
        color = CrewspaceSurface,
        shape = RoundedCornerShape(if (adaptiveLayout.isTablet) TabletLayoutTokens.CardCornerRadius else 24.dp),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(if (adaptiveLayout.isTablet) TabletLayoutTokens.CardPadding else 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(if (adaptiveLayout.isTablet) 48.dp else 40.dp)
                            .clip(CircleShape)
                            .background(CrewspaceAccent.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${uiState.onBoardCount}",
                            color = CrewspaceAccent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (adaptiveLayout.isTablet) 21.sp else 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Aktuell an Bord",
                        fontSize = if (adaptiveLayout.isTablet) 21.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrewspaceTextPrimary
                    )
                }
            }

            if (uiState.crewMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CrewspaceDivider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                uiState.crewMembers.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.startEditingCrew(member) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (adaptiveLayout.isTablet) 54.dp else 44.dp)
                                .clip(CircleShape)
                                .background(CrewspaceAccent.copy(alpha = 0.05f))
                                .border(1.dp, CrewspaceAccent.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = member.name.take(1).uppercase(),
                                color = CrewspaceAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (adaptiveLayout.isTablet) 19.sp else 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = member.name,
                                fontSize = if (adaptiveLayout.isTablet) 19.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrewspaceTextPrimary
                            )
                            Text(
                                text = member.crewRole.label,
                                fontSize = if (adaptiveLayout.isTablet) 15.sp else 13.sp,
                                color = CrewspaceTextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (adaptiveLayout.isTablet) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(48.dp)
                                        .clickable {
                                            viewModel.updateCrew(member.copy(isOnBoard = !member.isOnBoard))
                                        },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (member.isOnBoard) Color(0xFF10B981) else Color(0xFFEF4444),
                                            ),
                                )
                            }
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (member.isOnBoard) Color(0xFF10B981) else Color(0xFFEF4444),
                                        ).clickable {
                                            viewModel.updateCrew(member.copy(isOnBoard = !member.isOnBoard))
                                        },
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        IconButton(
                            onClick = { viewModel.deleteCrew(member) },
                            modifier = Modifier.size(if (adaptiveLayout.isTablet) 48.dp else 28.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Entfernen",
                                tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                                modifier = Modifier.size(if (adaptiveLayout.isTablet) 20.dp else 14.dp)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Noch keine Crewmitglieder hinzugefügt.",
                    fontSize = if (adaptiveLayout.isTablet) 17.sp else 14.sp,
                    color = CrewspaceTextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

// ── Card: "Crewmitglied hinzufügen" ────────────────────────────

@Composable
private fun CrewAddMemberCard(uiState: CrewspaceUiState, viewModel: CrewspaceViewModel) {
    val adaptiveLayout = currentAdaptiveLayout()
    Surface(
        modifier =
            if (adaptiveLayout.isTablet) {
                Modifier.widthIn(max = adaptiveLayout.overlayMaxWidth).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            },
        color = CrewspaceSurface,
        shape = RoundedCornerShape(if (adaptiveLayout.isTablet) TabletLayoutTokens.CardCornerRadius else 24.dp),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(if (adaptiveLayout.isTablet) TabletLayoutTokens.CardPadding else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (adaptiveLayout.isTablet) 20.dp else 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(if (adaptiveLayout.isTablet) 44.dp else 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CrewspaceAccent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        tint = CrewspaceAccent,
                        modifier = Modifier.size(if (adaptiveLayout.isTablet) 25.dp else 20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Mitglied hinzufügen",
                    fontSize = if (adaptiveLayout.isTablet) 22.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrewspaceTextPrimary
                )
            }

            HorizontalDivider(color = CrewspaceDivider, thickness = 1.dp)

            CrewspaceTextField(
                value = uiState.addName,
                onValueChange = { input ->
                    viewModel.updateAddName(ValidationUtils.sanitizeName(input))
                },
                placeholder = "Name des Crewmitglieds",
                leadingIcon = {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(if (adaptiveLayout.isTablet) 24.dp else 20.dp),
                    )
                }
            )

            Column {
                Text(
                    text = "ROLLE AN BORD",
                    fontSize = if (adaptiveLayout.isTablet) 13.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CrewspaceTextSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                val mainRoles = listOf(CrewRole.SKIPPER, CrewRole.CO_SKIPPER, CrewRole.NAVIGATION)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mainRoles.forEach { role ->
                        val isSelected = uiState.addSelectedRole == role
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.updateAddSelectedRole(role) },
                            color = if (isSelected) CrewspaceAccent else CrewspaceCardBg,
                            shape = RoundedCornerShape(12.dp),
                            border = if (!isSelected) BorderStroke(1.dp, CrewspaceDivider) else null
                        ) {
                            Box(
                                modifier =
                                    Modifier.padding(vertical = if (adaptiveLayout.isTablet) 14.dp else 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = role.label,
                                    fontSize = if (adaptiveLayout.isTablet) 16.sp else 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else CrewspaceTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            CrewspaceTextField(
                value = uiState.addEmergencyContact,
                onValueChange = { input ->
                    viewModel.updateAddEmergencyContact(ValidationUtils.sanitizeName(input))
                },
                placeholder = "Notfallkontakt (Name)",
                leadingIcon = {
                    Icon(
                        Icons.Default.ContactPhone,
                        contentDescription = null,
                        modifier = Modifier.size(if (adaptiveLayout.isTablet) 24.dp else 20.dp),
                    )
                }
            )

            CrewspaceTextField(
                value = uiState.addPhone,
                onValueChange = { input ->
                    viewModel.updateAddPhone(ValidationUtils.sanitizePhone(input))
                },
                placeholder = "Notfall-Telefonnummer",
                keyboardType = KeyboardType.Phone,
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(if (adaptiveLayout.isTablet) 24.dp else 20.dp),
                    )
                }
            )

            CrewspaceTextField(
                value = uiState.addMedicalNotes,
                onValueChange = { input ->
                    viewModel.updateAddMedicalNotes(ValidationUtils.sanitizeMedicalNotes(input))
                },
                placeholder = "Medizinische Hinweise / Allergien",
                leadingIcon = {
                    Icon(
                        Icons.Outlined.MedicalServices,
                        contentDescription = null,
                        modifier = Modifier.size(if (adaptiveLayout.isTablet) 24.dp else 20.dp),
                    )
                }
            )

            Button(
                onClick = { viewModel.addCrewMember() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (adaptiveLayout.isTablet) TabletLayoutTokens.PrimaryControlHeight else 54.dp),
                enabled = uiState.addName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrewspaceAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Crewmitglied hinzufügen",
                    fontSize = if (adaptiveLayout.isTablet) 19.sp else 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Wiederverwendbares TextField im Crewspace-Stil ─────────────

@Composable
private fun CrewspaceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    val isError = value.any { !it.isLetterOrDigit() && it != ' ' && it != '+' } && placeholder.contains("Name")
    val adaptiveLayout = currentAdaptiveLayout()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = CrewspaceTextSecondary.copy(alpha = 0.5f),
                fontSize = if (adaptiveLayout.isTablet) 18.sp else 15.sp,
                fontWeight = FontWeight.Medium
            )
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = if (isError) { { Text("Nur Buchstaben und Zahlen erlaubt") } } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (adaptiveLayout.isTablet) Modifier.heightIn(min = 64.dp) else Modifier),
        shape = RoundedCornerShape(if (adaptiveLayout.isTablet) 20.dp else 16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CrewspaceAccent,
            unfocusedBorderColor = CrewspaceDivider,
            focusedContainerColor = CrewspaceCardBg,
            unfocusedContainerColor = CrewspaceCardBg,
            cursorColor = CrewspaceAccent,
            focusedTextColor = CrewspaceTextPrimary,
            unfocusedTextColor = CrewspaceTextPrimary,
            focusedLeadingIconColor = CrewspaceAccent,
            unfocusedLeadingIconColor = CrewspaceTextSecondary
        ),
        textStyle =
            androidx.compose.ui.text.TextStyle(
                fontSize = if (adaptiveLayout.isTablet) 18.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
            ),
    )
}

// ══════════════════════════════════════════════════════════════
