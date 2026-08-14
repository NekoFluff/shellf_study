package com.crazyfluff.shellfstudy.shared.database

import androidx.room.TypeConverter
import com.crazyfluff.shellfstudy.shared.network.AuxiliaryMeaningData
import com.crazyfluff.shellfstudy.shared.network.ContextSentenceData
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.PronunciationAudioData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
import com.crazyfluff.shellfstudy.shared.network.SrsStageData
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
    fun auxiliaryMeaningsToJson(meanings: List<AuxiliaryMeaningData>): String = json.encodeToString(meanings)

    @TypeConverter
    fun auxiliaryMeaningsFromJson(value: String): List<AuxiliaryMeaningData> =
        json.decodeFromString(value)

    @TypeConverter
    fun readingsToJson(readings: List<ReadingData>): String = json.encodeToString(readings)

    @TypeConverter
    fun readingsFromJson(value: String): List<ReadingData> =
        json.decodeFromString(value)

    @TypeConverter
    fun srsStagesToJson(stages: List<SrsStageData>): String = json.encodeToString(stages)

    @TypeConverter
    fun srsStagesFromJson(value: String): List<SrsStageData> =
        json.decodeFromString(value)

    @TypeConverter
    fun contextSentencesToJson(sentences: List<ContextSentenceData>): String = json.encodeToString(sentences)

    @TypeConverter
    fun contextSentencesFromJson(value: String): List<ContextSentenceData> =
        json.decodeFromString(value)

    @TypeConverter
    fun pronunciationAudiosToJson(audios: List<PronunciationAudioData>): String = json.encodeToString(audios)

    @TypeConverter
    fun pronunciationAudiosFromJson(value: String): List<PronunciationAudioData> =
        json.decodeFromString(value)

    @TypeConverter
    fun longListToJson(ids: List<Long>): String = json.encodeToString(ids)

    @TypeConverter
    fun longListFromJson(value: String): List<Long> =
        json.decodeFromString(value)

    @TypeConverter
    fun stringListToJson(values: List<String>): String = json.encodeToString(values)

    @TypeConverter
    fun stringListFromJson(value: String): List<String> =
        json.decodeFromString(value)
}
