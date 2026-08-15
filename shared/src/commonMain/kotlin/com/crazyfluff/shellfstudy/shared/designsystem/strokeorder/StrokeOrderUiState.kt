package com.crazyfluff.shellfstudy.shared.designsystem.strokeorder

import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke

sealed interface StrokeOrderUiState {
    data object Loading : StrokeOrderUiState
    data object Unavailable : StrokeOrderUiState
    data class Available(val strokes: List<StrokeOrderStroke>) : StrokeOrderUiState
}
