package com.crazyfluff.shellfstudy.core.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One stroke of a kanji's stroke-order diagram. [pathData] is an SVG path "d" string in KanjiVG's
 * native 109x109 unit square; [labelX]/[labelY] is where KanjiVG's own maintainers hand-placed
 * that stroke's number label — reused as-is rather than computed, since their placement is
 * already curated to avoid overlapping the ink or other numbers.
 */
@Serializable
data class StrokeOrderStroke(
    @SerialName("d") val pathData: String,
    @SerialName("x") val labelX: Float,
    @SerialName("y") val labelY: Float
)
