package com.example.trnberechnung.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logbook_entries")
data class LogbookEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val routeDesc: String,
    val distance: String,
    val duration: String,
    val status: String,
    val details: String // JSON string for details or simplified text
)
