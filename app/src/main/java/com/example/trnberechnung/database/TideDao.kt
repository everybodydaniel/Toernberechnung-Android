package com.example.trnberechnung.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TideDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<TideEntity>)

    @Query("SELECT * FROM tide")
    suspend fun getAll(): List<TideEntity>

    @Query("DELETE FROM tide")
    suspend fun deleteAll()
}