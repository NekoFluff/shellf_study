package com.crazyfluff.shellfstudy.core.database

import androidx.room.TypeConverter
import com.crazyfluff.shellfstudy.core.network.MeaningData
import com.crazyfluff.shellfstudy.core.network.ReadingData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun meaningsToJson(meanings: List<MeaningData>): String = json.encodeToString(meanings)

    @TypeConverter
    fun meaningsFromJson(value: String): List<MeaningData> =
        json.decodeFromString(value)

    @TypeConverter
    fun readingsToJson(readings: List<ReadingData>): String = json.encodeToString(readings)

    @TypeConverter
    fun readingsFromJson(value: String): List<ReadingData> =
        json.decodeFromString(value)
}
