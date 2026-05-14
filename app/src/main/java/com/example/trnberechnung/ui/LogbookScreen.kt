package com.example.trnberechnung.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.model.LogbookEntry
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.TideViewModel

// ── Logbook-spezifische Farben ───────────────────────────────────
private val LogbookBlue = Color(0xFF0040DD)
private val LogbookBlueBg = Color(0xFF1A2E55)
private val LogbookChipBorder = Color(0xFF2A4070)
private val LogbookCardBg = Color(0xFF1B2838)
private val LogbookSubCardBg = Color(0xFF162030)
private val LogbookFieldBorder = Color(0xFF2A3A4E)

// ── Checklisten-Definition (Reihenfolge ist persistent!) ─────────
// 6 Crew & Sicherheit + 6 Technik + 3 Navigation = 15 Items
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
internal const val CHECKLIST_SIZE = 15  // 6 + 6 + 3

@Composable
fun LogbookScreen(viewModel: TideViewModel) {
    val logs by viewModel.allLogs.collectAsState()
    val context = LocalContext.current
    var logToDelete by remember { mutableStateOf<LogbookEntry?>(null) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NauticalBackground)
    ) {
        Text(
            "LOGBUCH",
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = NauticalTextSecondary,
            letterSpacing = 1.sp
        )

        // Debug-Log-Card (siehe RouterLog) — eingeklappt, immer sichtbar
        DebugLogCard(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", style = MaterialTheme.typography.displayLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Noch keine Törns gespeichert",
                        style = MaterialTheme.typography.titleMedium,
                        color = NauticalTextSecondary
                    )
                    Text(
                        "Berechne einen Törn im Karte-Tab",
                        style = MaterialTheme.typography.bodySmall,
                        color = NauticalTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
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

// ═════════════════════════════════════════════════════════════════
// Overview-Karte für einen Logbuch-Eintrag
// ═════════════════════════════════════════════════════════════════
@Composable
private fun LogbookOverviewCard(
    log: LogbookEntry,
    onDelete: () -> Unit,
    onUpdate: (LogbookEntry) -> Unit,
    onCreatePdf: (LogbookEntry) -> Unit
) {
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
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header: Icon + Titel + Delete ──
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
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
                    Text(title, style = MaterialTheme.typography.titleMedium,
                         fontWeight = FontWeight.Bold, color = NauticalTextPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(log.routeDesc, style = MaterialTheme.typography.bodyMedium,
                         color = NauticalTextSecondary)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Löschen",
                         tint = NauticalTextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Summary-Chips ──
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryChip(icon = "⛵", text = log.distance)
                SummaryChip(icon = "⏱", text = log.duration)
                SummaryChip(icon = "✓", text = formatStatus(log.status),
                            isStatus = true,
                            isGo = log.status.contains("GO") && !log.status.contains("NO-GO"))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Logbuchdaten anzeigen (Sektion 1) ──
            ExpanderRow(
                emoji = "📋",
                title = "Logbuchdaten anzeigen",
                expanded = detailsExpanded,
                onClick = { detailsExpanded = !detailsExpanded }
            )
            AnimatedVisibility(detailsExpanded,
                enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                       modifier = Modifier.padding(top = 4.dp)) {
                    DetailField("ABFAHRT", data.abfahrt.ifBlank { log.date })
                    DetailField("ANKUNFT", data.ankunft.ifBlank { "–" })
                    DetailField("DISTANZ", log.distance)
                    DetailField("WT", data.wt.ifBlank { "–" })
                    DetailField("UKC", data.ukc.ifBlank { "–" })
                    DetailField("FMW", data.fmw.ifBlank { "–" })
                    DetailField("WETTER", data.wetter.ifBlank { "–" })
                    DetailField("GEZEITEN", data.gezeiten.ifBlank { "–" })
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Checkliste vor Abfahrt (Sektion 2) ──
            ExpanderRow(
                emoji = "✅",
                title = "Checkliste vor Abfahrt",
                expanded = checklistExpanded,
                onClick = { checklistExpanded = !checklistExpanded }
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

                    // ── Aufbauhöhe ──
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

                    // ── Sonstiges ──
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

            // ── PDF erstellen ──
            Button(
                onClick = { onCreatePdf(log.copy(details = data.encode())) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LogbookBlue, contentColor = Color.White
                )
            ) {
                Text("📄", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("PDF erstellen", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════
// Wiederverwendbare UI-Bausteine
// ═════════════════════════════════════════════════════════════════
@Composable
private fun ExpanderRow(emoji: String, title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium,
             fontWeight = FontWeight.Medium, color = NauticalPrimary,
             modifier = Modifier.weight(1f))
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
    val checkedCount = states.count { it }
    Card(
        colors = CardDefaults.cardColors(containerColor = LogbookSubCardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(title, color = NauticalTextSecondary, letterSpacing = 1.sp,
                     fontWeight = FontWeight.Bold, fontSize = 13.sp,
                     modifier = Modifier.weight(1f))
                Text("$checkedCount / ${items.size}",
                     color = NauticalTextSecondary, fontSize = 12.sp)
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
                        .clickable { onToggle(i, !states[i]) }
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
                    Text(label, color = NauticalTextPrimary, fontWeight = FontWeight.Medium)
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
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium,
                 color = textColor, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, LogbookFieldBorder, RoundedCornerShape(12.dp))
            .background(LogbookSubCardBg, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
             color = NauticalTextSecondary, letterSpacing = 1.sp, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge,
             color = NauticalTextPrimary, fontWeight = FontWeight.Medium)
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

// ═════════════════════════════════════════════════════════════════
// Debug-Log-Card — zeigt die letzten RouterLog-Zeilen im Logbuch-Tab
// ═════════════════════════════════════════════════════════════════
@Composable
private fun DebugLogCard(modifier: Modifier = Modifier) {
    val logs by com.example.trnberechnung.logic.RouterLog.logs.collectAsState()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LogbookCardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        // Header: Toggle + line count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🐛", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "DEBUG-LOG",
                color = NauticalTextSecondary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${logs.size}",
                color = NauticalTextSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = NauticalTextSecondary
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                if (logs.isEmpty()) {
                    Text(
                        "Noch keine Log-Einträge.\nRoute berechnen, dann erscheint hier was.",
                        color = NauticalTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    // Scrollbarer Log-Container (max 240 dp, neueste unten)
                    val scrollState = rememberScrollState()
                    LaunchedEffect(logs.size) {
                        // Auto-scroll ans Ende, wenn neue Zeile reinkommt
                        scrollState.scrollTo(scrollState.maxValue)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(LogbookSubCardBg, RoundedCornerShape(8.dp))
                            .border(1.dp, LogbookFieldBorder, RoundedCornerShape(8.dp))
                            .verticalScroll(scrollState)
                            .padding(8.dp)
                    ) {
                        logs.forEach { line ->
                            val color = when {
                                " W/" in line || " E/" in line -> NauticalNoGo
                                " I/" in line -> NauticalPrimary
                                else -> NauticalTextPrimary
                            }
                            Text(
                                line,
                                color = color,
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = 13.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        "Debug-Log",
                                        logs.joinToString("\n")
                                    )
                                )
                                android.widget.Toast.makeText(
                                    context, "Log in Zwischenablage kopiert",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("Kopieren", fontSize = 12.sp) }

                        OutlinedButton(
                            onClick = { com.example.trnberechnung.logic.RouterLog.clear() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = NauticalNoGo
                            )
                        ) { Text("Leeren", fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════
// LogbookDetails — strukturierte Repräsentation des `details`-Feldes
// ═════════════════════════════════════════════════════════════════
internal data class LogbookDetails(
    val abfahrt: String = "",
    val ankunft: String = "",
    val wt: String = "",
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
            // Primär Pipe-Format, Fallback Newline/Semikolon
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
                ukc = map["ukc"] ?: "",
                fmw = map["fmw"] ?: "",
                wetter = map["wetter"] ?: map.entries.firstOrNull {
                    it.key !in setOf("abfahrt","ankunft","wt","ukc","fmw","wetter","gezeiten","crew",
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
