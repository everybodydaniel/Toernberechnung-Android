package com.example.trnberechnung.model

import org.junit.Test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import com.example.trnberechnung.dto.HighLowWaterDto
import com.example.trnberechnung.dto.WaterLevelItemDto
import com.example.trnberechnung.database.TideEntity
import com.example.trnberechnung.model.TideStationData
import com.example.trnberechnung.model.toModel
import com.example.trnberechnung.model.toEntity

class MappingExtensionsTest {

    @Test
    fun `HighLowWaterDto toModel with forecastValue converts cm to m`() {
        val dto = HighLowWaterDto(event = "HW", eventTimestamp = "2024-01-01T12:00", forecastValue = "450")
        val model = dto.toModel()
        model.value shouldBe 4.5
    }

    @Test
    fun `HighLowWaterDto toModel with latOffset applies offset`() {
        val dto = HighLowWaterDto(event = "HW", eventTimestamp = "2024-01-01T12:00", forecastValue = "450")
        val model = dto.toModel(latOffsetM = 1.0)
        model.value shouldBe 3.5
    }

    @Test
    fun `HighLowWaterDto toModel null forecastValue gives null value`() {
        val dto = HighLowWaterDto(event = "HW", eventTimestamp = "2024-01-01T12:00", forecastValue = null)
        val model = dto.toModel()
        model.value.shouldBeNull()
    }

    @Test
    fun `WaterLevelItemDto toModel converts correctly`() {
        val dto = WaterLevelItemDto(
            area = "Norderney", region = "Ostfriesland",
            latitude = 53.7, longitude = 7.1,
            water_level = "250", mean_high_water = "300",
            mean_low_water = "100", gauge_label = "Pegel",
            gaugezero_relative_to_nhn = "-100",
            chartdatum_relative_to_gaugezero = "50",
            forecast_timestamp = "2024-01-01T12:00",
            high_water_low_water = listOf(
                HighLowWaterDto("HW", "2024-01-01T14:00", "450")
            )
        )
        val model = dto.toModel()
        model.waterLevel shouldBe 2.5
        model.meanHighWater shouldBe 3.0
        model.meanLowWater shouldBe 1.0
        model.events.size shouldBe 1
        model.events[0].value shouldBe 4.0
    }

    @Test
    fun `TideStationData toEntity preserves fields`() {
        val model = TideStationData(
            area = "Norderney", region = "Ostfriesland",
            latitude = 53.7, longitude = 7.1,
            waterLevel = 2.5, meanHighWater = 3.0, meanLowWater = 1.0,
            forecastTimestamp = "2024-01-01T12:00", events = emptyList()
        )
        val entity = model.toEntity()
        entity.area shouldBe "Norderney"
        entity.latitude shouldBe 53.7
        entity.waterLevel shouldBe 2.5
    }

    @Test
    fun `TideEntity toModel preserves fields and returns empty events`() {
        val entity = TideEntity(
            id = 1, area = "Norderney", region = "Ostfriesland",
            latitude = 53.7, longitude = 7.1,
            waterLevel = 2.5, meanHighWater = 3.0, meanLowWater = 1.0,
            forecastTimestamp = "2024-01-01T12:00"
        )
        val model = entity.toModel()
        model.area shouldBe "Norderney"
        model.waterLevel shouldBe 2.5
        model.events.size shouldBe 0
    }
}
