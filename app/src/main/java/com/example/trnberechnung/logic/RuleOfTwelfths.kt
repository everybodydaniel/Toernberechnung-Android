package com.example.trnberechnung.logic

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

object RuleOfTwelfths {

    /**
     * Berechnet den Wasserstand für einen bestimmten Zeitpunkt mittels der 12/1 Regel (Zwölfter-Regel).
     * @param timeStart Der Zeitpunkt des Beginns (z.B. Niedrigwasser)
     * @param heightStart Der Wasserstand zum Beginn
     * @param timeEnd Der Zeitpunkt des Endes (z.B. Hochwasser)
     * @param heightEnd Der Wasserstand zum Ende
     * @param targetTime Der Zeitpunkt, für den der Wasserstand berechnet werden soll
     * @return Der geschätzte Wasserstand zur targetTime.
     */
    fun calculateWaterLevel(
        timeStart: LocalDateTime,
        heightStart: Double,
        timeEnd: LocalDateTime,
        heightEnd: Double,
        targetTime: LocalDateTime
    ): Double {
        if (targetTime.isBefore(timeStart)) return heightStart
        if (targetTime.isAfter(timeEnd)) return heightEnd

        val totalDurationMillis = Duration.between(timeStart, timeEnd).toMillis().toDouble()
        if (totalDurationMillis <= 0) return heightEnd

        val elapsedMillis = Duration.between(timeStart, targetTime).toMillis().toDouble()
        val phase = elapsedMillis / totalDurationMillis // 0.0 bis 1.0

        val totalHeightDiff = heightEnd - heightStart

        // 12/1-Regel:
        // 1. "Stunde" (0 - 1/6): 1/12
        // 2. "Stunde" (1/6 - 2/6): 2/12
        // 3. "Stunde" (2/6 - 3/6): 3/12
        // 4. "Stunde" (3/6 - 4/6): 3/12
        // 5. "Stunde" (4/6 - 5/6): 2/12
        // 6. "Stunde" (5/6 - 6/6): 1/12
        
        // Akkumulierte Zwölftel pro Phase (1/6):
        // Ende Phase 1: 1/12
        // Ende Phase 2: 3/12 (1/4)
        // Ende Phase 3: 6/12 (1/2)
        // Ende Phase 4: 9/12 (3/4)
        // Ende Phase 5: 11/12
        // Ende Phase 6: 12/12 (1)

        val twelfthsFractions = listOf(1.0, 2.0, 3.0, 3.0, 2.0, 1.0)
        
        // Wir berechnen präzise den Anteil, indem wir die Phase in 6 Segmente unterteilen.
        val segmentLength = 1.0 / 6.0
        val currentSegmentIndex = (phase / segmentLength).toInt().coerceIn(0, 5)
        val fractionInCurrentSegment = (phase - (currentSegmentIndex * segmentLength)) / segmentLength

        var accumulatedTwelfths = 0.0
        for (i in 0 until currentSegmentIndex) {
            accumulatedTwelfths += twelfthsFractions[i]
        }
        accumulatedTwelfths += twelfthsFractions[currentSegmentIndex] * fractionInCurrentSegment

        val addedHeight = totalHeightDiff * (accumulatedTwelfths / 12.0)
        return heightStart + addedHeight
    }

    /**
     * Berechnet die Under-Keel Clearance (UKC).
     * @param waterLevel Der aktuell berechnete Wasserstand
     * @param chartDatumDepth Die Kartentiefe (Standardnull) an der gewünschten Stelle (positiv für Wassertiefe, negativ für Trockenfallen)
     * @param boatDraft Der Tiefgang des Bootes
     * @return Die verbleibende Distanz unter dem Kiel.
     */
    fun calculateUKC(waterLevel: Double, chartDatumDepth: Double, boatDraft: Double): Double {
        val totalWaterDepth = waterLevel + chartDatumDepth
        return totalWaterDepth - boatDraft
    }

    /**
     * Prüft, ob ein Passieren basierend auf UKC und Sicherheitsmarge möglich ist (Go/No-Go).
     * @param ukc Die berechnete Under-Keel Clearance
     * @param safetyMargin Die konfigurierte Sicherheitsmarge (z.B. 0.5m)
     * @return Wahr (Go), wenn UKC >= safetyMargin, ansonsten Falsch (No-Go).
     */
    fun evaluateGoNoGo(ukc: Double, safetyMargin: Double): Boolean {
        return ukc >= safetyMargin
    }
}
