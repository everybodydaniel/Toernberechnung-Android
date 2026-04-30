package com.example.trnberechnung.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// DAO = Data Access Object
// Diese Schnittstelle regelt den Zugriff auf die Datenbank
@Dao
interface TideDao {

    // Fügt eine Liste von Daten in die Datenbank ein
    // REPLACE bedeutet: vorhandene gleiche Einträge werden überschrieben
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<TideEntity>)

    // Holt alle Datensätze aus der Tabelle tide
    @Query("SELECT * FROM tide")
    suspend fun getAll(): List<TideEntity>

    // Löscht alle Datensätze aus der Tabelle tide
    @Query("DELETE FROM tide")
    suspend fun deleteAll()
}