package com.example.trnberechnung.logic

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import com.example.trnberechnung.dto.WeatherDto
import com.example.trnberechnung.model.TideStationData

@RunWith(JUnit4::class)
class DecisionLogicTest {

    private fun createWeather(
        windSpeed: Double? = 10.0,
        condition: String? = "clear-day",
        visibility: Int? = 10000
    ) = WeatherDto(
        timestamp = "2024-01-01T12:00",
        temperature = 20.0,
        windSpeed = windSpeed,
        windDirection = 180,
        windGustSpeed = 15.0,
        condition = condition,
        icon = condition,
        cloudCover = 0,
        pressureMsl = 1013.0,
        relativeHumidity = 50,
        precipitation = 0.0,
        visibility = visibility,
        sunshine = 60.0,
        dewPoint = 10.0,
        solar = 500.0,
        precipitationProbability = 0
    )

    private val dummyStation = TideStationData(
        area = "Norderney",
        region = "Ostfriesland",
        latitude = 53.7,
        longitude = 7.1,
        waterLevel = null,
        meanHighWater = null,
        meanLowWater = null,
        forecastTimestamp = "2024-01-01",
        events = emptyList()
    )

    @Test
    fun `null weather returns Keine Wetterdaten`() {
        val result = DecisionLogic.calculateDecision(dummyStation, null)
        result shouldContain "Keine Wetterdaten verfügbar"
    }

    @Test
    fun `good conditions returns GUTE BEDINGUNGEN`() {
        val weather = createWeather(windSpeed = 15.0, visibility = 20000, condition = "clear-day")
        val result = DecisionLogic.calculateDecision(dummyStation, weather)
        result shouldContain "GUTE BEDINGUNGEN"
    }

    @Test
    fun `storm windSpeed 120 returns NO-GO`() {
        val weather = createWeather(windSpeed = 120.0) // Bft >= 8
        val result = DecisionLogic.calculateDecision(dummyStation, weather)
        result shouldContain "NO-GO"
    }

    @Test
    fun `thunderstorm returns NO-GO`() {
        val weather = createWeather(condition = "thunderstorm")
        val result = DecisionLogic.calculateDecision(dummyStation, weather)
        result shouldContain "NO-GO"
    }

    @Test
    fun `warning conditions due to fog returns EINGESCHRÄNKT`() {
        val weather = createWeather(visibility = 1500) // Fog warning between 1km and 2km
        val result = DecisionLogic.calculateDecision(dummyStation, weather)
        result shouldContain "EINGESCHRÄNKT"
    }

    @Test
    fun `evaluate null returns expected triple`() {
        val (isOk, isWarning, msg) = DecisionLogic.evaluate(null)
        isOk shouldBe false
        isWarning shouldBe true
        msg shouldContain "Keine Wetterdaten"
    }

    @Test
    fun `evaluate good conditions returns isOk true and isWarning false`() {
        val weather = createWeather(windSpeed = 10.0, visibility = 10000, condition = "clear-day")
        val (isOk, isWarning, _) = DecisionLogic.evaluate(weather)
        isOk shouldBe true
        isWarning shouldBe false
    }

    @Test
    fun `evaluate storm returns isOk false`() {
        val weather = createWeather(windSpeed = 120.0)
        val (isOk, _, _) = DecisionLogic.evaluate(weather)
        isOk shouldBe false
    }
}
