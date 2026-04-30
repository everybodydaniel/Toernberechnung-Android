package com.example.trnberechnung.logic

import com.example.trnberechnung.dto.WeatherDto
import com.example.trnberechnung.model.TideStationData
import com.example.trnberechnung.ui.kmhToBeaufort

/**
 * Zentrale Bewertungslogik für die Törn-Entscheidung (Go / Warnung / No-Go).
 *
 * Bewertet auf Basis aller verfügbaren Wetterdaten:
 *  - Wind (Beaufort)
 *  - Windböen (km/h)
 *  - Sichtweite (m)
 *  - Niederschlag (mm)
 *  - Wetterlage (Gewitter, Nebel …)
 *  - Luftdruck-Trend (hPa)
 */
object DecisionLogic {

    // ── Schwellenwerte ──────────────────────────────────────────
    private const val WIND_NOGO_BFT = 8           // Ab Bft 8 → NO-GO
    private const val WIND_WARN_BFT = 6           // Ab Bft 6 → Warnung
    private const val GUST_NOGO_KMH = 90.0        // Böen ≥ 90 km/h → NO-GO
    private const val GUST_WARN_KMH = 60.0        // Böen ≥ 60 km/h → Warnung
    private const val VIS_NOGO_M = 1000            // Sicht < 1 km → NO-GO
    private const val VIS_WARN_M = 2000            // Sicht < 2 km → Warnung
    private const val PRECIP_WARN_MM = 10.0        // Niederschlag ≥ 10 mm → Warnung
    private const val PRESSURE_LOW_HPA = 995.0     // Tiefdruck → Warnung

    // Gefährliche Conditions
    private val NOGO_CONDITIONS = setOf("thunderstorm")
    private val WARN_CONDITIONS = setOf("fog", "hail", "sleet", "snow")

    /**
     * Erzeugt einen zusammenfassenden Bewertungstext.
     * Wird vom ViewModel als _decision-State verwendet.
     *
     * @return Formatierter String mit Gesamt-Bewertung und Einzelbewertungen
     */
    fun calculateDecision(station: TideStationData, weather: WeatherDto?): String {
        if (weather == null) return "Keine Wetterdaten verfügbar"

        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var isNoGo = false

        // ── 1) Wind (Beaufort) ──
        val windKmh = weather.windSpeed ?: 0.0
        val bft = kmhToBeaufort(windKmh)
        if (bft >= WIND_NOGO_BFT) {
            isNoGo = true
            issues += "⛈ STURMWARNUNG Bft $bft (${formatKmh(windKmh)} km/h)"
        } else if (bft >= WIND_WARN_BFT) {
            warnings += "⚠ Starker Wind Bft $bft (${formatKmh(windKmh)} km/h)"
        }

        // ── 2) Windböen ──
        val gustKmh = weather.windGustSpeed ?: 0.0
        if (gustKmh >= GUST_NOGO_KMH) {
            isNoGo = true
            issues += "💨 Extreme Böen ${formatKmh(gustKmh)} km/h"
        } else if (gustKmh >= GUST_WARN_KMH) {
            warnings += "💨 Starke Böen ${formatKmh(gustKmh)} km/h"
        }

        // ── 3) Sichtweite ──
        val vis = weather.visibility
        if (vis != null) {
            if (vis < VIS_NOGO_M) {
                isNoGo = true
                issues += "🌫 Sicht unter 1 km (${vis}m) – Navigation gefährlich"
            } else if (vis < VIS_WARN_M) {
                warnings += "🌫 Eingeschränkte Sicht (${vis}m)"
            }
        }

        // ── 4) Wetterlage / Condition ──
        val condition = weather.condition?.lowercase()
        if (condition != null && NOGO_CONDITIONS.contains(condition)) {
            isNoGo = true
            issues += "⛈ Gewitter – Törn nicht durchführbar"
        } else if (condition != null && WARN_CONDITIONS.contains(condition)) {
            warnings += "⚠ ${conditionToGerman(condition)}"
        }

        // ── 5) Niederschlag ──
        val precip = weather.precipitation ?: 0.0
        if (precip >= PRECIP_WARN_MM) {
            warnings += "🌧 Starker Niederschlag (${formatMm(precip)} mm)"
        }

        // ── 6) Luftdruck ──
        val pressure = weather.pressureMsl
        if (pressure != null && pressure < PRESSURE_LOW_HPA) {
            warnings += "📉 Niedriger Luftdruck (${formatHpa(pressure)} hPa)"
        }

        // ── Gesamt-Bewertung ──
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

    /**
     * Gibt ein einfaches Ergebnis-Triple zurück für UI-Ampelfarbe.
     * @return Triple(isGo, isWarning, summaryText)
     */
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

    // ── Formatierungshelfer ──

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
