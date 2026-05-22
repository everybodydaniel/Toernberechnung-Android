package com.example.trnberechnung.logic

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

object RuleOfTwelfths {

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
        val phase = elapsedMillis / totalDurationMillis 

        val totalHeightDiff = heightEnd - heightStart

        val twelfthsFractions = listOf(1.0, 2.0, 3.0, 3.0, 2.0, 1.0)

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

    fun calculateUKC(waterLevel: Double, chartDatumDepth: Double, boatDraft: Double): Double {
        val totalWaterDepth = waterLevel + chartDatumDepth
        return totalWaterDepth - boatDraft
    }

    fun evaluateGoNoGo(ukc: Double, safetyMargin: Double): Boolean {
        return ukc >= safetyMargin
    }
}
