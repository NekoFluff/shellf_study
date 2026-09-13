package com.crazyfluff.shellfstudy.shared.designsystem.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Returns a function that hands [text] off to the OS share sheet (Android's chooser /
 *  `UIActivityViewController` on iOS) — lets the user pick any app that accepts shared text
 *  (Akebi, Anki, Notes, ...) without this app needing to know anything about Akebi specifically,
 *  and without requiring a text-selection gesture first. */
@Composable
expect fun rememberShareText(): (String) -> Unit

/**
 * Hands [text] off to the OS — see [rememberShareText] for the two platform implementations.
 *
 * Provided once by `ShellfStudyApp` so the two consumers (the subject detail's context sentences and
 * the lesson study card's) read it where they need it. Until this existed there was no seam between a
 * share tap and `startActivity`/`presentViewController`, so nothing could assert *what* was shared:
 * the `expect fun` was called at the point of use.
 *
 * Null means "no override": a consumer falls back to [rememberShareText] itself, so a composable
 * rendered outside the app root — a focused test, a preview — still shares through the OS rather than
 * silently doing nothing. Only a test that wants to *record* what was shared provides a sink, which
 * is the point of the local: it is additive, not a requirement.
 */
val LocalShareText = staticCompositionLocalOf<((String) -> Unit)?> { null }
