package com.example.trnberechnung.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.trnberechnung.model.TideStationData
import com.example.trnberechnung.dto.WeatherDto
import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailScreen(
    station: TideStationData,
    weather: WeatherDto?,
    decision: String = "Keine Bewertung",
    onBack: () -> Unit,
    onOpenCalculator: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(station.area) },
                navigationIcon = {
                    Button(onClick = onBack) {
                        Text("< Zurück")
                    }
                },
                actions = {
                    Button(onClick = onOpenCalculator) {
                        Text("12/1 Rechner")
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

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (decision.contains("GO ✅")) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "Entscheidung: ",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        decision,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Aktuelles Wetter (DWD)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (weather != null) {
                        Text("Bedingung: ${weather.condition ?: "Unbekannt"}")
                        Text("Temperatur: ${weather.temperature ?: "-"} °C")
                        Text("Windgeschwindigkeit: ${weather.windSpeed ?: "-"} km/h")
                        Text("Windrichtung: ${weather.windDirection ?: "-"}°")
                    } else {
                        Text("Wetterdaten werden geladen...")
                        CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            Text("Tidenkalender", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(station.events) { event ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val timeLabel = try {
                                val odt = OffsetDateTime.parse(event.timestamp)
                                odt.format(DateTimeFormatter.ofPattern("HH:mm"))
                            } catch (e: Exception) {
                                event.timestamp
                            }

                            val isHighWater = event.type.contains("HW", ignoreCase = true) || event.type.contains("Hochwasser", ignoreCase = true)

                            Text(
                                text = event.type,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isHighWater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "$timeLabel Uhr",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
