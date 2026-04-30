package com.example.trnberechnung.dto

import com.google.gson.annotations.SerializedName

// Response from /current_weather
data class BrightSkyResponseDto(
    @SerializedName("weather")
    val weather: WeatherDto?
)

// Response from /weather (hourly forecast list)
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
    val windSpeed: Double?,        // km/h
    @SerializedName("wind_direction")
    val windDirection: Int?,       // degrees
    @SerializedName("wind_gust_speed")
    val windGustSpeed: Double?,    // km/h
    @SerializedName("condition")
    val condition: String?,
    @SerializedName("icon")
    val icon: String?,
    @SerializedName("cloud_cover")
    val cloudCover: Int?,          // 0-100 %
    @SerializedName("pressure_msl")
    val pressureMsl: Double?,      // hPa
    @SerializedName("relative_humidity")
    val relativeHumidity: Int?,    // %
    @SerializedName("precipitation")
    val precipitation: Double?,    // mm
    @SerializedName("visibility")
    val visibility: Int?,          // m
    @SerializedName("sunshine")
    val sunshine: Double?,         // minutes in last hour
    @SerializedName("dew_point")
    val dewPoint: Double?,         // °C
    @SerializedName("solar")
    val solar: Double?,            // kWh/m²
    @SerializedName("precipitation_probability")
    val precipitationProbability: Int?  // %
)
