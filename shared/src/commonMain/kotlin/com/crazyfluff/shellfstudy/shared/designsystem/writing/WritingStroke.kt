package com.crazyfluff.shellfstudy.shared.designsystem.writing

import androidx.compose.ui.geometry.Offset

/**
 * One user-drawn stroke from the writing-practice canvas, recorded in that canvas's own local
 * pixel space. Purely ephemeral UI state — never serialized, persisted, or submitted anywhere —
 * which is why it lives here rather than alongside [com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke].
 */
data class WritingStroke(val points: List<Offset>)
