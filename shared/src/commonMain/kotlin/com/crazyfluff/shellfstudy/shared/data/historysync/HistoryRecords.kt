package com.crazyfluff.shellfstudy.shared.data.historysync

import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDayEntity
import com.crazyfluff.shellfstudy.shared.database.studytime.StudyTimeSegmentEntity
import com.crazyfluff.shellfstudy.shared.network.historysync.HistoryRecordDto
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/*
 * The wire shape of synced history, kept apart from the Room entities on purpose. Payloads are stored
 * by the backend and read back by every install, including ones running older builds, so their field
 * names are a permanent contract: renaming a Room column must never change one. Adding a field is safe
 * (readers ignore unknown keys); renaming or removing one is not, and calls for a new [SEGMENT_PAYLOAD_VERSION].
 */

internal const val STUDY_SEGMENT_RECORD = "study_segment"
internal const val ACTIVITY_DAY_RECORD = "activity_day"

/** Bumped only for an incompatible change. Readers skip versions they don't know. */
internal const val SEGMENT_PAYLOAD_VERSION = 1

@Serializable
internal data class SyncedSegmentV1(
    val v: Int,
    val kind: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val level: Int? = null,
    val itemsAnswered: Int
)

internal fun StudyTimeSegmentEntity.toRecord(json: Json): HistoryRecordDto = HistoryRecordDto(
    type = STUDY_SEGMENT_RECORD,
    recordId = syncId,
    payload = json.encodeToJsonElement(
        SyncedSegmentV1(
            v = SEGMENT_PAYLOAD_VERSION,
            kind = kind,
            startedAtMs = startedAtMs,
            durationMs = durationMs,
            level = level,
            itemsAnswered = itemsAnswered
        )
    ).jsonObject
)

internal fun StudyActivityDayEntity.toRecord(): HistoryRecordDto =
    HistoryRecordDto(type = ACTIVITY_DAY_RECORD, recordId = date, payload = JsonObject(emptyMap()))

/** A stretch another device recorded, or null if this build can't read it. */
internal fun HistoryRecordDto.toSegmentEntity(json: Json): StudyTimeSegmentEntity? =
    runCatching { json.decodeFromJsonElement<SyncedSegmentV1>(payload) }.getOrNull()
        ?.takeIf { type == STUDY_SEGMENT_RECORD && it.v == SEGMENT_PAYLOAD_VERSION }
        ?.let { segment ->
            StudyTimeSegmentEntity(
                kind = segment.kind,
                startedAtMs = segment.startedAtMs,
                durationMs = segment.durationMs,
                level = segment.level,
                itemsAnswered = segment.itemsAnswered,
                syncId = recordId,
                uploaded = true
            )
        }

/** A study day another device recorded, or null if the id isn't a date. */
internal fun HistoryRecordDto.toActivityDayEntity(): StudyActivityDayEntity? =
    if (type == ACTIVITY_DAY_RECORD && runCatching { LocalDate.parse(recordId) }.isSuccess) {
        StudyActivityDayEntity(date = recordId, uploaded = true)
    } else {
        null
    }
