package com.example.trnberechnung.network

import com.example.trnberechnung.dto.BrightSkyResponseDto
import com.example.trnberechnung.dto.ForecastResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface DwdApiService {

    @GET("current_weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): BrightSkyResponseDto

    @GET("weather")
    suspend fun getForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("date") date: String,
        @Query("last_date") lastDate: String
    ): ForecastResponseDto
}
