package com.example.trnberechnung.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.trnberechnung.routing.v2.SeaMask
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.trnberechnung.TideNodeApplication
import com.example.trnberechnung.mapplanning.AndroidRouteAssessmentProvider
import com.example.trnberechnung.mapplanning.CatalogFairwayRouteResolver
import com.example.trnberechnung.mapplanning.RoutePlanningViewModel
import com.example.trnberechnung.mapplanning.RoutePlanningViewModelFactory
import com.example.trnberechnung.mapplanning.SimpleTidalCurrentProvider
import com.example.trnberechnung.nauti.GeminiNautiClient
import com.example.trnberechnung.repository.TideRepository
import com.example.trnberechnung.navigation.ActiveVoyageState
import com.example.trnberechnung.navigation.SensorHeadingProvider
import com.example.trnberechnung.navigation.VoyageServiceController
import com.example.trnberechnung.ui.components.GlassIconButton
import com.example.trnberechnung.ui.components.TideNodeAppHeader
import com.example.trnberechnung.ui.components.TideNodeBlue
import com.example.trnberechnung.ui.components.TideNodeInk
import com.example.trnberechnung.ui.components.tideNodeAppHeaderHeight
import com.example.trnberechnung.ui.components.tideNodeGlass
import com.example.trnberechnung.ui.map.MapTabScreen
import com.example.trnberechnung.ui.navigation.FullScreenNavigationScreen
import com.example.trnberechnung.ui.theme.NauticalBackground
import com.example.trnberechnung.viewmodel.CrewspaceViewModel
import com.example.trnberechnung.viewmodel.CrewspaceViewModelFactory
import com.example.trnberechnung.viewmodel.NautiViewModel
import com.example.trnberechnung.viewmodel.TideViewModel

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
) {
    data object MapRoute : Screen("map_route", "Karte", Icons.Default.Map)

    data object Revier : Screen("revier", "Wetter", Icons.Default.Cloud)

    data object Crew : Screen("crew", "Crewspace", Icons.Default.People)

    data object Logbook : Screen("logbook", "Logbuch", Icons.AutoMirrored.Filled.MenuBook)

    data object Settings : Screen("settings", "Einstellungen", Icons.Default.Map)

    data object Navigation : Screen("navigation", "Navigation", Icons.Default.Map)
}

val bottomNavItems = listOf(Screen.MapRoute, Screen.Revier, Screen.Crew, Screen.Logbook)

