package com.example.trnberechnung.repository

import com.example.trnberechnung.database.TideDao
import com.example.trnberechnung.database.TideEntity
import com.example.trnberechnung.model.LogbookDao
import com.example.trnberechnung.model.LogbookEntry
import com.example.trnberechnung.model.CrewMemberDao
import com.example.trnberechnung.model.CrewMember
import com.example.trnberechnung.model.TideStationData
import com.example.trnberechnung.model.toEntity
import com.example.trnberechnung.model.toModel
import com.example.trnberechnung.network.RetrofitInstance
import com.example.trnberechnung.dto.WeatherDto
import kotlinx.coroutines.flow.Flow

class TideRepository(
    private val tideDao: TideDao,
    private val logbookDao: LogbookDao,
    private val crewMemberDao: CrewMemberDao
) {

    // --- Tiden-Daten ---

    suspend fun getDataFromApi(): List<TideStationData> {
        return try {
            android.util.Log.d("BSH_API", "Calling BSH API...")
            val response = RetrofitInstance.bshApi.getWaterLevel(100, "north_sea")
            android.util.Log.d("BSH_API", "Response code: ${response.code()}")
            if (response.isSuccessful) {
                val body = response.body()
                android.util.Log.d("BSH_API", "Features count: ${body?.features?.size ?: "null body"}")
                val apiData = body?.features?.map { it.toModel() } ?: emptyList()
                android.util.Log.d("BSH_API", "Parsed stations: ${apiData.size}, first events: ${apiData.firstOrNull()?.events?.size ?: 0}")

                if (apiData.isNotEmpty()) {
                    tideDao.deleteAll()
                    tideDao.insertAll(apiData.map { it.toEntity() })
                }
                apiData
            } else {
                android.util.Log.e("BSH_API", "API error: ${response.code()} - ${response.errorBody()?.string()?.take(200)}")
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("BSH_API", "Exception: ${e.javaClass.simpleName}: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getDataFromDatabase(): List<TideEntity> {
        return tideDao.getAll()
    }

    // --- Wetter-Daten ---

    suspend fun getWeatherData(lat: Double, lon: Double): WeatherDto? {
        return try {
            val response = RetrofitInstance.dwdApi.getCurrentWeather(lat, lon)
            response.weather
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getForecastData(lat: Double, lon: Double, start: String, end: String): List<WeatherDto> {
        return try {
            val response = RetrofitInstance.dwdApi.getForecast(lat, lon, start, end)
            response.weather ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Logbuch ---

    val allLogs: Flow<List<LogbookEntry>> = logbookDao.getAllLogs()

    suspend fun insertLog(log: LogbookEntry) {
        logbookDao.insertLog(log)
    }

    suspend fun updateLog(log: LogbookEntry) {
        logbookDao.updateLog(log)
    }

    suspend fun deleteLog(log: LogbookEntry) {
        logbookDao.deleteLog(log)
    }

    suspend fun deleteAllLogs() {
        logbookDao.deleteAllLogs()
    }

    // --- Crew ---

    val allCrew: Flow<List<CrewMember>> = crewMemberDao.getAllCrew()

    suspend fun insertCrew(member: CrewMember) {
        crewMemberDao.insertCrew(member)
    }

    suspend fun updateCrew(member: CrewMember) {
        crewMemberDao.updateCrew(member)
    }

    suspend fun deleteCrew(member: CrewMember) {
        crewMemberDao.deleteCrew(member)
    }
}
