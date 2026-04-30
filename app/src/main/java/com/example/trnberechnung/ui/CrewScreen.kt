package com.example.trnberechnung.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.model.CrewMember
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.TideViewModel

// ── Rang-Farben ──
private val rankColors = mapOf(
    "Skipper" to Color(0xFFFFD700),        // Gold
    "Navigator" to Color(0xFF00BFA6),      // Teal
    "Steuermann" to Color(0xFF4FC3F7),     // Cyan
    "Matrose" to Color(0xFF81C784),         // Grün
    "Koch" to Color(0xFFFFB74D),           // Amber
    "Funker" to Color(0xFFBA68C8),         // Violett
    "Bootsmann" to Color(0xFF4DD0E1),      // Hellcyan
    "Sonstiges" to Color(0xFF90A4AE)       // Grau
)

private val availableRanks = listOf(
    "Skipper", "Navigator", "Steuermann", "Matrose",
    "Koch", "Funker", "Bootsmann", "Sonstiges"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewScreen(viewModel: TideViewModel) {
    val crew by viewModel.allCrew.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<CrewMember?>(null) }
    var memberToDelete by remember { mutableStateOf<CrewMember?>(null) }

    // ── Dialog: Mitglied löschen ──
    memberToDelete?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            containerColor = NauticalSurface,
            title = { Text("Crewmitglied löschen?", color = NauticalTextPrimary) },
            text = {
                Text(
                    "\"${member.name}\" (${member.rank}) wirklich aus der Crew entfernen?",
                    color = NauticalTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCrew(member)
                    memberToDelete = null
                    Toast.makeText(context, "${member.name} entfernt", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Löschen", color = NauticalNoGo)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) {
                    Text("Abbrechen", color = NauticalTextSecondary)
                }
            }
        )
    }

    // ── Dialog: Hinzufügen / Bearbeiten ──
    if (showAddDialog || editingMember != null) {
        CrewMemberDialog(
            existingMember = editingMember,
            onDismiss = {
                showAddDialog = false
                editingMember = null
            },
            onSave = { member ->
                if (editingMember != null) {
                    viewModel.updateCrew(member)
                    Toast.makeText(context, "${member.name} aktualisiert", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.saveCrew(member)
                    Toast.makeText(context, "${member.name} hinzugefügt", Toast.LENGTH_SHORT).show()
                }
                showAddDialog = false
                editingMember = null
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(NauticalBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .border(1.dp, NauticalDivider, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = NauticalSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "CREW",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NauticalTextPrimary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${crew.size} Mitglieder · ${crew.count { it.isOnBoard }} an Bord",
                                style = MaterialTheme.typography.bodySmall,
                                color = NauticalTextSecondary
                            )
                        }
                        // Hinzufügen-Button im Header
                        FilledTonalButton(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = NauticalPrimary.copy(alpha = 0.15f),
                                contentColor = NauticalPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Hinzufügen",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Neu", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // ── Status-Übersicht ──
                    if (crew.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // An Bord
                            StatusChip(
                                label = "An Bord",
                                count = crew.count { it.isOnBoard },
                                color = NauticalGo,
                                modifier = Modifier.weight(1f)
                            )
                            // Nicht an Bord
                            StatusChip(
                                label = "Nicht an Bord",
                                count = crew.count { !it.isOnBoard },
                                color = NauticalNoGo,
                                modifier = Modifier.weight(1f)
                            )
                            // Medizinisches
                            val medCount = crew.count { it.medicalNote.isNotBlank() }
                            if (medCount > 0) {
                                StatusChip(
                                    label = "Med. Hinweis",
                                    count = medCount,
                                    color = NauticalAccentWarm,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Crew-Liste ──
            if (crew.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚓", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Noch keine Crew an Bord",
                            style = MaterialTheme.typography.titleMedium,
                            color = NauticalTextSecondary
                        )
                        Text(
                            "Tippe auf \"Neu\" um Crewmitglieder hinzuzufügen",
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(crew, key = { it.id }) { member ->
                        CrewMemberCard(
                            member = member,
                            onEdit = { editingMember = member },
                            onDelete = { memberToDelete = member },
                            onToggleOnBoard = {
                                viewModel.updateCrew(member.copy(isOnBoard = !member.isOnBoard))
                            },
                            onCallEmergency = {
                                if (member.emergencyPhone.isNotBlank()) {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${member.emergencyPhone}")
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Status-Chip ──
@Composable
private fun StatusChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

// ── Crew-Mitglied-Karte ──
@Composable
private fun CrewMemberCard(
    member: CrewMember,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleOnBoard: () -> Unit,
    onCallEmergency: () -> Unit
) {
    val rankColor = rankColors[member.rank] ?: NauticalTextSecondary
    val onBoardColor = if (member.isOnBoard) NauticalGo else NauticalNoGo

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NauticalDivider, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = NauticalSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ── Obere Zeile: Avatar + Name + Rang + Aktionen ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar mit Rang-Farbe
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(rankColor.copy(alpha = 0.6f), rankColor.copy(alpha = 0.2f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        member.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name + Rang
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        member.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NauticalTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Rang-Badge
                        Card(
                            colors = CardDefaults.cardColors(containerColor = rankColor.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                member.rank,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                color = rankColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // On-Board Indikator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(onBoardColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (member.isOnBoard) "An Bord" else "Nicht an Bord",
                            style = MaterialTheme.typography.labelSmall,
                            color = onBoardColor.copy(alpha = 0.8f)
                        )
                    }
                }

                // Aktions-Buttons
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Bearbeiten",
                        tint = NauticalPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Löschen",
                        tint = NauticalNoGo,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Medizinische Hinweise ──
            if (member.medicalNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = NauticalAccentWarm.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Medizinisch",
                            tint = NauticalAccentWarm,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                "Medizinischer Hinweis",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = NauticalAccentWarm
                            )
                            Text(
                                member.medicalNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = NauticalTextSecondary
                            )
                        }
                    }
                }
            }

            // ── Untere Zeile: Notfallnummer + An-Bord-Toggle ──
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Notfallnummer
                if (member.emergencyPhone.isNotBlank()) {
                    OutlinedButton(
                        onClick = onCallEmergency,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NauticalSecondary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder(true).copy(
                            brush = androidx.compose.ui.graphics.SolidColor(NauticalDivider)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Anrufen",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            member.emergencyPhone,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Text(
                        "Keine Notfallnr.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NauticalTextSecondary.copy(alpha = 0.5f)
                    )
                }

                // An-Bord Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "An Bord",
                        style = MaterialTheme.typography.labelSmall,
                        color = NauticalTextSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = member.isOnBoard,
                        onCheckedChange = { onToggleOnBoard() },
                        modifier = Modifier.height(24.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NauticalGo,
                            checkedTrackColor = NauticalGo.copy(alpha = 0.3f),
                            uncheckedThumbColor = NauticalNoGo,
                            uncheckedTrackColor = NauticalNoGo.copy(alpha = 0.2f),
                            uncheckedBorderColor = Color.Transparent,
                            checkedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

// ── Dialog: Crewmitglied anlegen/bearbeiten ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrewMemberDialog(
    existingMember: CrewMember?,
    onDismiss: () -> Unit,
    onSave: (CrewMember) -> Unit
) {
    var name by remember { mutableStateOf(existingMember?.name ?: "") }
    var selectedRank by remember { mutableStateOf(existingMember?.rank ?: availableRanks.first()) }
    var isOnBoard by remember { mutableStateOf(existingMember?.isOnBoard ?: true) }
    var medicalNote by remember { mutableStateOf(existingMember?.medicalNote ?: "") }
    var emergencyPhone by remember { mutableStateOf(existingMember?.emergencyPhone ?: "") }
    var rankDropdownExpanded by remember { mutableStateOf(false) }

    val isEditing = existingMember != null
    val isValid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NauticalSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = NauticalPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isEditing) "Crew bearbeiten" else "Neues Crewmitglied",
                    color = NauticalTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NauticalPrimary,
                        unfocusedBorderColor = NauticalDivider,
                        focusedLabelColor = NauticalPrimary,
                        unfocusedLabelColor = NauticalTextSecondary,
                        cursorColor = NauticalPrimary,
                        focusedTextColor = NauticalTextPrimary,
                        unfocusedTextColor = NauticalTextPrimary
                    )
                )

                // Rang-Dropdown
                ExposedDropdownMenuBox(
                    expanded = rankDropdownExpanded,
                    onExpandedChange = { rankDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedRank,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rang") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rankDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NauticalPrimary,
                            unfocusedBorderColor = NauticalDivider,
                            focusedLabelColor = NauticalPrimary,
                            unfocusedLabelColor = NauticalTextSecondary,
                            focusedTextColor = NauticalTextPrimary,
                            unfocusedTextColor = NauticalTextPrimary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = rankDropdownExpanded,
                        onDismissRequest = { rankDropdownExpanded = false },
                        containerColor = NauticalSurfaceVariant
                    ) {
                        availableRanks.forEach { rank ->
                            val rankColor = rankColors[rank] ?: NauticalTextSecondary
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(rankColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(rank, color = NauticalTextPrimary)
                                    }
                                },
                                onClick = {
                                    selectedRank = rank
                                    rankDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // An Bord Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("An Bord", color = NauticalTextPrimary)
                    Switch(
                        checked = isOnBoard,
                        onCheckedChange = { isOnBoard = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NauticalGo,
                            checkedTrackColor = NauticalGo.copy(alpha = 0.3f),
                            uncheckedThumbColor = NauticalNoGo,
                            uncheckedTrackColor = NauticalNoGo.copy(alpha = 0.2f),
                            uncheckedBorderColor = Color.Transparent,
                            checkedBorderColor = Color.Transparent
                        )
                    )
                }

                // Medizinische Hinweise
                OutlinedTextField(
                    value = medicalNote,
                    onValueChange = { medicalNote = it },
                    label = { Text("Medizinische Hinweise") },
                    placeholder = { Text("z.B. Allergie, Diabetes...", color = NauticalTextSecondary.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NauticalAccentWarm,
                        unfocusedBorderColor = NauticalDivider,
                        focusedLabelColor = NauticalAccentWarm,
                        unfocusedLabelColor = NauticalTextSecondary,
                        cursorColor = NauticalPrimary,
                        focusedTextColor = NauticalTextPrimary,
                        unfocusedTextColor = NauticalTextPrimary
                    )
                )

                // Notfallnummer
                OutlinedTextField(
                    value = emergencyPhone,
                    onValueChange = { emergencyPhone = it },
                    label = { Text("Notfallnummer") },
                    placeholder = { Text("+49 ...", color = NauticalTextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = null,
                            tint = NauticalTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NauticalPrimary,
                        unfocusedBorderColor = NauticalDivider,
                        focusedLabelColor = NauticalPrimary,
                        unfocusedLabelColor = NauticalTextSecondary,
                        cursorColor = NauticalPrimary,
                        focusedTextColor = NauticalTextPrimary,
                        unfocusedTextColor = NauticalTextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val member = CrewMember(
                        id = existingMember?.id ?: 0,
                        name = name.trim(),
                        rank = selectedRank,
                        isOnBoard = isOnBoard,
                        medicalNote = medicalNote.trim(),
                        emergencyPhone = emergencyPhone.trim()
                    )
                    onSave(member)
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NauticalPrimary,
                    contentColor = NauticalTextOnPrimary,
                    disabledContainerColor = NauticalDivider,
                    disabledContentColor = NauticalTextSecondary
                )
            ) {
                Text(
                    if (isEditing) "Speichern" else "Hinzufügen",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = NauticalTextSecondary)
            }
        }
    )
}
