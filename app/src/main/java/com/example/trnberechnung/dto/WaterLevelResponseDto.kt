package com.example.trnberechnung.dto

data class WaterLevelResponseDto(
    val features: List<FeatureDto>
)

data class FeatureDto(
    val properties: WaterLevelItemDto
)