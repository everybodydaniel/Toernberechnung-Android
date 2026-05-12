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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.model.LogbookEntry
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.TideViewModel

// ══════════════════════════════════════════════════════
// iOS-matching colors (blue accent on dark bg)
// ══════════════════════════════════════════════════════
private val LogbookBlue = Color(0xFF0040DD)
private val LogbookBlueBg = Color(0xFF1A2E55)
private val LogbookChipBorder = Color(0xFF2A4070)
private val LogbookCardBg = Color(0xFF1B2838)
private val LogbookFieldBg = Color(0xFF162030)
private val LogbookFieldBorder = Color(0xFF2A3A4E)

@Composable
fun LogbookScreen(viewModel: TideViewModel) {
    val logs by viewModel.allLogs.collectAsState()
    val context = LocalContext.current
    var logToDelete by remember { mutableStateOf<LogbookEntry?>(null) }

    // ── Delete confirmation dialog ──
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
                }) {
                    Text("Löschen", color = NauticalNoGo)
                }
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
        // ── Section label ──
        Text(
            "LOGBUCH",
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = NauticalTextSecondary,
            letterSpacing = 1.sp
        )

        if (logs.isEmpty()) {
            // ── Empty state ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                        onCreatePdf = { LogbookPdfGenerator.generate(context, log) }
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// Main overview card (matches iOS Logbook card)
// ══════════════════════════════════════════════════════
@Composable
private fun LogbookOverviewCard(
    log: LogbookEntry,
    onDelete: () -> Unit,
    onCreatePdf: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val details = parseDetails(log.details)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LogbookCardBg),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header: Icon + Title + Delete ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Logbook icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LogbookBlueBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📘", fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title + route
                Column(modifier = Modifier.weight(1f)) {
                    // Format date DD.MM.YYYY and add departure time
                    val formattedDate = try {
                        val ld = java.time.LocalDate.parse(log.date)
                        ld.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    } catch (_: Exception) { log.date }
                    val depTime = details["abfahrt"]?.takeLast(5) ?: ""
                    val titleStr = if (depTime.isNotBlank()) "Törn · $formattedDate · $depTime" else "Törn · $formattedDate"
                    Text(
                        titleStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NauticalTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        log.routeDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NauticalTextSecondary
                    )
                }

                // Minimal delete icon
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Löschen",
                        tint = NauticalTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Summary chips ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryChip(icon = "⛵", text = log.distance)
                SummaryChip(icon = "⏱", text = log.duration)
                SummaryChip(
                    icon = "✓",
                    text = formatStatus(log.status),
                    isStatus = true,
                    isGo = log.status.contains("GO") && !log.status.contains("NO-GO")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── "Logbuchdaten anzeigen" toggle ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📋", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Logbuchdaten anzeigen",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = NauticalPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = NauticalTextSecondary
                )
            }

            // ── Expandable detail fields ──
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    DetailField("ABFAHRT", details["abfahrt"] ?: log.date)
                    DetailField("ANKUNFT", details["ankunft"] ?: "–")
                    DetailField("DISTANZ", log.distance)
                    DetailField("WT", details["wt"] ?: "–")
                    DetailField("UKC", details["ukc"] ?: "–")
                    DetailField("FMW", details["fmw"] ?: "–")
                    DetailField("WETTER", details["wetter"] ?: "–")
                    DetailField("GEZEITEN", details["gezeiten"] ?: "–")
                    DetailField("CREW", details["crew"] ?: "–")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── "PDF erstellen" button ──
            Button(
                onClick = onCreatePdf,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LogbookBlue,
                    contentColor = Color.White
                )
            ) {
                Text("📄", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "PDF erstellen",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// Summary chip (outlined, with icon)
// ══════════════════════════════════════════════════════
@Composable
private fun SummaryChip(
    icon: String,
    text: String,
    isStatus: Boolean = false,
    isGo: Boolean = false
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
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ══════════════════════════════════════════════════════
// Detail field card (iOS-style rounded field)
// ══════════════════════════════════════════════════════
@Composable
private fun DetailField(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LogbookFieldBorder, RoundedCornerShape(12.dp))
            .background(LogbookFieldBg, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = NauticalTextSecondary,
            letterSpacing = 1.sp,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = NauticalTextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

// ══════════════════════════════════════════════════════
// Parse details string into key-value map
// ══════════════════════════════════════════════════════
private fun parseDetails(details: String): Map<String, String> {
    if (details.isBlank()) return emptyMap()
    val map = mutableMapOf<String, String>()

    // Split by pipe first (new structured format), then by newline/semicolon
    val segments = details.split("|")
    for (segment in segments) {
        // Use first colon only as separator (value may contain colons like times)
        val idx = segment.indexOf(':')
        if (idx > 0) {
            val key = segment.substring(0, idx).trim().lowercase()
            val value = segment.substring(idx + 1).trim()
            if (value.isNotBlank()) {
                when (key) {
                    "abfahrt" -> map["abfahrt"] = value
                    "ankunft" -> map["ankunft"] = value
                    "wt" -> map["wt"] = value
                    "ukc" -> map["ukc"] = value
                    "fmw" -> map["fmw"] = value
                    "wetter" -> map["wetter"] = value
                    "gezeiten" -> map["gezeiten"] = value
                    "crew" -> map["crew"] = value
                }
            }
        }
    }
    // Backward compat: if pipe parsing found nothing, try legacy newline format
    if (map.isEmpty()) {
        for (line in details.split("\n", ";")) {
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim().lowercase()
                val value = line.substring(idx + 1).trim()
                if (value.isNotBlank()) when {
                    key.contains("abfahrt") -> map["abfahrt"] = value
                    key.contains("ankunft") -> map["ankunft"] = value
                    key.contains("wt") || key.contains("wassertiefe") -> map["wt"] = value
                    key.contains("ukc") -> map["ukc"] = value
                    key.contains("fmw") -> map["fmw"] = value
                    key.contains("wetter") -> map["wetter"] = value
                    key.contains("gezeiten") -> map["gezeiten"] = value
                    key.contains("crew") -> map["crew"] = value
                }
            }
        }
    }
    if (map.isEmpty() && details.isNotBlank()) map["wetter"] = details
    return map
}

// ══════════════════════════════════════════════════════
// Format status text for chip
// ══════════════════════════════════════════════════════
private fun formatStatus(status: String): String {
    return when {
        status.contains("GO ✓") || status == "GO" -> "Befahrbar"
        status.contains("NO-GO") -> "Nicht befahrbar"
        else -> status
    }
}



