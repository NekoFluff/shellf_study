package com.crazyfluff.shellfstudy.shared.database.pitchaccent

import androidx.room.TypeConverter
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PitchAccentConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun pitchAccentsToJson(pitchAccents: List<PitchAccent>): String = json.encodeToString(pitchAccents)

    @TypeConverter
    fun pitchAccentsFromJson(value: String): List<PitchAccent> = json.decodeFromString(value)
}
