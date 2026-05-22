package com.example.trnberechnung.model

import org.maplibre.android.geometry.LatLng

enum class SegmentType {
    SAFE,       
    CRITICAL,   
    NO_GO       
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
