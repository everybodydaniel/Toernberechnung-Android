package com.example.trnberechnung.model

import com.example.trnberechnung.database.TideEntity

import com.example.trnberechnung.dto.FeatureDto
import com.example.trnberechnung.dto.HighLowWaterDto
import com.example.trnberechnung.dto.WaterLevelItemDto

fun HighLowWaterDto.toModel(latOffsetM: Double = 0.0): TideEvent {
    val rawValueM = forecastValue?.toDoubleOrNull()?.let { it / 100.0 }
    return TideEvent(
        timestamp = eventTimestamp,
        type = event,
        value = rawValueM?.let { it - latOffsetM } 
    )
}

fun WaterLevelItemDto.toModel(): TideStationData {

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

        events = high_water_low_water
            .filter { !it.forecastValue.isNullOrBlank() && it.forecastValue.toDoubleOrNull() != null }
            .map { it.toModel(latOffsetM) }
    )
}

fun FeatureDto.toModel(): TideStationData {
    return properties.toModel()
}

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