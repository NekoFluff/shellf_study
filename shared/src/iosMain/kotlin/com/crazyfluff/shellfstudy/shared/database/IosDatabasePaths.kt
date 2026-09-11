package com.crazyfluff.shellfstudy.shared.database

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
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
