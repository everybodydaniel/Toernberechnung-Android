package com.example.trnberechnung.routing.v2

import com.example.trnberechnung.logic.RouterLog
import com.example.trnberechnung.logic.RuleOfTwelfths
import com.example.trnberechnung.model.DepthPoint
import com.example.trnberechnung.model.RouteSegment
import com.example.trnberechnung.model.SegmentType
import com.example.trnberechnung.model.TideEvent
import org.maplibre.android.geometry.LatLng
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object NauticalRouterV2 {

    private const val TAG = "NauticalRouterV2"

    private const val DEPTH_SAMPLE_M = 250.0

    private val pathfinder = AStarPathfinder()

    private data class BridgeRule(val harbor: LatLng, val via: List<LatLng>, val matchRadiusM: Double)

    private val bridgeRules = listOf(

        BridgeRule(
            harbor = LatLng(53.3382, 7.1945),
            via = listOf(
                LatLng(53.3200, 7.1800), 
                LatLng(53.3100, 7.0500), 
                LatLng(53.3700, 6.9200), 
                LatLng(53.4500, 6.9500)  
            ),
            matchRadiusM = 3_000.0
        ),

        BridgeRule(
            harbor = LatLng(53.5150, 8.1500),
            via = listOf(
                LatLng(53.52743262744863, 8.20080110975966),  
                LatLng(53.65600309601381, 8.137997413254762)   
            ),
            matchRadiusM = 5_000.0
        ),

        BridgeRule(
            harbor = LatLng(53.4500, 8.1200),
            via = listOf(
                LatLng(53.52743262744863, 8.20080110975966),  
                LatLng(53.65600309601381, 8.137997413254762)   
            ),
            matchRadiusM = 5_000.0
        ),

        BridgeRule(
            harbor = LatLng(53.6280, 8.0430),
            via = listOf(
                LatLng(53.660676, 8.102389),                   
                LatLng(53.65600309601381, 8.137997413254762)   
            ),
            matchRadiusM = 5_000.0
        ),

        BridgeRule(
            harbor = LatLng(53.6900, 8.0000),
            via = listOf(
                LatLng(53.68442552008205, 8.026188181851579),
                LatLng(53.684758103906816, 8.027591485261341),
                LatLng(53.68426991325801, 8.03027190151226),
                LatLng(53.68366786285469, 8.032813226495673),
                LatLng(53.682307995589746, 8.035652370546673),
                LatLng(53.68635761439309, 8.037761461836245),
                LatLng(53.69191912175027, 8.044052238929034)
            ),
            matchRadiusM = 5_000.0
        ),

        BridgeRule(
            harbor = LatLng(53.77485124022699, 7.867251072413869),
            via = listOf(
                LatLng(53.768564770764016, 7.863543835115352) 
            ),
            matchRadiusM = 5_000.0
        )
    )

    private fun bridgeForPoint(p: LatLng): List<LatLng> {
        for (rule in bridgeRules) {
            val d = GridConfig.approxMeters(p.latitude, p.longitude, rule.harbor.latitude, rule.harbor.longitude)
            if (d <= rule.matchRadiusM) return rule.via
        }
        return emptyList()
    }

    fun calculateRoute(start: LatLng, end: LatLng): List<LatLng> {
        if (!SeaMask.ready) {
            RouterLog.w(TAG, "SeaMask not ready, falling back to straight line ${start} → ${end}")
            return listOf(start, end)
        }
        val raw = pathfinder.findPath(start, end) ?: run {
            RouterLog.w(TAG, "A* found no path ${start} → ${end} — fallback to straight line")
            return listOf(start, end)
        }
        val smoothed = PathSmoother.smooth(raw)
        BuoyValidator.validate(smoothed) 
        return smoothed
    }

    fun calculateSegmentedRoute(
        start: LatLng,
        end: LatLng,
        draft: Double,
        margin: Double,
        currentTime: LocalDateTime? = null,
        tideEvents: List<TideEvent> = emptyList()
    ): List<RouteSegment> {

        val startBridges = bridgeForPoint(start) 
        val endBridges = bridgeForPoint(end).reversed() 
        val viaPoints = buildList {
            add(start)
            addAll(startBridges)

            for (p in endBridges) {
                if (none { it == p }) add(p)
            }
            add(end)
        }
        if (viaPoints.size > 2) {
            RouterLog.d(TAG, "Routing via bridge waypoint(s): ${viaPoints.drop(1).dropLast(1)}")
            return calculateMultiStopRouteInternal(viaPoints, draft, margin, currentTime, tideEvents)
        }

        val route = calculateRoute(start, end)
        if (route.size < 2) return emptyList()

        val tideOffset = if (currentTime != null && tideEvents.isNotEmpty()) {
            calculateTideOffset(currentTime, tideEvents)
        } else {
            0.0
        }

        return classifyRouteByDepth(route, draft, margin, tideOffset)
    }

    private fun calculateMultiStopRouteInternal(
        points: List<LatLng>,
        draft: Double,
        margin: Double,
        currentTime: LocalDateTime?,
        tideEvents: List<TideEvent>
    ): List<RouteSegment> {
        val tideOffset = if (currentTime != null && tideEvents.isNotEmpty()) {
            calculateTideOffset(currentTime, tideEvents)
        } else {
            0.0
        }
        val all = mutableListOf<RouteSegment>()
        for (i in 0 until points.size - 1) {
            val route = calculateRoute(points[i], points[i + 1])
            if (route.size >= 2) {
                all.addAll(classifyRouteByDepth(route, draft, margin, tideOffset))
            }
        }
        return mergeAdjacentSegments(all)
    }

    fun calculateMultiStopRoute(
        points: List<LatLng>,
        draft: Double,
        margin: Double,
        currentTime: LocalDateTime? = null,
        tideEvents: List<TideEvent> = emptyList()
    ): List<RouteSegment> {
        if (points.size < 2) return emptyList()
        val all = mutableListOf<RouteSegment>()
        for (i in 0 until points.size - 1) {
            all.addAll(calculateSegmentedRoute(points[i], points[i + 1], draft, margin, currentTime, tideEvents))
        }
        return mergeAdjacentSegments(all)
    }

    fun depthSamplesAlongRoute(
        route: List<LatLng>,
        tideOffset: Double,
        draft: Double,
        margin: Double,
        spacingM: Double = 500.0
    ): List<DepthPoint> {
        if (route.size < 2) return emptyList()
        val samples = mutableListOf<DepthPoint>()
        for (i in 0 until route.size - 1) {
            val a = route[i]
            val b = route[i + 1]
            val len = GridConfig.approxMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            val steps = kotlin.math.max(1, (len / spacingM).toInt())
            for (s in 0..steps) {
                val t = s.toDouble() / steps
                val lat = a.latitude + (b.latitude - a.latitude) * t
                val lon = a.longitude + (b.longitude - a.longitude) * t
                val chart = SeaMask.depthAtLatLng(lat, lon)
                val current = chart + tideOffset
                samples.add(DepthPoint(LatLng(lat, lon), current, classifyDepth(current, draft, margin)))
            }
        }
        return samples
    }

    fun calculateTideOffset(time: LocalDateTime, events: List<TideEvent>): Double {
        if (events.isEmpty()) return 0.0

        val sorted = events.mapNotNull { event ->
            try {
                val cleanTs = event.timestamp
                    .replace("T", " ")
                    .replace(Regex("Z$"), "")
                    .replace(Regex("\\+\\d{2}:\\d{2}$"), "")
                    .replace(Regex("\\+\\d{2}$"), "")
                    .trim()

                val dt = try {
                    LocalDateTime.parse(cleanTs, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                } catch (_: Exception) {
                    LocalDateTime.parse(cleanTs, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                }
                event to dt
            } catch (_: Exception) { null }
        }.sortedBy { it.second }

        if (sorted.isEmpty()) return 0.0

        val nextIndex = sorted.indexOfFirst { it.second.isAfter(time) }
        if (nextIndex == -1) return sorted.last().first.value ?: 0.0
        if (nextIndex == 0) return sorted.first().first.value ?: 0.0

        val prev = sorted[nextIndex - 1]
        val next = sorted[nextIndex]

        return RuleOfTwelfths.calculateWaterLevel(
            prev.second, prev.first.value ?: 0.0,
            next.second, next.first.value ?: 0.0,
            time
        )
    }

    fun classifyDepth(depth: Double, draft: Double, margin: Double): SegmentType = when {
        depth >= (draft + margin) -> SegmentType.SAFE
        depth >= draft -> SegmentType.CRITICAL
        else -> SegmentType.NO_GO
    }

    private fun classifyRouteByDepth(
        route: List<LatLng>,
        draft: Double,
        margin: Double,
        tideOffset: Double
    ): List<RouteSegment> {
        if (route.size < 2) return emptyList()

        val dense = mutableListOf<LatLng>()
        dense.add(route.first())
        for (i in 0 until route.size - 1) {
            val a = route[i]
            val b = route[i + 1]
            val len = GridConfig.approxMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            val steps = kotlin.math.max(1, (len / DEPTH_SAMPLE_M).toInt())
            for (s in 1..steps) {
                val t = s.toDouble() / steps
                dense.add(LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t))
            }
        }

        val perSample = dense.map { p ->
            val chart = SeaMask.depthAtLatLng(p.latitude, p.longitude)
            val current = chart + tideOffset
            Triple(p, current, classifyDepth(current, draft, margin))
        }

        val segments = mutableListOf<RouteSegment>()
        var curPoints = mutableListOf<LatLng>(perSample[0].first)
        var curType = perSample[0].third
        var curMinDepth = perSample[0].second

        for (i in 1 until perSample.size) {
            val (p, d, t) = perSample[i]
            if (t == curType) {
                curPoints.add(p)
                if (d < curMinDepth) curMinDepth = d
            } else {
                curPoints.add(p) 
                segments.add(RouteSegment(curPoints, curType, curMinDepth))
                curPoints = mutableListOf(p) 
                curType = t
                curMinDepth = d
            }
        }
        segments.add(RouteSegment(curPoints, curType, curMinDepth))
        return mergeAdjacentSegments(segments)
    }

    private fun mergeAdjacentSegments(segments: List<RouteSegment>): List<RouteSegment> {
        if (segments.isEmpty()) return emptyList()
        val merged = mutableListOf<RouteSegment>()
        var currentPoints = segments[0].points.toMutableList()
        var currentType = segments[0].type
        var currentMinDepth = segments[0].minDepth

        for (i in 1 until segments.size) {
            if (segments[i].type == currentType) {
                currentPoints.addAll(segments[i].points.drop(1))
                currentMinDepth = kotlin.math.min(currentMinDepth, segments[i].minDepth)
            } else {
                merged.add(RouteSegment(currentPoints, currentType, currentMinDepth))
                currentPoints = segments[i].points.toMutableList()
                currentType = segments[i].type
                currentMinDepth = segments[i].minDepth
            }
        }
        merged.add(RouteSegment(currentPoints, currentType, currentMinDepth))
        return merged
    }
}
