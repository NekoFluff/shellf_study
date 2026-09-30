package com.crazyfluff.shellfstudy.shared.designsystem.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Surface colours for grouped-list screens (Settings, Friends), with their e-ink fallbacks.
 *
 * The e-ink scheme is hand-authored for the core roles only, so the tonal container roles
 * (surfaceContainerLow, primaryContainer, secondaryContainer) fall through to the stock light
 * scheme and render as a faint lavender. On e-ink these use plain grays and a border instead.
 */

/** The fill behind a group of rows. */
@Composable
fun groupContainerColor(): Color =
    if (LocalEinkTheme.current) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow

/** The fill for the one card a screen leads with (Settings' Daily plan, Friends' standing). */
@Composable
fun emphasisContainerColor(): Color =
    if (LocalEinkTheme.current) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer

/** A 1dp outline on e-ink, where the fills above are too close to the background to mark an edge. */
@Composable
fun einkBorder(): BorderStroke? =
    if (LocalEinkTheme.current) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null

/** Segmented buttons whose selected segment is solid ink on e-ink, where the stock lavender fill
 *  reads as unselected. */
@Composable
fun segmentedButtonColors(): SegmentedButtonColors =
    if (LocalEinkTheme.current) {
        SegmentedButtonDefaults.colors(
            activeContainerColor = MaterialTheme.colorScheme.onSurface,
            activeContentColor = MaterialTheme.colorScheme.surface
        )
    } else {
        SegmentedButtonDefaults.colors()
    }
