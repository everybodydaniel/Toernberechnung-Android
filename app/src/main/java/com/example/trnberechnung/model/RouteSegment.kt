package com.example.trnberechnung.model

import org.maplibre.android.geometry.LatLng

/**
 * Represents a part of a nautical route with a specific safety classification.
 */
enum class SegmentType {
    SAFE,       // Navigable with good margin (Blue)
    CRITICAL,   // Shallow water, low margin (Orange/Yellow)
    NO_GO       // Depth less than draft + margin (Red)
}

data class RouteSegment(
    val points: List<LatLng>,
    val type: SegmentType,
    val minDepth: Double = 0.0
)

data class DepthPoint(
    val position: LatLng,
    val depth: Double,
    val type: SegmentType
)
