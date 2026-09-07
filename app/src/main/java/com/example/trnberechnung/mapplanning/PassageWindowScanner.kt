package com.example.trnberechnung.mapplanning

import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class PassageCandidateAssessment(
    val expectedWaypointCount: Int,
    val waypointClearances: List<ClearanceSample>,
    val allLegsValid: Boolean,
    val safetyMarginMeters: Double = 0.0,
    val weatherStatus: WeatherStatus = WeatherStatus.BEFAHRBAR,
) {
    val isSafe: Boolean
        get() =
            expectedWaypointCount > 0 &&
                waypointClearances.size == expectedWaypointCount &&
                allLegsValid &&
                weatherStatus != WeatherStatus.NICHT_BEFAHRBAR &&
                waypointClearances.all { sample ->
                    val clearance = sample.clearanceMeters
                    if (!sample.isValid || clearance == null) {
                        // WICHTIG: Wenn keine Daten vorhanden sind, können wir nicht garantieren,
                        // dass die Passage sicher ist.
                        false
                    } else {
                        // Wir erlauben nun eine minimale Unterschreitung von 1cm, um numerische Rundungsfehler
                        // abzufangen.
                        clearance >= (safetyMarginMeters - 0.01)
                    }
                }

    val worstClearance: Double
        get() = waypointClearances.minOfOrNull { it.clearanceMeters ?: Double.NEGATIVE_INFINITY } ?: Double.NEGATIVE_INFINITY

    val bottleneck: ClearanceSample?
        get() = waypointClearances.minByOrNull { it.clearanceMeters ?: Double.NEGATIVE_INFINITY }

    val worstQuality: WaterLevelQuality
        get() {
            val hasMissingData = waypointClearances.any { it.clearanceMeters == null }
            val baseQuality = waypointClearances.maxByOrNull { it.waterLevelQuality.qualityRank }
                ?.waterLevelQuality ?: WaterLevelQuality.UNAVAILABLE
            return if (hasMissingData && baseQuality.qualityRank < WaterLevelQuality.UNAVAILABLE.qualityRank) {
                WaterLevelQuality.UNAVAILABLE
            } else {
                baseQuality
            }
        }
}

fun interface PassageCandidateEvaluator {
    suspend fun evaluate(departure: ZonedDateTime): PassageCandidateAssessment
}

