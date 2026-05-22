package com.example.trnberechnung.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crew_members")
data class CrewMember(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val rank: String,           
    val isOnBoard: Boolean,
    val medicalNote: String,    
    val emergencyPhone: String  
)