@Composable
fun MainAppScreen(
    viewModel: TideViewModel,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as TideNodeApplication
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Screen.MapRoute.route
    val isMainTab = currentRoute in bottomNavItems.map(Screen::route)

    val crewspaceViewModel: CrewspaceViewModel =
        viewModel(
            factory =
                remember(application) {
                    val db = application.database
                    CrewspaceViewModelFactory(
                        TideRepository(
                            db.tideDao(),
                            db.logbookDao(),
                            db.crewMemberDao(),
                            db.checklistDao(),
                            db.plannerEventDao(),
                        ),
                    )
                },
        )
    val nautiViewModel: NautiViewModel =
        viewModel(
            factory =
                remember(application) {
                    NautiViewModel.Factory(
                        application.nautiConversationRepository,
                        GeminiNautiClient(),
                    )
                },
        )
    val routePlanningViewModel: RoutePlanningViewModel =
        viewModel(
            factory =
                remember(viewModel) {
                    val assessmentProvider = AndroidRouteAssessmentProvider(
                        tideStationProvider = { viewModel.allStations.value },
                        chartDepthProvider = { point ->
                            if (SeaMask.isReady.value) SeaMask.depthAtLatLng(point.latitude, point.longitude) else null
                        },
                        fairwayRouteResolver = CatalogFairwayRouteResolver,
                    )
                    RoutePlanningViewModelFactory(
                        routeAssessmentProvider = assessmentProvider,
                        metricRouteResolver = CatalogFairwayRouteResolver,
                        currentVectorProvider = SimpleTidalCurrentProvider { viewModel.allStations.value }
                    )
                },
        )
    val headingProvider =
        remember(application) {
            SensorHeadingProvider(
                context = context,
                latestLocation = {
                    application.navigationLocationProvider.latestFix.value
                },
            )
        }

    val activeVoyageState by application.activeVoyageManager.state.collectAsState()

    LaunchedEffect(Unit) {
        application.activeVoyageManager.restoreActiveVoyage()
    }

    val adaptiveLayout = currentAdaptiveLayout()
    val isLandscape = adaptiveLayout.isLandscape
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingStart =
        if (adaptiveLayout.isTablet) {
            safeDrawingPadding.calculateStartPadding(layoutDirection)
        } else {
            0.dp
        }
    val safeDrawingEnd =
        if (adaptiveLayout.isTablet) {
            safeDrawingPadding.calculateEndPadding(layoutDirection)
        } else {
            0.dp
        }
    val headerHeight = tideNodeAppHeaderHeight(adaptiveLayout)
    val topSystemInset =
        if (adaptiveLayout.isTablet) {
            safeDrawingPadding.calculateTopPadding()
        } else {
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        }
    val topClearance = topSystemInset + headerHeight
    val bottomSystemInset =
        if (adaptiveLayout.isTablet) {
            safeDrawingPadding.calculateBottomPadding()
        } else {
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        }
    val bottomOverlayClearance =
        if (adaptiveLayout.isTablet) {
            bottomSystemInset + bottomNavigationHeight(adaptiveLayout) + bottomNavigationBottomMargin(adaptiveLayout)
        } else if (isLandscape) {
            54.dp
        } else {
            88.dp
        }
    val mapBottomClearance =
        if (adaptiveLayout.isTablet) {
            bottomOverlayClearance
        } else {
            bottomSystemInset + bottomOverlayClearance
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.MapRoute.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Screen.MapRoute.route) {
                MapTabScreen(
                    tideViewModel = viewModel,
                    planningViewModel = routePlanningViewModel,
                    nautiViewModel = nautiViewModel,
                    activeVoyageManager = application.activeVoyageManager,
                    locationProvider = application.navigationLocationProvider,
                    topOverlayClearance = topClearance,
                    bottomOverlayClearance = mapBottomClearance,
                    onOpenWeather = {
                        navController.navigateMainTab(Screen.Revier.route)
                    },
                    onOpenNavigation = {
                        navController.navigate(Screen.Navigation.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Screen.Revier.route) {
                MainTabContent {
                    WeatherScreen(
                        viewModel = viewModel,
                        topOverlayClearance = topClearance,
                        bottomOverlayClearance = bottomOverlayClearance,
                    )
                }
            }
            composable(Screen.Crew.route) {
                MainTabContent {
                    CrewspaceScreen(
                        viewModel = crewspaceViewModel,
                        topOverlayClearance = topClearance,
                        bottomOverlayClearance = bottomOverlayClearance,
                    )
                }
            }
            composable(Screen.Logbook.route) {
                MainTabContent {
                    LogbookScreen(
                        viewModel = viewModel,
                        topOverlayClearance = topClearance,
                        bottomOverlayClearance = bottomOverlayClearance,
                    )
                }
            }
            composable(Screen.Settings.route) {
                SettingsDestination(
                    onBack = navController::popBackStack,
                ) {
                    DashboardScreen(
                        appPreferences = application.appPreferences,
                        onToggleDarkMode = onToggleDarkMode,
                        onReplayOnboarding = onReplayOnboarding,
                    )
                }
            }
            composable(Screen.Navigation.route) {
                if (activeVoyageState !is ActiveVoyageState.Active) {
                    LaunchedEffect(Unit) {
                        navController.navigateMainTab(Screen.MapRoute.route)
                    }
                }
                FullScreenNavigationScreen(
                    activeVoyageManager = application.activeVoyageManager,
                    locationProvider = application.navigationLocationProvider,
                    headingProvider = headingProvider,
                    onMinimize = {
                        navController.navigateMainTab(Screen.MapRoute.route)
                    },
                    onVoyageFinished = {
                        VoyageServiceController.stop(context)
                        navController.navigateMainTab(Screen.MapRoute.route)
                    },
                )
            }
        }

        if (isMainTab) {
            TideNodeAppHeader(
                onSettings = {
                    navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                },
                // Map and Revier draw their own full-bleed background behind the header - the
                // nautical chart and the blue gradient - so the wordmark goes white there.
                onColoredBackground =
                    currentRoute == Screen.MapRoute.route || currentRoute == Screen.Revier.route,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .testTag("global_app_header"),
            )

            if (adaptiveLayout.isTablet) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(start = safeDrawingStart, end = safeDrawingEnd),
                ) {
                    TideNodeBottomNavigation(
                        navController = navController,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = adaptiveLayout.horizontalScreenPadding)
                                .widthIn(max = adaptiveLayout.compactContentMaxWidth)
                                .fillMaxWidth(),
                    )
                }
            } else {
                TideNodeBottomNavigation(
                    navController = navController,
                    modifier =
                        Modifier
                            .align(if (isLandscape && currentRoute == Screen.MapRoute.route) Alignment.BottomStart else Alignment.BottomCenter)
                            .then(
                                if (isLandscape && currentRoute == Screen.MapRoute.route) {
                                    Modifier.fillMaxWidth(0.48f).widthIn(max = 440.dp)
                                } else {
                                    Modifier.fillMaxWidth()
                                },
                            ),
                )
            }

            if (
                currentRoute == Screen.MapRoute.route &&
                activeVoyageState is ActiveVoyageState.Active
            ) {
                val onResumeVoyage = {
                    (context as? Activity)?.let(VoyageServiceController::startFromVisibleActivity)
                    navController.navigate(Screen.Navigation.route) {
                        launchSingleTop = true
                    }
                    Unit
                }
                if (adaptiveLayout.isTablet) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(start = safeDrawingStart, end = safeDrawingEnd),
                    ) {
                        ActiveVoyageResumePill(
                            onClick = onResumeVoyage,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = adaptiveLayout.horizontalScreenPadding)
                                    .widthIn(max = adaptiveLayout.overlayMaxWidth)
                                    .fillMaxWidth()
                                    .padding(bottom = mapBottomClearance + 8.dp),
                        )
                    }
                } else {
                    ActiveVoyageResumePill(
                        onClick = onResumeVoyage,
                        modifier =
                            Modifier
                                .align(if (isLandscape) Alignment.BottomStart else Alignment.BottomCenter)
                                .then(
                                    if (isLandscape) {
                                        Modifier.fillMaxWidth(0.48f).widthIn(max = 440.dp)
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(
                                    start = 28.dp,
                                    end = 28.dp,
                                    bottom = mapBottomClearance + 8.dp,
                                ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTabContent(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        content()
    }
}

@Composable
private fun SettingsDestination(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val adaptiveLayout = currentAdaptiveLayout()
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val topInset =
        if (adaptiveLayout.isTablet) {
            safeDrawingPadding.calculateTopPadding()
        } else {
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        }
    val settingsTopBarHeight = if (adaptiveLayout.isTablet) 72.dp else 60.dp
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (adaptiveLayout.isTablet) {
                            Modifier.padding(
                                start = safeDrawingPadding.calculateStartPadding(layoutDirection),
                                end = safeDrawingPadding.calculateEndPadding(layoutDirection),
                                bottom = safeDrawingPadding.calculateBottomPadding(),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(top = topInset + settingsTopBarHeight),
        ) {
            content()
        }
        GlassIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Zurück",
            onClick = onBack,
            size = if (adaptiveLayout.isTablet) 56.dp else 48.dp,
            iconSize = if (adaptiveLayout.isTablet) TabletLayoutTokens.StandardIconSize else 23.dp,
            modifier =
                Modifier
                    .then(
                        if (adaptiveLayout.isTablet) {
                            Modifier.padding(
                                start =
                                    safeDrawingPadding.calculateStartPadding(layoutDirection) +
                                        adaptiveLayout.horizontalScreenPadding,
                                top = topInset + 8.dp,
                            )
                        } else {
                            Modifier.padding(start = 16.dp, top = topInset + 8.dp)
                        },
                    )
                    .testTag("settings_back"),
        )
    }
}

private fun bottomNavigationHeight(layout: AdaptiveLayout): Dp =
    when {
        !layout.isTablet && layout.isLandscape -> 50.dp
        !layout.isTablet -> 72.dp
        layout.isLandscape -> 64.dp
        else -> 84.dp
    }

private fun bottomNavigationBottomMargin(layout: AdaptiveLayout): Dp =
    when {
        !layout.isTablet && layout.isLandscape -> 4.dp
        !layout.isTablet -> 10.dp
        layout.isLandscape -> 8.dp
        else -> 12.dp
    }

@Composable
private fun TideNodeBottomNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val adaptiveLayout = currentAdaptiveLayout()
    val isLandscape = adaptiveLayout.isLandscape
    val bottomInset =
        if (adaptiveLayout.isTablet) {
            WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        } else {
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        }
    val navHeight = bottomNavigationHeight(adaptiveLayout)
    val itemHeight =
        when {
            !adaptiveLayout.isTablet && isLandscape -> 44.dp
            !adaptiveLayout.isTablet -> 62.dp
            isLandscape -> 56.dp
            else -> 72.dp
        }
    val outerHorizontalPadding =
        when {
            adaptiveLayout.isTablet -> 0.dp
            isLandscape -> 10.dp
            else -> 18.dp
        }
    val navCornerRadius =
        when {
            !adaptiveLayout.isTablet && isLandscape -> 24.dp
            !adaptiveLayout.isTablet -> 34.dp
            isLandscape -> 30.dp
            else -> 40.dp
        }
    val itemCornerRadius =
        when {
            !adaptiveLayout.isTablet && isLandscape -> 20.dp
            !adaptiveLayout.isTablet -> 28.dp
            isLandscape -> 26.dp
            else -> 34.dp
        }
    val innerVerticalPadding =
        when {
            !adaptiveLayout.isTablet && isLandscape -> 3.dp
            !adaptiveLayout.isTablet -> 5.dp
            isLandscape -> 4.dp
            else -> 6.dp
        }
    val iconSize = if (adaptiveLayout.isTablet) 30.dp else 25.dp
    val labelSize = if (adaptiveLayout.isTablet) 13.sp else 11.sp

    Row(
        modifier =
            modifier
                .padding(
                    start = outerHorizontalPadding,
                    end = outerHorizontalPadding,
                    bottom = bottomInset + bottomNavigationBottomMargin(adaptiveLayout),
                )
                .fillMaxWidth()
                .height(navHeight)
                .tideNodeGlass(cornerRadius = navCornerRadius, elevation = 14.dp, alpha = 0.80f)
                .selectableGroup()
                .padding(
                    horizontal = if (adaptiveLayout.isTablet) 12.dp else 8.dp,
                    vertical = innerVerticalPadding,
                )
                .testTag("global_bottom_navigation"),
        horizontalArrangement = Arrangement.spacedBy(if (adaptiveLayout.isTablet) 4.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bottomNavItems.forEach { screen ->
            val selected = currentRoute == screen.route
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(itemHeight)
                        .background(
                            color =
                                if (selected) {
                                    TideNodeBlue.copy(alpha = 0.12f)
                                } else {
                                    Color.Transparent
                                },
                            shape = RoundedCornerShape(itemCornerRadius),
                        )
                        .selectable(
                            selected = selected,
                            onClick = { navController.navigateMainTab(screen.route) },
                            role = Role.Tab,
                        )
                        .testTag("nav_${screen.route}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = screen.icon,
                    contentDescription = null,
                    tint = if (selected) TideNodeBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(iconSize).testTag("nav_icon_${screen.route}"),
                )
                Spacer(Modifier.height(if (adaptiveLayout.isTablet) 3.dp else 2.dp))
                Text(
                    text = screen.title,
                    color = if (selected) TideNodeBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = labelSize,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    modifier = Modifier.testTag("nav_text_${screen.route}")
                )
            }
        }
    }
}

@Composable
private fun ActiveVoyageResumePill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .tideNodeGlass(cornerRadius = 25.dp, elevation = 10.dp, alpha = 0.88f)
                .clickable(
                    onClick = onClick,
                    onClickLabel = "Aktive Navigation fortsetzen",
                    role = Role.Button,
                )
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .testTag("active_voyage_resume"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Map, null, tint = TideNodeBlue)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Aktive Fahrt",
                color = TideNodeInk,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "Navigation fortsetzen",
                color = Color(0xFF62666C),
                fontSize = 12.sp,
            )
        }
        Text(
            "›",
            color = TideNodeBlue,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

private fun NavHostController.navigateMainTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

