package com.example.trnberechnung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.model.BoatProfileRepository
import com.example.trnberechnung.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onStartNavigation: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { BoatProfileRepository(context) }
    val scrollState = rememberScrollState()

    var boatName by remember { mutableStateOf(repo.boatName) }
    var boatType by remember { mutableStateOf(repo.boatType) }
    var manufacturer by remember { mutableStateOf(repo.manufacturer) }
    var buildYear by remember { mutableStateOf(repo.buildYear) }
    var length by remember { mutableStateOf(if (repo.length > 0) repo.length.toString() else "") }
    var beam by remember { mutableStateOf(if (repo.beam > 0) repo.beam.toString() else "") }
    var draft by remember { mutableStateOf(repo.draft.toString()) }
    var displacement by remember { mutableStateOf(if (repo.displacement > 0) repo.displacement.toString() else "") }
    var speed by remember { mutableStateOf(repo.speed.toString()) }
    var safetyMargin by remember { mutableStateOf(repo.safetyMargin.toString()) }
    var fuelCapacity by remember { mutableStateOf(if (repo.fuelCapacity > 0) repo.fuelCapacity.toString() else "") }

    var showSavedBanner by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NauticalBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {

        Text(
            "BOOTSPROFIL",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = NauticalTextSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            if (boatName.isNotBlank()) boatName else "Kein Name hinterlegt",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = NauticalTextPrimary,
            modifier = Modifier.padding(bottom = 16.dp).testTag("boat_name_headline")
        )

        if (showSavedBanner) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = NauticalGoBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✅", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Bootsprofil gespeichert!",
                        fontWeight = FontWeight.Bold,
                        color = NauticalGo
                    )
                }
            }
        }

        SectionHeader(icon = Icons.Default.Info, title = "IDENTIFIKATION")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .border(1.dp, NauticalDivider, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = NauticalSurface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                NauticalTextField(
                    value = boatName,
                    onValueChange = { boatName = it; repo.boatName = it },
                    label = "Bootsname",
                    placeholder = "z.B. Freya",
                    modifier = Modifier.testTag("boat_name_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                NauticalTextField(
                    value = boatType,
                    onValueChange = { boatType = it; repo.boatType = it },
                    label = "Typ / Modell",
                    placeholder = "z.B. Hallberg-Rassy 31"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NauticalTextField(
                        value = manufacturer,
                        onValueChange = { manufacturer = it; repo.manufacturer = it },
                        label = "Hersteller",
                        placeholder = "z.B. Bavaria",
                        modifier = Modifier.weight(1f)
                    )
                    NauticalTextField(
                        value = buildYear,
                        onValueChange = { buildYear = it; repo.buildYear = it },
                        label = "Baujahr",
                        placeholder = "z.B. 2018",
                        modifier = Modifier.weight(0.6f),
                        isNumber = true
                    )
                }
            }
        }

        SectionHeader(icon = Icons.Default.Build, title = "ABMESSUNGEN")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .border(1.dp, NauticalDivider, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = NauticalSurface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileNumberField(
                        value = length,
                        onValueChange = { length = it; it.toFloatOrNull()?.let { v -> repo.length = v } },
                        label = "Länge (m)",
                        placeholder = "12.5",
                        modifier = Modifier.weight(1f)
                    )
                    ProfileNumberField(
                        value = beam,
                        onValueChange = { beam = it; it.toFloatOrNull()?.let { v -> repo.beam = v } },
                        label = "Breite (m)",
                        placeholder = "3.8",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileNumberField(
                        value = draft,
                        onValueChange = { draft = it; it.toFloatOrNull()?.let { v -> repo.draft = v } },
                        label = "Tiefgang (m)",
                        placeholder = "1.5",
                        modifier = Modifier.weight(1f)
                    )
                    ProfileNumberField(
                        value = displacement,
                        onValueChange = { displacement = it; it.toFloatOrNull()?.let { v -> repo.displacement = v } },
                        label = "Verdrängung (kg)",
                        placeholder = "8500",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SectionHeader(icon = Icons.Default.Settings, title = "BETRIEB & NAVIGATION")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .border(1.dp, NauticalDivider, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = NauticalSurface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileNumberField(
                        value = speed,
                        onValueChange = { speed = it; it.toFloatOrNull()?.let { v -> repo.speed = v } },
                        label = "Geschw. (kn)",
                        placeholder = "5.0",
                        modifier = Modifier.weight(1f)
                    )
                    ProfileNumberField(
                        value = safetyMargin,
                        onValueChange = { safetyMargin = it; it.toFloatOrNull()?.let { v -> repo.safetyMargin = v } },
                        label = "UKC-Marge (m)",
                        placeholder = "0.5",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                ProfileNumberField(
                    value = fuelCapacity,
                    onValueChange = { fuelCapacity = it; it.toFloatOrNull()?.let { v -> repo.fuelCapacity = v } },
                    label = "Kraftstoff Tank (Liter)",
                    placeholder = "200",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .border(1.dp, NauticalInfoText.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = NauticalInfoBg),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ℹ️", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Alle Eingaben werden automatisch gespeichert und beim nächsten Start geladen. Die Bootsdaten erscheinen auch im Logbuch-Export (TXT).",
                    fontSize = 12.sp,
                    color = NauticalInfoText,
                    lineHeight = 16.sp
                )
            }
        }

        Button(
            onClick = onStartNavigation,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("navigation_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NauticalPrimary)
        ) {
            Text(
                "ZUR KARTE →",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = NauticalTextOnPrimary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = NauticalPrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = NauticalTextSecondary
        )
    }
}

@Composable
private fun NauticalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    isNumber: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = NauticalTextSecondary) },
        placeholder = { Text(placeholder, color = NauticalTextSecondary.copy(alpha = 0.5f)) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
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

@Composable
private fun ProfileNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = NauticalTextSecondary) },
        placeholder = { Text(placeholder, color = NauticalTextSecondary.copy(alpha = 0.5f)) },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
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