class PassageWindowScanner(
    val scanIncrement: Duration = Duration.ofMinutes(10),
    val scanBackward: Duration = Duration.ofHours(12),
    val scanForward: Duration = Duration.ofHours(24),
) {
    init {
        require(!scanIncrement.isZero && !scanIncrement.isNegative) {
            "Das Scan-Intervall muss positiv sein."
        }
        require(!scanBackward.isNegative) { "Der Rückwärtsbereich darf nicht negativ sein." }
        require(!scanForward.isNegative) { "Der Vorwärtsbereich darf nicht negativ sein." }
    }

    suspend fun findSafeWindow(
        center: ZonedDateTime,
        evaluator: PassageCandidateEvaluator,
    ): PassageWindow? {
        val berlinCenter = center.withZoneSameInstant(MAP_PLANNING_ZONE_ID)
        val windows = findSafeWindows(berlinCenter, evaluator)

        // 1. Fenster, das den aktuellen Zeitpunkt enthält
        windows.find { it.contains(berlinCenter) }?.let { return it }

        // 2. Nächstes zukünftiges Fenster
        windows.filter { it.start.isAfter(berlinCenter) }
            .minByOrNull { it.start }?.let { return it }

        // 3. Letztes vergangenes Fenster (als Fallback)
        return windows.filter { it.end.isBefore(berlinCenter) }
            .maxByOrNull { it.end }
    }

    suspend fun findSafeWindows(
        center: ZonedDateTime,
        evaluator: PassageCandidateEvaluator,
    ): List<PassageWindow> {
        val berlinCenter = center.withZoneSameInstant(MAP_PLANNING_ZONE_ID)
        // Wir scannen den konfigurierten Bereich um den gewählten Zeitpunkt
        val scanStart = berlinCenter.minus(scanBackward)
        val scanEnd = berlinCenter.plus(scanForward)
        val windows = mutableListOf<PassageWindow>()
        var openWindow: OpenWindow? = null
        var candidate = scanStart

        while (!candidate.isAfter(scanEnd)) {
            currentCoroutineContext().ensureActive()
            val assessment = evaluator.evaluate(candidate)
            if (assessment.isSafe) {
                val safeAssessment = assessment.toSafeAssessment()
                openWindow =
                    openWindow?.apply {
                        end = candidate
                        merge(safeAssessment)
                    } ?: OpenWindow(
                        start = candidate,
                        end = candidate,
                        assessment = safeAssessment,
                    )
            } else {
                openWindow?.let { windows += it.toWindow() }
                openWindow = null
            }
            candidate = candidate.plus(scanIncrement)
        }

        openWindow?.let { windows += it.toWindow() }
        return windows
    }

    private data class SafeAssessment(
        val quality: WaterLevelQuality,
        val anchoredHighWater: ZonedDateTime?,
        val bottleneckName: String?,
        val worstClearance: Double,
    )

    private data class OpenWindow(
        val start: ZonedDateTime,
        var end: ZonedDateTime,
        var assessment: SafeAssessment,
    ) {
        fun merge(candidate: SafeAssessment) {
            // Wir behalten die Daten des "sichersten" Zeitpunkts im Fenster (meiste Wassertiefe),
            // damit die Anzeige von Wattenhoch und Engstelle repräsentativ ist.
            if (candidate.worstClearance > assessment.worstClearance) {
                assessment = candidate
            }
            // Aber die schlechteste Datenqualität gewinnt immer als Warnung
            if (candidate.quality.qualityRank > assessment.quality.qualityRank) {
                assessment = assessment.copy(quality = candidate.quality)
            }
        }

        fun toWindow(): PassageWindow =
            PassageWindow(
                start = start,
                end = end,
                anchoredHighWater = assessment.anchoredHighWater,
                bottleneckName = assessment.bottleneckName,
                waterLevelQuality = assessment.quality,
                waterLevelDetail = assessment.quality.detail,
            )
    }

    private fun PassageCandidateAssessment.toSafeAssessment(): SafeAssessment {
        val bottleneck = bottleneck
        return SafeAssessment(
            quality = worstQuality,
            anchoredHighWater = bottleneck?.anchoredHighWater,
            bottleneckName = bottleneck?.waypointName,
            worstClearance = worstClearance
        )
    }
}

private val WaterLevelQuality.qualityRank: Int
    get() =
        when (this) {
            WaterLevelQuality.LOCAL_OFFICIAL -> 0
            WaterLevelQuality.MANUAL -> 1
            WaterLevelQuality.CONFIRMED_COMPARISON -> 2
            WaterLevelQuality.STALE -> 3
            WaterLevelQuality.OUTSIDE_FORECAST_HORIZON -> 4
            WaterLevelQuality.UNAVAILABLE -> 5
        }

private val WaterLevelQuality.detail: String?
    get() =
        when (this) {
            WaterLevelQuality.LOCAL_OFFICIAL -> null
            WaterLevelQuality.MANUAL ->
                "Das Passagefenster verwendet eine manuelle Wasserstandskorrektur."

            WaterLevelQuality.CONFIRMED_COMPARISON ->
                "Das Passagefenster verwendet einen bestätigten Vergleichspegel."

            WaterLevelQuality.STALE ->
                "Die Wasserstandsprognose für das Passagefenster ist veraltet."

            WaterLevelQuality.OUTSIDE_FORECAST_HORIZON ->
                "Das Passagefenster basiert auf astronomischen Gezeitendaten."

            WaterLevelQuality.UNAVAILABLE ->
                "Für das Passagefenster liegt keine aktuelle lokale Wasserstandsprognose vor."
        }
