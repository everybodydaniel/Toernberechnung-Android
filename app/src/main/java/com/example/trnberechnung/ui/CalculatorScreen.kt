package com.example.trnberechnung.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.trnberechnung.logic.RuleOfTwelfths
import com.example.trnberechnung.model.BoatProfileRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val boatProfile = remember { BoatProfileRepository(context) }

    var hwTimeStr by remember { mutableStateOf("2024-01-01T12:00:00") }
    var hwHeightStr by remember { mutableStateOf("3.5") }
    var nwTimeStr by remember { mutableStateOf("2024-01-01T18:00:00") }
    var nwHeightStr by remember { mutableStateOf("0.5") }
    var targetTimeStr by remember { mutableStateOf("2024-01-01T14:00:00") }

    var chartDatumDepthStr by remember { mutableStateOf("-0.5") } 

    var resultText by remember { mutableStateOf<String?>(null) }
    var goNoGoResult by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("12/1 Rechner & UKC") },
                navigationIcon = {
                    Button(onClick = onBack) {
                        Text("<")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Wasserstand & UKC (Zwölfter-Regel)", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = hwTimeStr,
                onValueChange = { hwTimeStr = it },
                label = { Text("Zeitpunkt Hochwasser (Format: ZZZZ-MM-TTHH:MM)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = hwHeightStr,
                onValueChange = { hwHeightStr = it },
                label = { Text("Wasserstand Hochwasser (m)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = nwTimeStr,
                onValueChange = { nwTimeStr = it },
                label = { Text("Zeitpunkt Niedrigwasser") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = nwHeightStr,
                onValueChange = { nwHeightStr = it },
                label = { Text("Wasserstand Niedrigwasser (m)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = targetTimeStr,
                onValueChange = { targetTimeStr = it },
                label = { Text("Zielzeitpunkt (Passage)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = chartDatumDepthStr,
                onValueChange = { chartDatumDepthStr = it },
                label = { Text("Kartentiefe am Wegpunkt (m, negativ bei Watthöhe)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    try {
                        val hwTime = LocalDateTime.parse(hwTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val nwTime = LocalDateTime.parse(nwTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val targetTime = LocalDateTime.parse(targetTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                        val hwHeight = hwHeightStr.toDouble()
                        val nwHeight = nwHeightStr.toDouble()
                        val chartDatum = chartDatumDepthStr.toDouble()

                        val (timeStart, heightStart, timeEnd, heightEnd) = if (hwTime.isBefore(nwTime)) {
                            listOf(hwTime, hwHeight, nwTime, nwHeight)
                        } else {
                            listOf(nwTime, nwHeight, hwTime, hwHeight)
                        }

                        val calculatedLevel = RuleOfTwelfths.calculateWaterLevel(
                            timeStart = timeStart as LocalDateTime,
                            heightStart = heightStart as Double,
                            timeEnd = timeEnd as LocalDateTime,
                            heightEnd = heightEnd as Double,
                            targetTime = targetTime
                        )

                        val ukc = RuleOfTwelfths.calculateUKC(
                            waterLevel = calculatedLevel,
                            chartDatumDepth = chartDatum,
                            boatDraft = boatProfile.draft.toDouble()
                        )

                        val isGo = RuleOfTwelfths.evaluateGoNoGo(
                            ukc = ukc,
                            safetyMargin = boatProfile.safetyMargin.toDouble()
                        )

                        resultText = String.format("Pegel: %.2fm | UKC: %.2fm\n(Tiefgang: %.2fm, Marge: %.2fm)", 
                                        calculatedLevel, ukc, boatProfile.draft, boatProfile.safetyMargin)
                        goNoGoResult = isGo
                    } catch (e: Exception) {
                        resultText = "Fehlerhafte Eingabe: ${e.message}"
                        goNoGoResult = null
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Go / No-Go Berechnen")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (resultText != null) {
                val containerColor = when (goNoGoResult) {
                    true -> MaterialTheme.colorScheme.primaryContainer
                    false -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val contentColor = when(goNoGoResult) {
                    true -> MaterialTheme.colorScheme.onPrimaryContainer
                    false -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = containerColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (goNoGoResult == true) "GO: Passage sicher!" 
                                   else if (goNoGoResult == false) "NO-GO: Gefahr der Grundberührung!" 
                                   else "Berechnungsfehler",
                            style = MaterialTheme.typography.titleLarge,
                            color = contentColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = resultText ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}
