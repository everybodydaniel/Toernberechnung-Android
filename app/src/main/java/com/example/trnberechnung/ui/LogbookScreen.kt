package com.example.trnberechnung.ui

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.trnberechnung.model.LogbookEntry
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.TideViewModel
import java.io.File
import java.io.FileOutputStream

@Composable
fun LogbookScreen(viewModel: TideViewModel) {
    val logs by viewModel.allLogs.collectAsState()
    val context = LocalContext.current

    var showDetails by remember { mutableStateOf(true) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var logToDelete by remember { mutableStateOf<LogbookEntry?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }

    // ── Dialog: Alle löschen ──
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            containerColor = NauticalSurface,
            title = { Text("Alle Einträge löschen?", color = NauticalTextPrimary) },
            text = { Text("Möchten Sie wirklich alle ${logs.size} Logbuch-Einträge unwiderruflich löschen?", color = NauticalTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllLogs()
                    showDeleteAllDialog = false
                    Toast.makeText(context, "Alle Einträge gelöscht", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Löschen", color = NauticalNoGo)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Abbrechen", color = NauticalTextSecondary)
                }
            }
        )
    }

    // ── Dialog: Einzelnen Eintrag löschen ──
    logToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { logToDelete = null },
            containerColor = NauticalSurface,
            title = { Text("Eintrag löschen?", color = NauticalTextPrimary) },
            text = { Text("\"${entry.routeDesc}\" vom ${entry.date} wirklich löschen?", color = NauticalTextSecondary) },
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
        // ── Header mit Aktionen ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .border(1.dp, NauticalDivider, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = NauticalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "LOGBUCH (${logs.size} Einträge)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NauticalTextPrimary
                    )
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(
                            if (showDetails) "Ausblenden ▲" else "Einblenden ▼",
                            color = NauticalPrimary
                        )
                    }
                }

                // ── Action-Buttons ──
                if (showDetails && logs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Alle/Keine auswählen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allSelected = selectedIds.size == logs.size && logs.isNotEmpty()
                        TextButton(onClick = {
                            selectedIds = if (allSelected) emptySet() else logs.map { it.id }.toSet()
                        }) {
                            Text(
                                if (allSelected) "Keine auswählen" else "Alle auswählen",
                                color = NauticalPrimary
                            )
                        }
                        if (selectedIds.isNotEmpty()) {
                            Text(
                                "${selectedIds.size} ausgewählt",
                                style = MaterialTheme.typography.bodySmall,
                                color = NauticalSecondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val toExport = if (selectedIds.isEmpty()) logs
                                               else logs.filter { it.id in selectedIds }
                                exportLogbookAsTxt(context, toExport)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = NauticalPrimary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(NauticalPrimary.copy(alpha = 0.5f))
                            )
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (selectedIds.isEmpty()) "Alle (TXT)" else "${selectedIds.size} (TXT)")
                        }
                        OutlinedButton(
                            onClick = { showDeleteAllDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = NauticalNoGo
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(NauticalNoGo.copy(alpha = 0.5f))
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Alle löschen")
                        }
                    }
                }
            }
        }

        // ── Logbuch-Einträge (ausblendbar) ──
        if (showDetails) {
            if (logs.isEmpty()) {
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        LogbookEntryCard(
                            log = log,
                            isSelected = log.id in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + log.id else selectedIds - log.id
                            },
                            onDelete = { logToDelete = log }
                        )
                    }
                }
            }
        } else {
            // Eingeklappter Platzhalter
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Logbuch eingeklappt – tippe oben auf \"Einblenden\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NauticalTextSecondary
                )
            }
        }
    }
}

