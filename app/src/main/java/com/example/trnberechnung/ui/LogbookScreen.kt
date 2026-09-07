package com.example.trnberechnung.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.model.LogbookEntry
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.TideViewModel

private val LogbookBlue = Color(0xFF0040DD)
private val LogbookBlueBg = Color(0xFF1A2E55)
private val LogbookChipBorder = Color(0xFF2A4070)
private val LogbookCardBg = Color(0xFF1B2838)
private val LogbookSubCardBg = Color(0xFF162030)
private val LogbookFieldBorder = Color(0xFF2A3A4E)

internal val CHECKLIST_CREW = listOf(
    "Einweisung der Crew",
    "Sicherheitsmittel geprüft",
    "Revierkunde besprochen",
    "Lagemeldung abgegeben",
    "UKW-Funkgerät geprüft",
    "Handy geladen"
)
internal val CHECKLIST_TECH = listOf(
    "Kraftstoff geprüft",
    "Ölstand geprüft",
    "Seeventil kontrolliert",
    "Beleuchtung funktionsfähig",
    "Signalhorn funktionsfähig",
    "Scheibenwischer geprüft"
)
internal val CHECKLIST_NAV = listOf(
    "Ankerfunktion geprüft",
    "Seekarten aktuell",
    "Wegepunkte gesetzt"
)
internal const val CHECKLIST_SIZE = 15

