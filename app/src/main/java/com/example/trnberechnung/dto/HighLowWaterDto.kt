package com.example.trnberechnung.dto

import com.google.gson.annotations.SerializedName

data class HighLowWaterDto(
    @SerializedName("event")
    val event: String,              
    @SerializedName("event_timestamp")
    val eventTimestamp: String,      
    @SerializedName("forecast_value")
    val forecastValue: String?          
)