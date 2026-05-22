package com.example.trnberechnung.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tide")
data class TideEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val area: String,

    val region: String,

    val latitude: Double,

    val longitude: Double,

    val waterLevel: Double?,

    val meanHighWater: Double?,

    val meanLowWater: Double?,

    val forecastTimestamp: String
)