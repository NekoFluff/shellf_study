package com.crazyfluff.shellfstudy.shared.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A slimmer stand-in for Material3's [androidx.compose.material3.TopAppBar] for screens whose
 * title is empty or often blank (Dashboard, Lesson, Review) — the stock TopAppBar reserves a fixed
 * ~64dp band regardless of title content, which reads as dead space when there's nothing in it.
 * This sizes to its actual content (just the icon row) instead. Screens with a real title
 * (Settings) keep the stock TopAppBar, since its title fills the space appropriately.
 */
@Composable
fun CompactTopBar(
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    title: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        navigationIcon()
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            title()
        }
        actions()
    }
}
