package com.example.trnberechnung.model

data class TideEvent(
    val timestamp: String,   
    val type: String,        
    val value: Double?       
)