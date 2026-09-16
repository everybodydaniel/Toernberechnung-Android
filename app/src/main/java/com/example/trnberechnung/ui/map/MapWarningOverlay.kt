package com.example.trnberechnung.ui.map

/** Geometry supplied by an official warning source. The map never infers a shape from points. */
enum class MapWarningGeometryType {
    POINT,
    MULTI_POINT,
    LINE,
    AREA,
}

/** A source-provided WGS84 coordinate. */
data class MapWarningCoordinate(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Warning data needed by the existing nautical map.
 *
 * Use [mapWarningOverlayOrNull] at a source/domain boundary. Rendering validates the object again,
 * so incomplete or malformed source data can never place an estimated marker on the chart.
 */
data class MapWarningOverlay(
    val id: String,
    val title: String,
    val summary: String,
    val geometryType: MapWarningGeometryType,
    val coordinates: List<MapWarningCoordinate>,
)

data class MapWarningFocusAction(
    val overlay: MapWarningOverlay,
)

data class MapWarningDetailsAction(
    val warningId: String,
)

/** Prevents an already handled navigation focus from moving the map a second time. */
internal data class MapWarningFocusGate(
    val processedWarningId: String? = null,
) {
    fun canProcess(warningId: String): Boolean =
        warningId.isNotBlank() && warningId != processedWarningId

    fun markProcessed(warningId: String): MapWarningFocusGate =
        if (canProcess(warningId)) copy(processedWarningId = warningId) else this

    fun reset(): MapWarningFocusGate =
        if (processedWarningId == null) this else MapWarningFocusGate()
}

/**
 * Converts source/domain values into a renderable warning without completing missing geometry.
 */
fun mapWarningOverlayOrNull(
    id: String?,
    title: String?,
    summary: String?,
    geometryType: MapWarningGeometryType?,
    coordinates: List<MapWarningCoordinate>?,
): MapWarningOverlay? {
    val cleanId = id?.trim().orEmpty()
    val cleanTitle = title?.trim().orEmpty()
    val cleanCoordinates = coordinates?.toList().orEmpty()
    val type = geometryType ?: return null

    if (cleanId.isEmpty() || cleanTitle.isEmpty()) return null
    if (!cleanCoordinates.hasValidCardinality(type)) return null
    if (cleanCoordinates.any { !it.isValidWgs84() }) return null

    return MapWarningOverlay(
        id = cleanId,
        title = cleanTitle,
        summary = summary?.trim().orEmpty(),
        geometryType = type,
        coordinates = cleanCoordinates,
    )
}

/** Returns only complete overlays, keeping the first official entry for a stable ID. */
internal fun validatedMapWarningOverlays(overlays: List<MapWarningOverlay>): List<MapWarningOverlay> =
    overlays
        .mapNotNull { overlay ->
            mapWarningOverlayOrNull(
                id = overlay.id,
                title = overlay.title,
                summary = overlay.summary,
                geometryType = overlay.geometryType,
                coordinates = overlay.coordinates,
            )
        }
        .distinctBy(MapWarningOverlay::id)

/** Pure focus action used by navigation and camera code. */
fun mapWarningFocusActionOrNull(
    overlays: List<MapWarningOverlay>,
    warningId: String?,
): MapWarningFocusAction? {
    val cleanId = warningId?.trim().orEmpty()
    if (cleanId.isEmpty()) return null
    val overlay = validatedMapWarningOverlays(overlays).firstOrNull { it.id == cleanId } ?: return null
    return MapWarningFocusAction(overlay)
}

/** Pure details action behind the map summary's "Zur Meldung" affordance. */
fun mapWarningDetailsActionOrNull(
    overlays: List<MapWarningOverlay>,
    warningId: String?,
): MapWarningDetailsAction? {
    val focus = mapWarningFocusActionOrNull(overlays, warningId) ?: return null
    return MapWarningDetailsAction(focus.overlay.id)
}

private fun MapWarningCoordinate.isValidWgs84(): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0

private fun List<MapWarningCoordinate>.hasValidCardinality(type: MapWarningGeometryType): Boolean =
    when (type) {
        MapWarningGeometryType.POINT -> size == 1
        MapWarningGeometryType.MULTI_POINT -> isNotEmpty()
        MapWarningGeometryType.LINE -> size >= 2 && distinct().size >= 2
        MapWarningGeometryType.AREA -> size >= 3 && distinct().size >= 3
    }
