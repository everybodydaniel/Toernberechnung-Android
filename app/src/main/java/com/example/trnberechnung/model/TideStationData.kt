package com.example.trnberechnung.model

data class TideStationData(
    val area: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val waterLevel: Double?,
    val meanHighWater: Double?,      
    val meanLowWater: Double?,       
    val gaugeLabel: String? = null,   
    val gaugeZeroNhn: Double? = null, 
    val chartDatumGauge: Double? = null, 
    val forecastTimestamp: String,
    val events: List<TideEvent>
)