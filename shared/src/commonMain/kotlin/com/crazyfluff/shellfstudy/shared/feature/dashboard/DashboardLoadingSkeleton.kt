package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Placeholder shapes mirroring the eventual dashboard layout, with a gentle shared pulse — reads
 * as "your content is on its way" rather than a bare, context-free spinner.
 */
@Composable
fun DashboardLoadingSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton_pulse")
    // Held as a State and read in the draw phase (see SkeletonBlock): this pulse runs for the whole
    // load, and reading it in composition would recompose all eight blocks every frame.
    val alpha = transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "skeleton_alpha"
    )

    Column(modifier = modifier) {
        SkeletonBlock(width = 220.dp, height = 34.dp, alpha = alpha)
        Spacer(modifier = Modifier.height(10.dp))
        SkeletonBlock(width = 140.dp, height = 20.dp, alpha = alpha)
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBlock(width = 260.dp, height = 14.dp, alpha = alpha)
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SkeletonBlock(height = 140.dp, alpha = alpha, modifier = Modifier.weight(1f))
            SkeletonBlock(height = 140.dp, alpha = alpha, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(24.dp))

        SkeletonBlock(height = 190.dp, alpha = alpha, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        SkeletonBlock(height = 90.dp, alpha = alpha, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        SkeletonBlock(height = 90.dp, alpha = alpha, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        SkeletonBlock(height = 120.dp, alpha = alpha, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SkeletonBlock(
    height: Dp,
    alpha: State<Float>,
    modifier: Modifier = Modifier,
    width: Dp? = null
) {
    val sizeModifier = if (width != null) modifier.width(width).height(height) else modifier.height(height)
    Box(
        modifier = sizeModifier
            // Read inside graphicsLayer, not in composition: `alpha` changes every frame of an
            // infinite animation, so a composition read here recomposed every block — and rebuilt
            // every modifier chain and RoundedCornerShape — for as long as the skeleton was on
            // screen. A State passed as a parameter also keeps this composable skippable.
            .graphicsLayer { this.alpha = alpha.value }
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
    )
}
