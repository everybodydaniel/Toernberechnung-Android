package com.example.trnberechnung.dto

import com.google.gson.annotations.SerializedName

data class WaterLevelItemDto(
    val area: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("water_level")
    val water_level: String?,
    @SerializedName("mean_high_water")
    val mean_high_water: String?,       
    @SerializedName("mean_low_water")
    val mean_low_water: String?,        
    @SerializedName("gauge_label")
    val gauge_label: String?,           
    @SerializedName("gaugezero_relative_to_nhn")
    val gaugezero_relative_to_nhn: String?,  
    @SerializedName("chartdatum_relative_to_gaugezero")
    val chartdatum_relative_to_gaugezero: String?,  
    @SerializedName("forecast_timestamp")
    val forecast_timestamp: String,
    @SerializedName("high_water_low_water")
    val high_water_low_water: List<HighLowWaterDto>
)