package com.crazyfluff.shellfstudy.shared.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityBucketsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun roundTrip_preservesAllFields() {
        val original = ActivityBuckets(
            weekDays = List(7) { it },
            monthDays = List(30) { it % 5 },
            yearMonths = List(12) { it * 2 },
            allTimeMonths = listOf(0, 3, 7, 12, 5)
        )
        val encoded = json.encodeToString(ActivityBuckets.serializer(), original)
        val decoded = json.decodeFromString(ActivityBuckets.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun oldJsonWithoutAllTimeMonths_deserializesToEmptyList() {
        // Simulates cached data written before allTimeMonths was added
        val oldJson = """
            {
              "weekDays":[1,0,0,2,0,0,3],
              "monthDays":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
              "yearMonths":[0,0,0,5,0,0,0,3,0,0,0,2]
            }
        """.trimIndent()
        val decoded = json.decodeFromString(ActivityBuckets.serializer(), oldJson)
        assertEquals(emptyList(), decoded.allTimeMonths)
        // Other fields unaffected
        assertEquals(listOf(1, 0, 0, 2, 0, 0, 3), decoded.weekDays)
        assertEquals(2, decoded.yearMonths.last())
    }

    @Test
    fun emptyAllTimeMonths_roundTrips() {
        val original = ActivityBuckets()  // all defaults
        val decoded = json.decodeFromString(ActivityBuckets.serializer(), json.encodeToString(ActivityBuckets.serializer(), original))
        assertEquals(emptyList(), decoded.allTimeMonths)
    }
}
