package com.example.trnberechnung.mapplanning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.time.Clock

class RoutePlanningViewModelFactory(
    private val routeAssessmentProvider: RouteAssessmentProvider,
    private val metricRouteResolver: FairwayRouteResolver? = null,
    private val routeGeometryProvider: RouteGeometryProvider = NauticalRouterV2GeometryProvider(),
    private val passageWindowScanner: PassageWindowScanner = PassageWindowScanner(),
    private val currentVectorProvider: CurrentVectorProvider? = null,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(RoutePlanningViewModel::class.java)) {
            "Unbekanntes ViewModel: ${modelClass.name}"
        }
        return RoutePlanningViewModel(
            routeGeometryProvider = routeGeometryProvider,
            routeAssessmentProvider = routeAssessmentProvider,
            metricRouteResolver = metricRouteResolver,
            passageWindowScanner = passageWindowScanner,
            currentVectorProvider = currentVectorProvider,
            clock = clock,
        ) as T
    }
}
