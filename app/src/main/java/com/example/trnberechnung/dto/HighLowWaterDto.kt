package com.example.trnberechnung.dto

import com.google.gson.annotations.SerializedName

// Diese Klasse beschreibt ein einzelnes HW/NW-Ereignis aus der BSH-API
data class HighLowWaterDto(
    @SerializedName("event")
    val event: String,              // "HW" oder "NW"
    @SerializedName("event_timestamp")
    val eventTimestamp: String,      // z.B. "2026-04-29 18:07:00+02:00"
    @SerializedName("forecast_value")
    val forecastValue: String?          // cm relativ zum Pegelnullpunkt (kann bei fernen Vorhersagen null oder leer sein)
)