@Composable
fun LogbookScreen(
    viewModel: TideViewModel,
    topOverlayClearance: Dp = 0.dp,
    bottomOverlayClearance: Dp = 0.dp
) {
    val logs by viewModel.allLogs.collectAsState()
    val context = LocalContext.current
    var logToDelete by remember { mutableStateOf<LogbookEntry?>(null) }
    val adaptiveLayout = currentAdaptiveLayout()

    logToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { logToDelete = null },
            containerColor = NauticalSurface,
            title = { Text("Eintrag löschen?", color = NauticalTextPrimary) },
            text = {
                Text(
                    "\"${entry.routeDesc}\" vom ${entry.date} wirklich löschen?",
                    color = NauticalTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLog(entry)
                    logToDelete = null
                    Toast.makeText(context, "Eintrag gelöscht", Toast.LENGTH_SHORT).show()
                }) { Text("Löschen", color = NauticalNoGo) }
            },
            dismissButton = {
                TextButton(onClick = { logToDelete = null }) {
                    Text("Abbrechen", color = NauticalTextSecondary)
                }
            }
        )
    }

    val isLandscape = adaptiveLayout.isLandscape
    val compactLandscape = isLandscape && !adaptiveLayout.isTablet

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("screen_logbook")
                .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(topOverlayClearance + (if (compactLandscape) 2.dp else 4.dp)))
        Text(
            "LOGBUCH",
            modifier =
                (if (adaptiveLayout.isTablet) {
                    Modifier.widthIn(max = adaptiveLayout.mainContentMaxWidth).fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth()
                }).padding(
                    start = if (adaptiveLayout.isTablet) adaptiveLayout.horizontalScreenPadding else 16.dp,
                    end = if (adaptiveLayout.isTablet) adaptiveLayout.horizontalScreenPadding else 0.dp,
                    top = if (compactLandscape) 2.dp else 8.dp,
                    bottom = if (compactLandscape) 2.dp else 4.dp,
                ).testTag("screen_header_logbook")
                    .semantics { heading() },
            style = MaterialTheme.typography.labelMedium,
            fontSize = if (adaptiveLayout.isTablet) 15.sp else MaterialTheme.typography.labelMedium.fontSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f), // Slightly higher contrast
            letterSpacing = 1.sp
        )

        val createBlankPdf = {
            LogbookPdfGenerator.generate(
                context = context,
                log = LogbookEntry(
                    date = java.time.LocalDate.now().toString(),
                    routeDesc = "",
                    distance = "",
                    duration = "",
                    status = "",
                    details = ""
                )
            )
        }

        LogbookActionBar(logCount = logs.size, onCreateBlankPdf = createBlankPdf)

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // The clearance below the card, not inside it: as a Spacer in the card's
                    // Column it stretched the empty state by the full bottom-overlay height and
                    // left a large blank area under the button.
                    .padding(
                        start = if (adaptiveLayout.isTablet) adaptiveLayout.horizontalScreenPadding else 16.dp,
                        end = if (adaptiveLayout.isTablet) adaptiveLayout.horizontalScreenPadding else 16.dp,
                        top = if (adaptiveLayout.isTablet) 20.dp else 16.dp,
                        bottom = maxOf(16.dp, bottomOverlayClearance),
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    modifier =
                        if (adaptiveLayout.isTablet) {
                            Modifier.widthIn(max = adaptiveLayout.compactContentMaxWidth).fillMaxWidth()
                        } else {
                            Modifier.fillMaxWidth()
                        },
                    shape =
                        RoundedCornerShape(
                            if (adaptiveLayout.isTablet) TabletLayoutTokens.CardCornerRadius else 28.dp,
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (adaptiveLayout.isTablet) 32.dp else 24.dp,
                                vertical = if (adaptiveLayout.isTablet) 32.dp else 24.dp,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(if (adaptiveLayout.isTablet) 68.dp else 54.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Dein Logbuch ist bereit",
                            style = MaterialTheme.typography.headlineSmall,
                            fontSize =
                                if (adaptiveLayout.isTablet) 29.sp else MaterialTheme.typography.headlineSmall.fontSize,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Speichere eine Planung, zeichne eine Fahrt auf oder erstelle eine leere PDF-Vorlage.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize =
                                if (adaptiveLayout.isTablet) 19.sp else MaterialTheme.typography.bodyLarge.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = createBlankPdf,
                            modifier =
                                (if (adaptiveLayout.isTablet) {
                                    Modifier.widthIn(max = 560.dp).fillMaxWidth()
                                } else {
                                    Modifier.fillMaxWidth()
                                }).height(
                                    if (adaptiveLayout.isTablet) {
                                        TabletLayoutTokens.PrimaryControlHeight
                                    } else {
                                        56.dp
                                    },
                                ),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Leere PDF erstellen",
                                fontWeight = FontWeight.Bold,
                                fontSize = if (adaptiveLayout.isTablet) 21.sp else 18.sp,
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .weight(1f)
                        .then(
                            if (adaptiveLayout.isTablet) {
                                Modifier.widthIn(max = adaptiveLayout.compactContentMaxWidth).fillMaxWidth()
                            } else {
                                Modifier.fillMaxWidth()
                            },
                        ).padding(horizontal = if (adaptiveLayout.isTablet) 24.dp else 12.dp),
                verticalArrangement =
                    Arrangement.spacedBy(if (adaptiveLayout.isTablet) TabletLayoutTokens.SectionSpacing else 12.dp),
                contentPadding = PaddingValues(
                    bottom = maxOf(16.dp, bottomOverlayClearance)
                )
            ) {
                items(logs, key = { it.id }) { log ->
                    LogbookOverviewCard(
                        log = log,
                        onDelete = { logToDelete = log },
                        onUpdate = { viewModel.updateLog(it) },
                        onCreatePdf = { LogbookPdfGenerator.generate(context, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LogbookActionBar(
    logCount: Int,
    onCreateBlankPdf: () -> Unit
) {
    val adaptiveLayout = currentAdaptiveLayout()
    val compactLandscape = adaptiveLayout.isLandscape && !adaptiveLayout.isTablet
    // A flat surface with a hairline outline instead of an elevated, semi-transparent one. The
    // drop shadow of the previous version read as a grey box sitting behind a white card, because
    // the card's own fill was almost the same colour as the screen behind it.
    Card(
        modifier =
            (if (adaptiveLayout.isTablet) {
                Modifier.widthIn(max = adaptiveLayout.compactContentMaxWidth).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            }).padding(
                horizontal = if (adaptiveLayout.isTablet) adaptiveLayout.horizontalScreenPadding else 16.dp,
                vertical = if (compactLandscape) 4.dp else 12.dp,
            ),
        shape = RoundedCornerShape(if (adaptiveLayout.isTablet) 26.dp else 22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = if (adaptiveLayout.isTablet) 22.dp else 16.dp,
                    end = if (adaptiveLayout.isTablet) 18.dp else 12.dp,
                    top = if (adaptiveLayout.isTablet) 16.dp else if (compactLandscape) 6.dp else 14.dp,
                    bottom = if (adaptiveLayout.isTablet) 16.dp else if (compactLandscape) 6.dp else 14.dp,
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (logCount == 1) "1 Törn" else "$logCount Törns",
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize =
                        if (adaptiveLayout.isTablet) 28.sp else MaterialTheme.typography.headlineSmall.fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    // Short enough to share the row with the button on a phone: the longer
                    // wording wrapped to two lines and collided with it.
                    "Planungen und Fahrten",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize =
                        if (adaptiveLayout.isTablet) 15.sp else MaterialTheme.typography.bodySmall.fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(
                onClick = onCreateBlankPdf,
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                // heightIn, not height: the label used to be a hard-wrapped two-liner inside a
                // fixed 48 dp button, so its second line was simply cut off. The minimum keeps the
                // touch target accessible without capping the content.
                modifier = Modifier.heightIn(min = if (adaptiveLayout.isTablet) 56.dp else 48.dp)
            ) {
                Icon(
                    Icons.Default.AddCircle,
                    contentDescription = "PDF erstellen",
                    modifier = Modifier.size(if (adaptiveLayout.isTablet) 24.dp else 20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Leere Vorlage",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (adaptiveLayout.isTablet) 16.sp else 13.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LogbookOverviewCard(
    log: LogbookEntry,
    onDelete: () -> Unit,
    onUpdate: (LogbookEntry) -> Unit,
    onCreatePdf: (LogbookEntry) -> Unit
) {
    val adaptiveLayout = currentAdaptiveLayout()
    val parsed = remember(log.id, log.details) { LogbookDetails.parse(log.details) }
    var data by remember(log.id) { mutableStateOf(parsed) }
    var detailsExpanded by remember { mutableStateOf(false) }
    var checklistExpanded by remember { mutableStateOf(false) }

    fun persist(newData: LogbookDetails) {
        data = newData
        onUpdate(log.copy(details = newData.encode()))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LogbookCardBg),
        shape = RoundedCornerShape(if (adaptiveLayout.isTablet) 22.dp else 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(if (adaptiveLayout.isTablet) 22.dp else 16.dp)) {

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(if (adaptiveLayout.isTablet) 54.dp else 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LogbookBlueBg),
                    contentAlignment = Alignment.Center
                ) { Text("📘", fontSize = 22.sp) }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val formattedDate = try {
                        java.time.LocalDate.parse(log.date)
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    } catch (_: Exception) { log.date }
                    val depTime = data.abfahrt.takeLast(5).takeIf { ":" in it } ?: ""
                    val title = if (depTime.isNotBlank()) "Törn · $formattedDate · $depTime"
                                else "Törn · $formattedDate"
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize =
                            if (adaptiveLayout.isTablet) 20.sp else MaterialTheme.typography.titleMedium.fontSize,
                        fontWeight = FontWeight.Bold,
                        color = NauticalTextPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        log.routeDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize =
                            if (adaptiveLayout.isTablet) 17.sp else MaterialTheme.typography.bodyMedium.fontSize,
                        color = NauticalTextSecondary,
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(if (adaptiveLayout.isTablet) 48.dp else 32.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Löschen",
                         tint = NauticalTextSecondary,
                         modifier = Modifier.size(if (adaptiveLayout.isTablet) 22.dp else 18.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryChip(icon = "⛵", text = log.distance)
                SummaryChip(icon = "⏱", text = log.duration)
                SummaryChip(icon = "✓", text = formatStatus(log.status),
                            isStatus = true,
                            isGo = log.status.contains("GO") && !log.status.contains("NO-GO"))
            }

            Spacer(modifier = Modifier.height(12.dp))

            ExpanderRow(
                emoji = "📋",
                title = "Logbuchdaten anzeigen",
                expanded = detailsExpanded,
                onClick = { detailsExpanded = !detailsExpanded },
                contentDescription = if (detailsExpanded) "Details ausblenden" else "Details für ${log.routeDesc} anzeigen"
            )
            AnimatedVisibility(detailsExpanded,
                enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                       modifier = Modifier.padding(top = 4.dp)) {
                    DetailField("ABFAHRT", data.abfahrt.ifBlank { log.date })
                    DetailField("ANKUNFT", data.ankunft.ifBlank { "–" })
                    DetailField("DISTANZ", log.distance)
                    DetailField("WT", data.wt.ifBlank { "–" })
                    DetailField("Erf. Tiefe", data.erfTiefe.ifBlank { "–" })
                    DetailField("WuK (Netto)", data.ukc.ifBlank { "–" })
                    DetailField("FMW", data.fmw.ifBlank { "–" })
                    DetailField("WETTER", data.wetter.ifBlank { "–" })
                    DetailField("GEZEITEN", data.gezeiten.ifBlank { "–" })
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            ExpanderRow(
                emoji = "✅",
                title = "Checkliste vor Abfahrt",
                expanded = checklistExpanded,
                onClick = { checklistExpanded = !checklistExpanded },
                contentDescription = if (checklistExpanded) "Checkliste ausblenden" else "Checkliste für Abfahrt anzeigen"
            )
            AnimatedVisibility(checklistExpanded,
                enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp),
                       modifier = Modifier.padding(top = 4.dp)) {

                    ChecklistSection(
                        emoji = "👥",
                        title = "CREW & SICHERHEIT",
                        items = CHECKLIST_CREW,
                        states = data.checklist.slice(0 until 6),
                        onToggle = { idx, value ->
                            persist(data.copy(checklist = data.checklist.toMutableList().also { it[idx] = value }))
                        }
                    )

                    ChecklistSection(
                        emoji = "🔧",
                        title = "TECHNIK",
                        items = CHECKLIST_TECH,
                        states = data.checklist.slice(6 until 12),
                        onToggle = { idx, value ->
                            persist(data.copy(checklist = data.checklist.toMutableList().also { it[idx + 6] = value }))
                        }
                    )

                    ChecklistSection(
                        emoji = "🧭",
                        title = "NAVIGATION",
                        items = CHECKLIST_NAV,
                        states = data.checklist.slice(12 until 15),
                        onToggle = { idx, value ->
                            persist(data.copy(checklist = data.checklist.toMutableList().also { it[idx + 12] = value }))
                        }
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = LogbookSubCardBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = data.aufbauhoeheActive,
                                    onCheckedChange = { persist(data.copy(aufbauhoeheActive = it)) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NauticalPrimary,
                                        uncheckedColor = NauticalTextSecondary
                                    )
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Aufbauhöhe angeben", color = NauticalTextPrimary,
                                     fontWeight = FontWeight.Medium)
                            }
                            if (data.aufbauhoeheActive) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = data.aufbauhoehe,
                                    onValueChange = { persist(data.copy(aufbauhoehe = it)) },
                                    label = { Text("Aufbauhöhe (m)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = textFieldColors()
                                )
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = LogbookSubCardBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp),
                               verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📋", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Text("SONSTIGES",
                                     color = NauticalTextSecondary, letterSpacing = 1.sp,
                                     fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            OutlinedTextField(
                                value = data.bsAbfahrt,
                                onValueChange = { persist(data.copy(bsAbfahrt = it)) },
                                label = { Text("Betriebsstunden bei Abfahrt") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors()
                            )
                            OutlinedTextField(
                                value = data.bsAnkunft,
                                onValueChange = { persist(data.copy(bsAnkunft = it)) },
                                label = { Text("Betriebsstunden bei Ankunft") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors()
                            )
                            OutlinedTextField(
                                value = data.bemerkungen,
                                onValueChange = { persist(data.copy(bemerkungen = it)) },
                                label = { Text("Bemerkungen / Ereignisse") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onCreatePdf(log.copy(details = data.encode())) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(if (adaptiveLayout.isTablet) 60.dp else 48.dp),
                shape = RoundedCornerShape(if (adaptiveLayout.isTablet) 18.dp else 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LogbookBlue, contentColor = Color.White
                )
            ) {
                Text("📄", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "PDF erstellen",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (adaptiveLayout.isTablet) 18.sp else 15.sp,
                )
            }
        }
    }
}

@Composable
private fun ExpanderRow(
    emoji: String,
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    contentDescription: String? = null
) {
    val adaptiveLayout = currentAdaptiveLayout()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (adaptiveLayout.isTablet) 56.dp else 48.dp) // Accessibility
            .clip(RoundedCornerShape(if (adaptiveLayout.isTablet) 12.dp else 8.dp))
            .clickable(
                onClickLabel = contentDescription,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            emoji,
            fontSize = if (adaptiveLayout.isTablet) 20.sp else 16.sp,
            modifier = Modifier.semantics { this.contentDescription = "" },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = if (adaptiveLayout.isTablet) 17.sp else MaterialTheme.typography.bodyMedium.fontSize,
            fontWeight = FontWeight.Medium,
            color = NauticalPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null, tint = NauticalTextSecondary
        )
    }
}

@Composable
private fun ChecklistSection(
    emoji: String,
    title: String,
    items: List<String>,
    states: List<Boolean>,
    onToggle: (Int, Boolean) -> Unit
) {
    val adaptiveLayout = currentAdaptiveLayout()
    val checkedCount = states.count { it }
    Card(
        colors = CardDefaults.cardColors(containerColor = LogbookSubCardBg),
        shape = RoundedCornerShape(if (adaptiveLayout.isTablet) 16.dp else 12.dp)
    ) {
        Column(modifier = Modifier.padding(if (adaptiveLayout.isTablet) 16.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(title, color = NauticalTextSecondary, letterSpacing = 1.sp,
                     fontWeight = FontWeight.Bold,
                     fontSize = if (adaptiveLayout.isTablet) 16.sp else 13.sp,
                     modifier = Modifier.weight(1f))
                Text("$checkedCount / ${items.size}",
                     color = NauticalTextSecondary,
                     fontSize = if (adaptiveLayout.isTablet) 14.sp else 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { if (items.isEmpty()) 0f else checkedCount / items.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = NauticalPrimary,
                trackColor = LogbookFieldBorder
            )
            Spacer(Modifier.height(4.dp))
            items.forEachIndexed { i, label ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = if (adaptiveLayout.isTablet) 56.dp else 48.dp) // Touch Target
                        .clickable(
                            onClickLabel = "Markiere $label als erledigt",
                            onClick = { onToggle(i, !states[i]) }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = states[i],
                        onCheckedChange = { onToggle(i, it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = NauticalPrimary,
                            uncheckedColor = NauticalTextSecondary
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        label,
                        color = NauticalTextPrimary,
                        fontSize = if (adaptiveLayout.isTablet) 17.sp else LocalTextStyle.current.fontSize,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(
    icon: String, text: String,
    isStatus: Boolean = false, isGo: Boolean = false
) {
    val adaptiveLayout = currentAdaptiveLayout()
    val borderColor = when {
        isStatus && isGo -> NauticalGo.copy(alpha = 0.5f)
        isStatus -> NauticalNoGo.copy(alpha = 0.5f)
        else -> LogbookChipBorder
    }
    val textColor = when {
        isStatus && isGo -> NauticalGo
        isStatus -> NauticalNoGo
        else -> NauticalPrimary
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = ButtonDefaults.outlinedButtonBorder(true).copy(
            brush = androidx.compose.ui.graphics.SolidColor(borderColor)
        )
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = if (adaptiveLayout.isTablet) 14.dp else 10.dp,
                    vertical = if (adaptiveLayout.isTablet) 8.dp else 5.dp,
                ),
            verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = if (adaptiveLayout.isTablet) 16.sp else 13.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontSize = if (adaptiveLayout.isTablet) 15.sp else MaterialTheme.typography.labelMedium.fontSize,
                color = textColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    val adaptiveLayout = currentAdaptiveLayout()
    Column(
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, LogbookFieldBorder, RoundedCornerShape(12.dp))
            .background(LogbookSubCardBg, RoundedCornerShape(12.dp))
            .padding(
                horizontal = if (adaptiveLayout.isTablet) 18.dp else 14.dp,
                vertical = if (adaptiveLayout.isTablet) 14.dp else 10.dp,
            )
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
             color = NauticalTextSecondary,
             letterSpacing = 1.sp,
             fontSize = if (adaptiveLayout.isTablet) 13.sp else 11.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = if (adaptiveLayout.isTablet) 18.sp else MaterialTheme.typography.bodyLarge.fontSize,
            color = NauticalTextPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = NauticalTextPrimary,
    unfocusedTextColor = NauticalTextPrimary,
    focusedBorderColor = NauticalPrimary,
    unfocusedBorderColor = LogbookFieldBorder,
    focusedLabelColor = NauticalPrimary,
    unfocusedLabelColor = NauticalTextSecondary,
    cursorColor = NauticalPrimary
)

private fun formatStatus(status: String): String = when {
    status.contains("GO ✓") || status == "GO" -> "Befahrbar"
    status.contains("NO-GO") -> "Nicht befahrbar"
    else -> status
}

internal data class LogbookDetails(
    val abfahrt: String = "",
    val ankunft: String = "",
    val wt: String = "",
    val erfTiefe: String = "",
    val ukc: String = "",
    val fmw: String = "",
    val wetter: String = "",
    val gezeiten: String = "",
    val crew: String = "",
    val checklist: List<Boolean> = List(CHECKLIST_SIZE) { false },
    val aufbauhoeheActive: Boolean = false,
    val aufbauhoehe: String = "",
    val bsAbfahrt: String = "",
    val bsAnkunft: String = "",
    val bemerkungen: String = ""
) {
    fun encode(): String {
        val parts = mutableListOf<String>()
        if (abfahrt.isNotBlank()) parts += "abfahrt:${abfahrt.sanitize()}"
        if (ankunft.isNotBlank()) parts += "ankunft:${ankunft.sanitize()}"
        if (wt.isNotBlank()) parts += "wt:${wt.sanitize()}"
        if (erfTiefe.isNotBlank()) parts += "erft:${erfTiefe.sanitize()}"
        if (ukc.isNotBlank()) parts += "ukc:${ukc.sanitize()}"
        if (fmw.isNotBlank()) parts += "fmw:${fmw.sanitize()}"
        if (wetter.isNotBlank()) parts += "wetter:${wetter.sanitize()}"
        if (gezeiten.isNotBlank()) parts += "gezeiten:${gezeiten.sanitize()}"
        if (crew.isNotBlank()) parts += "crew:${crew.sanitize()}"
        parts += "check:" + checklist.joinToString("") { if (it) "1" else "0" }
        parts += "aufh_on:" + (if (aufbauhoeheActive) "1" else "0")
        if (aufbauhoehe.isNotBlank()) parts += "aufh:${aufbauhoehe.sanitize()}"
        if (bsAbfahrt.isNotBlank()) parts += "bsa:${bsAbfahrt.sanitize()}"
        if (bsAnkunft.isNotBlank()) parts += "bsb:${bsAnkunft.sanitize()}"
        if (bemerkungen.isNotBlank()) parts += "bem:${bemerkungen.sanitize()}"
        return parts.joinToString("|")
    }

    private fun String.sanitize() = replace("|", "/").replace("\n", " ").trim()

    companion object {
        fun parse(details: String): LogbookDetails {
            if (details.isBlank()) return LogbookDetails()
            val map = mutableMapOf<String, String>()

            val segments = if ("|" in details) details.split("|")
                           else details.split("\n", ";")
            for (seg in segments) {
                val idx = seg.indexOf(':')
                if (idx > 0) {
                    val k = seg.substring(0, idx).trim().lowercase()
                    val v = seg.substring(idx + 1).trim()
                    if (v.isNotEmpty()) map[k] = v
                }
            }
            val rawCheck = map["check"] ?: ""
            val checklist = List(CHECKLIST_SIZE) { i ->
                rawCheck.getOrNull(i) == '1'
            }
            return LogbookDetails(
                abfahrt = map["abfahrt"] ?: "",
                ankunft = map["ankunft"] ?: "",
                wt = map["wt"] ?: "",
                erfTiefe = map["erft"] ?: "",
                ukc = map["ukc"] ?: "",
                fmw = map["fmw"] ?: "",
                wetter = map["wetter"] ?: map.entries.firstOrNull {
                    it.key !in setOf("abfahrt","ankunft","wt","erft","ukc","fmw","wetter","gezeiten","crew",
                                     "check","aufh_on","aufh","bsa","bsb","bem")
                }?.value ?: "",
                gezeiten = map["gezeiten"] ?: "",
                crew = map["crew"] ?: "",
                checklist = checklist,
                aufbauhoeheActive = map["aufh_on"] == "1",
                aufbauhoehe = map["aufh"] ?: "",
                bsAbfahrt = map["bsa"] ?: "",
                bsAnkunft = map["bsb"] ?: "",
                bemerkungen = map["bem"] ?: ""
            )
        }
    }
}
