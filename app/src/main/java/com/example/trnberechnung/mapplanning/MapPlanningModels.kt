package com.example.trnberechnung.mapplanning

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

val MAP_PLANNING_ZONE_ID: ZoneId = ZoneId.of("Europe/Berlin")

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

enum class HarbourId(val rawValue: String) {
    BORKUM_HARBOR("borkum_harbor"),
    EMDEN_HARBOR("emden_harbor"),
    JUIST_HARBOR("juist_harbor"),
    NORDERNEY_HARBOR("norderney_harbor"),
    BALTRUM_HARBOR("baltrum_harbor"),
    LANGEOOG_HARBOR("langeoog_harbor"),
    SPIEKEROOG_HARBOR("spiekeroog_harbor"),
    WANGEROOGE_HARBOR("wangerooge_harbor"),
    ;

    companion object {
        fun fromRawValue(rawValue: String?): HarbourId? =
            entries.firstOrNull { it.rawValue == rawValue?.trim() }
    }
}

data class Harbour(
    val id: HarbourId,
    val name: String,
    val subtitle: String,
    val coordinate: GeoPoint,
    val chartDepthMeters: Double,
    val tideStationId: String,
)

data class IntermediateStop(
    val id: UUID = UUID.randomUUID(),
    val harbourId: HarbourId,
)

data class BoatSettings(
    val draftMeters: Double = 1.1,
    val safetyMarginMeters: Double = 0.0,
    val speedKnots: Double = 6.0,
    val dieselLitersPerNm: Double = 0.35,
    val waterLevelCorrectionMeters: Double = 0.0,
) {
    init {
        require(draftMeters > 0) { "Der Tiefgang muss größer als 0 sein." }
        require(safetyMarginMeters >= 0) { "Der Sicherheitsabstand darf nicht negativ sein." }
        require(speedKnots > 0) { "Die Fahrtgeschwindigkeit muss größer als 0 sein." }
        require(dieselLitersPerNm >= 0) { "Der Dieselverbrauch darf nicht negativ sein." }
    }
}

enum class RouteStatus {
    BEFAHRBAR,
    EINGESCHRAENKT,
    NICHT_BEFAHRBAR,
    UNVOLLSTAENDIG,
}

enum class WeatherStatus {
    BEFAHRBAR,
    EINGESCHRAENKT,
    NICHT_BEFAHRBAR,
    UNVOLLSTAENDIG,
}

enum class WaterLevelQuality {
    LOCAL_OFFICIAL,
    MANUAL,
    CONFIRMED_COMPARISON,
    STALE,
    OUTSIDE_FORECAST_HORIZON,
    UNAVAILABLE,
}

data class RouteMetrics(
    val distanceNm: Double,
    val travelTime: Duration,
    val arrival: ZonedDateTime,
    val worstUnderKeelClearanceMeters: Double?,
    val draftMeters: Double = 0.0,
    val safetyMarginMeters: Double = 0.0,
    val dieselLiters: Double,
    val dieselReserveLiters: Double = 0.0,
    val worstClearanceName: String? = null,
    val worstHighWater: ZonedDateTime? = null,
    val maxWindKnots: Double? = null,
    val maxGustKnots: Double? = null,
    val averageTrueCourseDegrees: Double? = null,
    val averageCurrentSetDegrees: Double? = null,
    val averageCurrentDriftKnots: Double? = null,
) {
    val totalDieselLiters: Double get() = dieselLiters + dieselReserveLiters
    val requiredDepthMeters: Double get() = draftMeters + safetyMarginMeters
}

data class PassageWindow(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val anchoredHighWater: ZonedDateTime? = null,
    val bottleneckName: String? = null,
    val waterLevelQuality: WaterLevelQuality = WaterLevelQuality.LOCAL_OFFICIAL,
    val waterLevelDetail: String? = null,
) {
    fun contains(dateTime: ZonedDateTime): Boolean {
        val instant = dateTime.toInstant()
        return instant >= start.toInstant() && instant <= end.toInstant()
    }
}

data class RoutePlanningRequest(
    val startHarbourId: HarbourId,
    val destinationHarbourId: HarbourId,
    val intermediateStops: List<IntermediateStop>,
    val departure: ZonedDateTime,
    val boatSettings: BoatSettings,
) {
    val harbourChain: List<HarbourId>
        get() =
            buildList {
                add(startHarbourId)
                addAll(intermediateStops.map(IntermediateStop::harbourId))
                add(destinationHarbourId)
            }
}

data class RoutePlanningUiState(
    val startHarbourId: HarbourId? = null,
    val destinationHarbourId: HarbourId? = null,
    val intermediateStops: List<IntermediateStop> = emptyList(),
    val departure: ZonedDateTime,
    val boatSettings: BoatSettings = BoatSettings(),
    val routeGeometry: List<GeoPoint> = emptyList(),
    val routeStatus: RouteStatus = RouteStatus.UNVOLLSTAENDIG,
    val tidalStatus: RouteStatus = RouteStatus.UNVOLLSTAENDIG,
    val weatherStatus: WeatherStatus = WeatherStatus.UNVOLLSTAENDIG,
    val routeMetrics: RouteMetrics? = null,
    val passageWindows: List<PassageWindow> = emptyList(),
    val isCalculating: Boolean = false,
    val isSearchingPassageWindow: Boolean = false,
    val failureReason: SafetyFailureReason = SafetyFailureReason.NONE,
    val messages: List<String> = emptyList(),
    val error: String? = null,
) {
    val hasCompleteRouteInput: Boolean
        get() =
            startHarbourId != null &&
                destinationHarbourId != null &&
                startHarbourId != destinationHarbourId

    val routeTitle: String
        get() {
            val start = startHarbourId?.let(HarbourCatalog::get)
            val destination = destinationHarbourId?.let(HarbourCatalog::get)
            return if (start == null || destination == null) {
                "Törn noch nicht geplant"
            } else {
                "${start.name} → ${destination.name}"
            }
        }

    val passageWindow: PassageWindow?
        get() {
            if (passageWindows.isEmpty()) return null
            // 1. Fenster, das den gewählten Abfahrtszeitpunkt enthält
            passageWindows.find { it.contains(departure) }?.let { return it }
            // 2. Nächstes zukünftiges Fenster
            passageWindows.filter { it.start.isAfter(departure) }
                .minByOrNull { it.start }?.let { return it }
            // 3. Fallback: Das erste verfügbare Fenster
            return passageWindows.firstOrNull()
        }
}
