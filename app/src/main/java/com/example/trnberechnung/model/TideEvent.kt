package com.example.trnberechnung.model

data class TideEvent(
    val timestamp: String,   // ISO timestamp
    val type: String,        // "HW" oder "NW"
    val value: Double?       // Wasserstand in Metern (relativ zum Pegelnullpunkt)
)