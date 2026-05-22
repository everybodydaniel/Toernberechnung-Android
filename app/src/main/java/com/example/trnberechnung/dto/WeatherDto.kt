package com.example.trnberechnung.dto

import com.google.gson.annotations.SerializedName

data class BrightSkyResponseDto(
    @SerializedName("weather")
    val weather: WeatherDto?
)

data class ForecastResponseDto(
    @SerializedName("weather")
    val weather: List<WeatherDto>?
)

data class WeatherDto(
    @SerializedName("timestamp")
    val timestamp: String?,
    @SerializedName("temperature")
    val temperature: Double?,
    @SerializedName("wind_speed")
    val windSpeed: Double?,        
    @SerializedName("wind_direction")
    val windDirection: Int?,       
    @SerializedName("wind_gust_speed")
    val windGustSpeed: Double?,    
    @SerializedName("condition")
    val condition: String?,
    @SerializedName("icon")
    val icon: String?,
    @SerializedName("cloud_cover")
    val cloudCover: Int?,          
    @SerializedName("pressure_msl")
    val pressureMsl: Double?,      
    @SerializedName("relative_humidity")
    val relativeHumidity: Int?,    
    @SerializedName("precipitation")
    val precipitation: Double?,    
    @SerializedName("visibility")
    val visibility: Int?,          
    @SerializedName("sunshine")
    val sunshine: Double?,         
    @SerializedName("dew_point")
    val dewPoint: Double?,         
    @SerializedName("solar")
    val solar: Double?,            
    @SerializedName("precipitation_probability")
    val precipitationProbability: Int?  
)
