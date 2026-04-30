package com.example.trnberechnung.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// Entity beschreibt eine Tabelle in Room
@Entity(tableName = "tide")
data class TideEntity(
    // Primärschlüssel der Tabelle
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // Gebiet der Messstation
    val area: String,

    // Region der Messstation
    val region: String,

    // Geografische Breite
    val latitude: Double,

    // Geografische Länge
    val longitude: Double,

    // Aktueller Wasserstand
    val waterLevel: Double?,

    // Mittleres Hochwasser
    val meanHighWater: Double?,

    // Mittleres Niedrigwasser
    val meanLowWater: Double?,

    // Zeitpunkt der Vorhersage
    val forecastTimestamp: String
)