package com.example.trnberechnung.logic

import com.example.trnberechnung.dto.WeatherDto
import com.example.trnberechnung.model.TideStationData
import com.example.trnberechnung.ui.kmhToBeaufort

object DecisionLogic {

    private const val WIND_NOGO_BFT = 8           
    private const val WIND_WARN_BFT = 6           
    private const val GUST_NOGO_KMH = 90.0        
    private const val GUST_WARN_KMH = 60.0        
    private const val VIS_NOGO_M = 1000            
    private const val VIS_WARN_M = 2000            
    private const val PRECIP_WARN_MM = 10.0        
    private const val PRESSURE_LOW_HPA = 995.0     

    private val NOGO_CONDITIONS = setOf("thunderstorm")
    private val WARN_CONDITIONS = setOf("fog", "hail", "sleet", "snow")

    fun calculateDecision(station: TideStationData, weather: WeatherDto?): String {
        if (weather == null) return "Keine Wetterdaten verfügbar"

        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var isNoGo = false

        val windKmh = weather.windSpeed ?: 0.0
        val bft = kmhToBeaufort(windKmh)
        if (bft >= WIND_NOGO_BFT) {
            isNoGo = true
            issues += "⛈ STURMWARNUNG Bft $bft (${formatKmh(windKmh)} km/h)"
        } else if (bft >= WIND_WARN_BFT) {
            warnings += "⚠ Starker Wind Bft $bft (${formatKmh(windKmh)} km/h)"
        }

        val gustKmh = weather.windGustSpeed ?: 0.0
        if (gustKmh >= GUST_NOGO_KMH) {
            isNoGo = true
            issues += "💨 Extreme Böen ${formatKmh(gustKmh)} km/h"
        } else if (gustKmh >= GUST_WARN_KMH) {
            warnings += "💨 Starke Böen ${formatKmh(gustKmh)} km/h"
        }

        val vis = weather.visibility
        if (vis != null) {
            if (vis < VIS_NOGO_M) {
                isNoGo = true
                issues += "🌫 Sicht unter 1 km (${vis}m) – Navigation gefährlich"
            } else if (vis < VIS_WARN_M) {
                warnings += "🌫 Eingeschränkte Sicht (${vis}m)"
            }
        }

        val condition = weather.condition?.lowercase()
        if (condition != null && NOGO_CONDITIONS.contains(condition)) {
            isNoGo = true
            issues += "⛈ Gewitter – Törn nicht durchführbar"
        } else if (condition != null && WARN_CONDITIONS.contains(condition)) {
            warnings += "⚠ ${conditionToGerman(condition)}"
        }

        val precip = weather.precipitation ?: 0.0
        if (precip >= PRECIP_WARN_MM) {
            warnings += "🌧 Starker Niederschlag (${formatMm(precip)} mm)"
        }

        val pressure = weather.pressureMsl
        if (pressure != null && pressure < PRESSURE_LOW_HPA) {
            warnings += "📉 Niedriger Luftdruck (${formatHpa(pressure)} hPa)"
        }

        return when {
            isNoGo -> {
                val allIssues = (issues + warnings).joinToString("\n")
                "🔴 NO-GO\n$allIssues"
            }
            warnings.isNotEmpty() -> {
                val allWarnings = warnings.joinToString("\n")
                "🟡 EINGESCHRÄNKT\n$allWarnings"
            }
            else -> "🟢 GUTE BEDINGUNGEN\n✅ Wind Bft $bft, Sicht ${formatVis(vis)}, keine Warnungen"
        }
    }

    fun evaluate(weather: WeatherDto?): Triple<Boolean, Boolean, String> {
        if (weather == null) return Triple(false, true, "Keine Wetterdaten")

        val windKmh = weather.windSpeed ?: 0.0
        val bft = kmhToBeaufort(windKmh)
        val gustKmh = weather.windGustSpeed ?: 0.0
        val vis = weather.visibility
        val condition = weather.condition?.lowercase()

        val isNoGo = bft >= WIND_NOGO_BFT
                || gustKmh >= GUST_NOGO_KMH
                || (vis != null && vis < VIS_NOGO_M)
                || (condition != null && NOGO_CONDITIONS.contains(condition))

        val isWarning = !isNoGo && (
                bft >= WIND_WARN_BFT
                || gustKmh >= GUST_WARN_KMH
                || (vis != null && vis < VIS_WARN_M)
                || (condition != null && WARN_CONDITIONS.contains(condition))
                || (weather.precipitation ?: 0.0) >= PRECIP_WARN_MM
                || (weather.pressureMsl ?: 1013.0) < PRESSURE_LOW_HPA
        )

        val summary = when {
            isNoGo -> "Törn nicht empfohlen"
            isWarning -> "Einschränkungen beachten"
            else -> "Gute Segelbedingungen"
        }

        return Triple(!isNoGo, isWarning, summary)
    }

    private fun formatKmh(v: Double) = "%.0f".format(v)
    private fun formatMm(v: Double) = "%.1f".format(v)
    private fun formatHpa(v: Double) = "%.0f".format(v)

    private fun formatVis(m: Int?): String {
        if (m == null) return "-"
        return if (m >= 1000) "${"%.1f".format(m / 1000.0)} km" else "${m}m"
    }

    private fun conditionToGerman(cond: String): String = when (cond) {
        "fog" -> "Nebel"
        "hail" -> "Hagel"
        "sleet" -> "Schneeregen"
        "snow" -> "Schnee"
        "thunderstorm" -> "Gewitter"
        else -> cond
    }
}
