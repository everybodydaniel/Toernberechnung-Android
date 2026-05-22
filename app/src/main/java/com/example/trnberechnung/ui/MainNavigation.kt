package com.example.trnberechnung.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.RoutePlanningViewModel
import com.example.trnberechnung.viewmodel.TideViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector?) {
    object MapRoute : Screen("map_route", "Karte", Icons.Default.LocationOn)
    object Weather : Screen("weather", "Wetter", null) 
    object Tide : Screen("tides", "Gezeiten", Icons.Default.DateRange)
    object Crew : Screen("crew", "Crew", Icons.Default.Person)
    object Logbook : Screen("logbook", "Logbuch", Icons.Default.List)
    object Settings : Screen("settings", "Einstellungen", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.MapRoute,
    Screen.Weather,
    Screen.Tide,
    Screen.Crew,
    Screen.Logbook
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: TideViewModel) {
    val navController = rememberNavController()
    var expandedMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = NauticalBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "Logo",
                            tint = NauticalPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "TÖRNCALCULATOR",
                            fontWeight = FontWeight.ExtraBold,
                            color = NauticalTextPrimary,
                            fontSize = 20.sp,
                            letterSpacing = 1.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = NauticalPrimary)
                    }
                    Box {
                        IconButton(onClick = { expandedMenu = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = NauticalTextSecondary)
                        }
                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false },
                            containerColor = NauticalSurface
                        ) {
                            DropdownMenuItem(
                                text = { Text("Einstellungen", color = NauticalTextPrimary) },
                                onClick = {
                                    expandedMenu = false
                                    navController.navigate(Screen.Settings.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = NauticalPrimary)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NauticalBackground
                ),
                modifier = Modifier
                    .shadow(elevation = 4.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(NauticalBackground, NauticalSurface)
                        )
                    )
            )
        },
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.MapRoute.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.MapRoute.route) {

                val routeViewModel: RoutePlanningViewModel = viewModel()
                RoutePlanningScreen(viewModel, routeViewModel)
            }
            composable(Screen.Weather.route) {

                WeatherOverlayScreen(viewModel)
            }
            composable(Screen.Tide.route) {

                TideGraphScreen(viewModel)
            }
            composable(Screen.Crew.route) {

                CrewScreen(viewModel)
            }
            composable(Screen.Logbook.route) {

                LogbookScreen(viewModel)
            }
            composable(Screen.Settings.route) {

                DashboardScreen { 
                    navController.navigate(Screen.MapRoute.route)
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    NavigationBar(
        containerColor = NauticalBottomBar,
        tonalElevation = 0.dp,
        modifier = Modifier.shadow(8.dp)
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        bottomNavItems.forEach { screen ->
            val isSelected = currentRoute == screen.route
            NavigationBarItem(
                icon = { 
                    if (screen.icon != null) {
                        Icon(
                            screen.icon, 
                            contentDescription = screen.title,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {

                        Text(
                            "⛅",
                            fontSize = 20.sp
                        )
                    }
                },
                label = { 
                    Text(
                        screen.title, 
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp
                    ) 
                },
                selected = isSelected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NauticalPrimary,
                    selectedTextColor = NauticalPrimary,
                    unselectedIconColor = NauticalTextSecondary,
                    unselectedTextColor = NauticalTextSecondary,
                    indicatorColor = NauticalSurfaceVariant
                )
            )
        }
    }
}
