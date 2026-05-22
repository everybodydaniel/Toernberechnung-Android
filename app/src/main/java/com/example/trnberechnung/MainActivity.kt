package com.example.trnberechnung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.trnberechnung.database.AppDatabase
import com.example.trnberechnung.repository.TideRepository
import com.example.trnberechnung.routing.v2.SeaMask
import com.example.trnberechnung.ui.CalculatorScreen
import com.example.trnberechnung.ui.MapScreen
import com.example.trnberechnung.ui.StationDetailScreen
import com.example.trnberechnung.ui.theme.TörnberechnungTheme
import com.example.trnberechnung.viewmodel.TideViewModel
import com.example.trnberechnung.viewmodel.TideViewModelFactory
import org.maplibre.android.MapLibre
import com.example.trnberechnung.ui.MainAppScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(applicationContext)

        lifecycleScope.launch(Dispatchers.IO) {
            SeaMask.build(applicationContext)
        }

        enableEdgeToEdge()

        val db = Room.databaseBuilder(
            applicationContext,
            com.example.trnberechnung.database.AppDatabase::class.java,
            "tide_database"
        )
        .fallbackToDestructiveMigration()
        .build()

        val repository = TideRepository(db.tideDao(), db.logbookDao(), db.crewMemberDao(), db.checklistDao())
        val factory = TideViewModelFactory(repository)

        setContent {
            TörnberechnungTheme {
                val viewModel: TideViewModel = viewModel(factory = factory)
                LaunchedEffect(Unit) {
                    viewModel.loadData()
                }
                MainAppScreen(viewModel)
            }
        }
    }
}