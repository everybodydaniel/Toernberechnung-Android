package com.example.trnberechnung.model

import android.content.Context
import android.content.SharedPreferences

class BoatProfileRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("boat_profile", Context.MODE_PRIVATE)

    // ── Identifikation ──
    var boatName: String
        get() = prefs.getString("boat_name", "") ?: ""
        set(value) = prefs.edit().putString("boat_name", value).apply()

    var boatType: String
        get() = prefs.getString("boat_type", "") ?: ""
        set(value) = prefs.edit().putString("boat_type", value).apply()

    var manufacturer: String
        get() = prefs.getString("manufacturer", "") ?: ""
        set(value) = prefs.edit().putString("manufacturer", value).apply()

    var buildYear: String
        get() = prefs.getString("build_year", "") ?: ""
        set(value) = prefs.edit().putString("build_year", value).apply()

    // ── Abmessungen ──
    var length: Float
        get() = prefs.getFloat("length", 0f)
        set(value) = prefs.edit().putFloat("length", value).apply()

    var beam: Float
        get() = prefs.getFloat("beam", 0f)
        set(value) = prefs.edit().putFloat("beam", value).apply()

    var draft: Float
        get() = prefs.getFloat("draft", 1.5f)
        set(value) = prefs.edit().putFloat("draft", value).apply()

    var displacement: Float
        get() = prefs.getFloat("displacement", 0f)
        set(value) = prefs.edit().putFloat("displacement", value).apply()

    // ── Betrieb ──
    var speed: Float
        get() = prefs.getFloat("speed", 5.0f)
        set(value) = prefs.edit().putFloat("speed", value).apply()
        
    var safetyMargin: Float
        get() = prefs.getFloat("safety_margin", 0.5f)
        set(value) = prefs.edit().putFloat("safety_margin", value).apply()

    var fuelCapacity: Float
        get() = prefs.getFloat("fuel_capacity", 0f)
        set(value) = prefs.edit().putFloat("fuel_capacity", value).apply()

    // ── Für Logbuch-Export ──
    fun getProfileSummary(): String {
        val sb = StringBuilder()
        if (boatName.isNotBlank()) sb.appendLine("Bootsname:    $boatName")
        if (boatType.isNotBlank()) sb.appendLine("Typ/Modell:   $boatType")
        if (manufacturer.isNotBlank()) sb.appendLine("Hersteller:   $manufacturer")
        if (buildYear.isNotBlank()) sb.appendLine("Baujahr:      $buildYear")
        if (length > 0) sb.appendLine("Länge:        ${"%.1f".format(length)} m")
        if (beam > 0) sb.appendLine("Breite:       ${"%.1f".format(beam)} m")
        sb.appendLine("Tiefgang:     ${"%.2f".format(draft)} m")
        if (displacement > 0) sb.appendLine("Verdrängung:  ${"%.0f".format(displacement)} kg")
        sb.appendLine("Geschw.:      ${"%.1f".format(speed)} kn")
        sb.appendLine("UKC-Marge:    ${"%.2f".format(safetyMargin)} m")
        if (fuelCapacity > 0) sb.appendLine("Kraftstoff:   ${"%.0f".format(fuelCapacity)} L")
        return sb.toString()
    }
}
