package com.example.trnberechnung.dto

import com.google.gson.Gson
import org.junit.Test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class WeatherDtoTest {

    private val gson = Gson()

    @Test
    fun `Deserialize full WeatherDto JSON`() {
        val json = """
            {
                "timestamp": "2024-01-01T12:00:00+00:00",
                "temperature": 20.5,
                "wind_speed": 15.0,
                "wind_direction": 180,
                "wind_gust_speed": 25.0,
                "condition": "clear-day",
                "icon": "clear-day",
                "cloud_cover": 10,
                "pressure_msl": 1013.2,
                "relative_humidity": 55,
                "precipitation": 0.0,
                "visibility": 20000,
                "sunshine": 60.0,
                "dew_point": 10.0,
                "solar": 500.0,
                "precipitation_probability": 0
            }
        """.trimIndent()

        val dto = gson.fromJson(json, WeatherDto::class.java)
        dto.shouldNotBeNull()
        dto.temperature shouldBe 20.5
        dto.windSpeed shouldBe 15.0
        dto.condition shouldBe "clear-day"
        dto.visibility shouldBe 20000
    }

    @Test
    fun `Deserialize minimal WeatherDto JSON`() {
        val json = """
            {
                "timestamp": "2024-01-01T12:00:00+00:00"
            }
        """.trimIndent()

        val dto = gson.fromJson(json, WeatherDto::class.java)
        dto.shouldNotBeNull()
        dto.timestamp shouldBe "2024-01-01T12:00:00+00:00"
        dto.temperature.shouldBeNull()
    }

    @Test
    fun `Deserialize BrightSkyResponseDto`() {
        val json = """
            {
                "weather": {
                    "timestamp": "2024-01-01T12:00:00+00:00",
                    "temperature": 15.0
                }
            }
        """.trimIndent()

        val dto = gson.fromJson(json, BrightSkyResponseDto::class.java)
        dto.shouldNotBeNull()
        dto.weather?.temperature shouldBe 15.0
    }

    @Test
    fun `Deserialize ForecastResponseDto`() {
        val json = """
            {
                "weather": [
                    {
                        "timestamp": "2024-01-01T12:00:00+00:00",
                        "temperature": 10.0
                    },
                    {
                        "timestamp": "2024-01-01T13:00:00+00:00",
                        "temperature": 11.0
                    }
                ]
            }
        """.trimIndent()

        val dto = gson.fromJson(json, ForecastResponseDto::class.java)
        dto.shouldNotBeNull()
        dto.weather.shouldNotBeNull()
        dto.weather.size shouldBe 2
        dto.weather[1].temperature shouldBe 11.0
    }
}
