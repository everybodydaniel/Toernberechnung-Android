package com.example.trnberechnung.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.trnberechnung.model.LogbookEntry
import com.example.trnberechnung.model.LogbookDao
import com.example.trnberechnung.model.CrewMember
import com.example.trnberechnung.model.CrewMemberDao
import com.example.trnberechnung.model.ChecklistItem
import com.example.trnberechnung.model.ChecklistDao

@Database(entities = [TideEntity::class, LogbookEntry::class, CrewMember::class, ChecklistItem::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tideDao(): TideDao
    abstract fun logbookDao(): LogbookDao
    abstract fun crewMemberDao(): CrewMemberDao
    abstract fun checklistDao(): ChecklistDao
}