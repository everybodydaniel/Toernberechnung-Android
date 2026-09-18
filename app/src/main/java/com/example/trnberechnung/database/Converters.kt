package com.example.trnberechnung.database

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromTimestamp(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDate?): String? {
        return date?.toString()
    }

    @TypeConverter
    fun participantIdsToStorage(ids: List<Int>?): String =
        ids.orEmpty().filter { it > 0 }.distinct().sorted().joinToString(separator = ",")

    @TypeConverter
    fun participantIdsFromStorage(value: String?): List<Int> =
        value.orEmpty()
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .filter { it > 0 }
            .distinct()
            .sorted()
}
