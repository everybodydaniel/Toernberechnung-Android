package com.example.trnberechnung

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.trnberechnung.model.OnboardingPreferences
import com.example.trnberechnung.navigation.ActiveVoyageState
import com.example.trnberechnung.repository.TideRepository
import com.example.trnberechnung.routing.v2.SeaMask
import com.example.trnberechnung.ui.MainAppScreen
import com.example.trnberechnung.ui.OnboardingScreen
import com.example.trnberechnung.ui.theme.TörnberechnungTheme
import com.example.trnberechnung.viewmodel.TideViewModel
import com.example.trnberechnung.viewmodel.TideViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.module.http.HttpRequestUtil

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. System-Agent setzen (für HttpURLConnection)
        val userAgent = "ToernberechnungApp/1.0 (https://example.com/toernberechnung; info@example.com) Android"
        System.setProperty("http.agent", userAgent)

        // 2. MapLibre initialisieren
        MapLibre.getInstance(this, null, WellKnownTileServer.MapLibre)

        // 3. OkHttp Interceptor für MapLibre (Fix für 403 Forbidden)
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .header("Referer", "https://example.com/toernberechnung")
                    .build()
                Log.d("MapLibre-HTTP", "Request to: ${request.url} with User-Agent and Referer")
                chain.proceed(request)
            }
            .build()
        HttpRequestUtil.setOkHttpClient(okHttpClient)

        lifecycleScope.launch(Dispatchers.IO) {
            SeaMask.build(applicationContext)
        }

        enableEdgeToEdge()

        val tideNodeApplication = application as TideNodeApplication
        val db = tideNodeApplication.database
        val appPreferences = tideNodeApplication.appPreferences

        val repository =
            TideRepository(
                db.tideDao(),
                db.logbookDao(),
                db.crewMemberDao(),
                db.checklistDao(),
                db.plannerEventDao(),
            )
        val factory = TideViewModelFactory(repository)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST,
            )
        }

        setContent {
            var isDarkMode by remember { mutableStateOf(appPreferences.isDarkMode) }

            TörnberechnungTheme(darkTheme = isDarkMode) {
                val onboardingPreferences = remember { OnboardingPreferences(applicationContext) }
                var onboardingCompleted by remember {
                    mutableStateOf(onboardingPreferences.isCompleted)
                }

                if (!onboardingCompleted) {
                    OnboardingScreen(
                        onCompleted = {
                            onboardingPreferences.markCompleted()
                            onboardingCompleted = true
                        }
                    )
                } else {
                    val viewModel: TideViewModel = viewModel(factory = factory)

                    // Weather and tides keep themselves current while visible. Warnings refresh
                    // once per foreground entry, never periodically and never during an active
                    // voyage. All work is bound to RESUMED; no warning polling runs in background.
                    val lifecycleOwner = LocalLifecycleOwner.current
                    LaunchedEffect(viewModel, lifecycleOwner) {
                        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            tideNodeApplication.activeVoyageManager.restoreActiveVoyage()
                            if (tideNodeApplication.activeVoyageManager.state.value !is ActiveVoyageState.Active) {
                                launch {
                                    tideNodeApplication.northSeaWarningRepository.refresh()
                                }
                            }
                            while (true) {
                                viewModel.loadData()
                                delay(WEATHER_REFRESH_INTERVAL_MILLIS)
                            }
                        }
                    }

                    MainAppScreen(
                        viewModel = viewModel,
                        onToggleDarkMode = { mode ->
                            isDarkMode = mode
                            appPreferences.isDarkMode = mode
                        },
                        onReplayOnboarding = {
                            onboardingCompleted = false
                        },
                    )
                }
            }
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 4102

        /**
         * How often the foreground app reloads nautical data.
         *
         * Ten minutes matches how coarsely the upstream sources move: Bright Sky publishes hourly
         * observations and the BSH forecast updates a few times a day. Polling faster would only
         * burn battery and quota.
         */
        private const val WEATHER_REFRESH_INTERVAL_MILLIS = 10 * 60 * 1000L
    }
}
