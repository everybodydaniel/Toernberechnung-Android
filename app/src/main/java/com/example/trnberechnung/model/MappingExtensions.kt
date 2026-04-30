package com.example.trnberechnung.model

// Import der Datenbank Entity
import com.example.trnberechnung.database.TideEntity

// Import der API Datenklassen
import com.example.trnberechnung.dto.FeatureDto
import com.example.trnberechnung.dto.HighLowWaterDto
import com.example.trnberechnung.dto.WaterLevelItemDto

// Wandelt ein BSH HW/NW-Ereignis in ein TideEvent um
// BSH liefert Werte in cm relativ zum Pegelnullpunkt (PN)
// Wir konvertieren zu Metern und machen den Wert relativ zum Kartendatum (LAT)
fun HighLowWaterDto.toModel(latOffsetM: Double = 0.0): TideEvent {
    val rawValueM = forecastValue?.toDoubleOrNull()?.let { it / 100.0 }
    return TideEvent(
        timestamp = eventTimestamp,
        type = event,
        value = rawValueM?.let { it - latOffsetM } // Jetzt relativ zu LAT!
    )
}

// Wandelt eine BSH-Station in ein TideStationData um
fun WaterLevelItemDto.toModel(): TideStationData {
    // LAT relativ zu Pegelnull in Metern
    val latOffsetM = (chartdatum_relative_to_gaugezero?.toDoubleOrNull() ?: 0.0) / 100.0
    
    return TideStationData(
        area = area,
        region = region,
        latitude = latitude,
        longitude = longitude,
        waterLevel = water_level?.toDoubleOrNull()?.let { it / 100.0 },
        meanHighWater = mean_high_water?.toDoubleOrNull()?.let { it / 100.0 },
        meanLowWater = mean_low_water?.toDoubleOrNull()?.let { it / 100.0 },
        gaugeLabel = gauge_label,
        gaugeZeroNhn = gaugezero_relative_to_nhn?.toDoubleOrNull()?.let { it / 100.0 },
        chartDatumGauge = latOffsetM,
        forecastTimestamp = forecast_timestamp,
        // Nur Events mit forecast_value (nicht null/leer) konvertieren
        events = high_water_low_water
            .filter { !it.forecastValue.isNullOrBlank() && it.forecastValue.toDoubleOrNull() != null }
            .map { it.toModel(latOffsetM) }
    )
}

// Wandelt ein Feature aus der API um
fun FeatureDto.toModel(): TideStationData {
    return properties.toModel()
}

// App-Model → Datenbank Entity
fun TideStationData.toEntity(): TideEntity {
    return TideEntity(
        area = area,
        region = region,
        latitude = latitude,
        longitude = longitude,
        waterLevel = waterLevel,
        meanHighWater = meanHighWater,
        meanLowWater = meanLowWater,
        forecastTimestamp = forecastTimestamp
    )
}

// Datenbank Entity → App-Model
fun TideEntity.toModel(): TideStationData {
    return TideStationData(
        area = area,
        region = region,
        latitude = latitude,
        longitude = longitude,
        waterLevel = waterLevel,
        meanHighWater = meanHighWater,
        meanLowWater = meanLowWater,
        forecastTimestamp = forecastTimestamp,
        events = emptyList()
    )
}