@Composable
private fun LogbookEntryCard(log: LogbookEntry, isSelected: Boolean, onCheckedChange: (Boolean) -> Unit, onDelete: () -> Unit) {
    val isGo = log.status.contains("GO ✓") || log.status == "GO"
    val statusColor = if (isGo) NauticalGo else if (log.status.contains("NO-GO")) NauticalNoGo else NauticalPrimary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NauticalDivider, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = NauticalSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(36.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = NauticalPrimary,
                    uncheckedColor = NauticalTextSecondary,
                    checkmarkColor = NauticalTextOnPrimary
                )
            )
            Column(modifier = Modifier.weight(1f).padding(top = 12.dp, end = 12.dp, bottom = 12.dp)) {
            // Status-Badge + Datum + Löschen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = statusColor),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            log.status,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = if (isGo) NauticalTextOnPrimary else Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(log.date, style = MaterialTheme.typography.bodySmall, color = NauticalTextSecondary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Löschen",
                        tint = NauticalNoGo,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Route
            Text(
                log.routeDesc,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NauticalTextPrimary
            )

            // Messwerte
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(log.distance, style = MaterialTheme.typography.bodySmall, color = NauticalTextSecondary)
                Text(log.duration, style = MaterialTheme.typography.bodySmall, color = NauticalTextSecondary)
            }

            // Details
            if (log.details.isNotBlank()) {
                Text(
                    log.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = NauticalTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        }
    }
}

// PDF Funktion wurde gelöscht

// ── TXT Export (Downloads-Ordner) ──
private fun exportLogbookAsTxt(context: Context, logs: List<LogbookEntry>) {
    try {
        val boatProfile = com.example.trnberechnung.model.BoatProfileRepository(context)
        val sb = StringBuilder()
        sb.appendLine("========================================")
        sb.appendLine("  LOGBUCH – Törnberechnung Wattenmeer")
        sb.appendLine("  Exportiert am ${java.time.LocalDate.now()}")
        sb.appendLine("========================================")
        sb.appendLine()

        // Bootsprofil einfügen
        val profileSummary = boatProfile.getProfileSummary()
        if (profileSummary.isNotBlank()) {
            sb.appendLine("──── BOOTSPROFIL ────")
            sb.append(profileSummary)
            sb.appendLine("─────────────────────")
            sb.appendLine()
        }

        for ((index, log) in logs.withIndex()) {
            sb.appendLine("--- Eintrag ${index + 1} ---")
            sb.appendLine("Datum:    ${log.date}")
            sb.appendLine("Status:   ${log.status}")
            sb.appendLine("Route:    ${log.routeDesc}")
            sb.appendLine("Distanz:  ${log.distance}")
            sb.appendLine("Dauer:    ${log.duration}")
            if (log.details.isNotBlank()) {
                sb.appendLine("Details:  ${log.details}")
            }
            sb.appendLine()
        }

        sb.appendLine("========================================")
        sb.appendLine("${logs.size} Einträge exportiert.")

        // In den Downloads-Ordner schreiben
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val fileName = "logbuch_${java.time.LocalDate.now()}.txt"
        val file = File(downloadsDir, fileName)
        file.writeText(sb.toString())

        Toast.makeText(context, "Gespeichert: Downloads/$fileName", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        // Fallback: App-interner Speicher + Share
        try {
            val sb = StringBuilder()
            sb.appendLine("LOGBUCH – Törnberechnung Wattenmeer")
            sb.appendLine("Exportiert am ${java.time.LocalDate.now()}")
            sb.appendLine()
            for ((index, log) in logs.withIndex()) {
                sb.appendLine("--- Eintrag ${index + 1} ---")
                sb.appendLine("${log.date} | ${log.status} | ${log.routeDesc}")
                sb.appendLine("${log.distance} | ${log.duration}")
                if (log.details.isNotBlank()) sb.appendLine(log.details)
                sb.appendLine()
            }
            val fileName = "logbuch_${java.time.LocalDate.now()}.txt"
            val file = File(context.filesDir, fileName)
            file.writeText(sb.toString())

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Logbuch teilen"))
        } catch (e2: Exception) {
            Toast.makeText(context, "Export fehlgeschlagen: ${e2.message}", Toast.LENGTH_LONG).show()
        }
    }
}
