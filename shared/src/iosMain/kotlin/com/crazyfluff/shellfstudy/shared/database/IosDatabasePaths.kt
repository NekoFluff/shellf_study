package com.crazyfluff.shellfstudy.shared.database

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal fun iosDocumentDirectoryPath(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    return requireNotNull(documentDirectory?.path)
}

/** OS-purgeable, unlike [iosDocumentDirectoryPath] — the right home for re-downloadable content
 *  like cached audio clips, matching Android's use of `cacheDir`. */
@OptIn(ExperimentalForeignApi::class)
internal fun iosCachesDirectoryPath(): String {
    val cachesDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    return requireNotNull(cachesDirectory?.path)
}

/** Where the offline audio library keeps its clips: Application Support, which the OS never purges
 *  (unlike Caches), excluded from iCloud backup since every clip can be downloaded again. */
@OptIn(ExperimentalForeignApi::class)
internal fun iosAudioLibraryDirectoryPath(): String {
    val supportDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    )
    val library = requireNotNull(supportDirectory?.URLByAppendingPathComponent("audio_library"))
    NSFileManager.defaultManager.createDirectoryAtURL(
        library,
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )
    library.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    return requireNotNull(library.path)
}
