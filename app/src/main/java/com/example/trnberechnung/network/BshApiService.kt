package com.example.trnberechnung.network

import com.example.trnberechnung.dto.WaterLevelResponseDto
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Response

interface BshApiService {

    @GET("ldproxy/rest/services/WaterLevelForecast/collections/waterlevelforecastdata/items")
    suspend fun getWaterLevel(
        @Query("limit") limit: Int,
        @Query("region") region: String,
        @Query("f") format: String = "json"
    ): Response<WaterLevelResponseDto>
}