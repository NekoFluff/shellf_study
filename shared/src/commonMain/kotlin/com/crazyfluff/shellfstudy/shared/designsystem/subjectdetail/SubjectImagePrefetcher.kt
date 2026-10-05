package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * Loads radical images into Coil's disk cache ahead of time, so an image-only radical (one with no
 * Unicode glyph) can still be shown offline in a lesson or review it was never displayed in before.
 * Called for a level when its offline audio is downloaded — see OfflineAudioManager.
 *
 * May run before any screen has composed (a background download on a cold start), which is when
 * `ShellfStudyApp` would otherwise install the app's loader — so it installs the same one itself if
 * nothing has yet.
 */
class SubjectImagePrefetcher(private val context: PlatformContext) {

    suspend fun prefetch(urls: List<String>) {
        if (urls.isEmpty()) return
        SingletonImageLoader.setSafe(::newSubjectImageLoader)
        val loader = SingletonImageLoader.get(context)
        for (url in urls) {
            loader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    // Only the disk copy matters; the memory cache is for what's on screen.
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
            )
        }
    }
}
