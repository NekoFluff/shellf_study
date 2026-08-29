package com.crazyfluff.shellfstudy.shared.designsystem.text

import androidx.compose.runtime.Composable

/** Returns a function that hands [text] off to the OS share sheet (Android's chooser /
 *  `UIActivityViewController` on iOS) — lets the user pick any app that accepts shared text
 *  (Akebi, Anki, Notes, ...) without this app needing to know anything about Akebi specifically,
 *  and without requiring a text-selection gesture first. */
@Composable
expect fun rememberShareText(): (String) -> Unit
