package com.example.trnberechnung.model

data class TideStationData(
    val area: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val waterLevel: Double?,
    val meanHighWater: Double?,      // cm relativ zum Pegelnullpunkt
    val meanLowWater: Double?,       // cm relativ zum Pegelnullpunkt
    val gaugeLabel: String? = null,   // z.B. "Alte Weser, Leuchtturm"
    val gaugeZeroNhn: Double? = null, // cm, Pegelnull relativ zu NHN
    val chartDatumGauge: Double? = null, // cm, Kartendatum relativ zu Pegelnull
    val forecastTimestamp: String,
    val events: List<TideEvent>
)