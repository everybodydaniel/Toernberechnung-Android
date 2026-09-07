package com.example.trnberechnung.tides

import com.example.trnberechnung.logic.RuleOfTwelfths
import com.example.trnberechnung.logic.TideTimes
import com.example.trnberechnung.mapplanning.MarineWeatherAssessment
import com.example.trnberechnung.model.TideStationData
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

fun TideStationData.tideHeightAt(time: ZonedDateTime): Double? {
    if (events.isEmpty()) return null

    val sortedEvents = events.mapNotNull { event ->
        TideTimes.parseZDT(event.timestamp)?.let { event to it }
    }.sortedBy { it.second }

    if (sortedEvents.isEmpty()) return null

    // Find the two events surrounding the target time
    val nextEventIndex = sortedEvents.indexOfFirst {
        !it.second.isBefore(time)
    }

    if (nextEventIndex == -1) return null
    if (sortedEvents[nextEventIndex].second == time) return sortedEvents[nextEventIndex].first.value
    if (nextEventIndex == 0) return null

    val eventBefore = sortedEvents[nextEventIndex - 1]
    val eventAfter = sortedEvents[nextEventIndex]

    if (eventBefore.first.value == null || eventAfter.first.value == null) return null

    return RuleOfTwelfths.calculateWaterLevel(
        timeStart = eventBefore.second.toLocalDateTime(),
        heightStart = eventBefore.first.value!!,
        timeEnd = eventAfter.second.toLocalDateTime(),
        heightEnd = eventAfter.first.value!!,
        targetTime = time.toLocalDateTime()
    )
}

fun TideStationData.nearestHighWater(time: ZonedDateTime): ZonedDateTime? {
    return events
        .filter { it.type.contains("Hochwasser", ignoreCase = true) || it.type.equals("HW", ignoreCase = true) }
        .mapNotNull { event -> TideTimes.parseZDT(event.timestamp) }
        .minByOrNull {
            abs(ChronoUnit.MINUTES.between(it, time))
        }?.let {
            // Nur das Hochwasser der aktuellen Tide zurückgeben (max. 6,5 Stunden Differenz).
            // Das verhindert, dass bei nächtlichen Fenstern das Hochwasser vom nächsten Tag angezeigt wird.
            if (abs(ChronoUnit.MINUTES.between(it, time)) < 400) it else null
        }
}

fun TideStationData.weatherAt(time: ZonedDateTime): MarineWeatherAssessment? {
    if (weatherForecast.isEmpty()) return null

    val targetInstant = time.toInstant()
    val nearest = weatherForecast.mapNotNull { forecast ->
        TideTimes.parseZDT(forecast.timestamp)?.let { forecast to it }
    }.minByOrNull {
        abs(ChronoUnit.MINUTES.between(it.second.toInstant(), targetInstant))
    } ?: return null

    val diffMinutes = abs(ChronoUnit.MINUTES.between(nearest.second.toInstant(), targetInstant))
    if (diffMinutes > 90) return null

    val forecast = nearest.first
    return MarineWeatherAssessment(
        // Bright Sky / DWD liefert km/h. Die nautischen Grenzwerte verwenden Knoten.
        windKnots = (forecast.windSpeed ?: 0.0) / KILOMETERS_PER_HOUR_PER_KNOT,
        gustKnots = (forecast.windGustSpeed ?: forecast.windSpeed ?: 0.0) / KILOMETERS_PER_HOUR_PER_KNOT,
        visibilityKilometers = (forecast.visibility ?: 10000).toDouble() / 1000.0,
        precipitationChancePercent = (forecast.precipitationProbability ?: 0).toDouble(),
        precipitationMillimeters = forecast.precipitation ?: 0.0
    )
}

private const val KILOMETERS_PER_HOUR_PER_KNOT = 1.852
