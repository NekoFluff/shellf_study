package com.crazyfluff.shellfstudy.shared.data.audio

import okio.FileSystem
import okio.Path

/** okio's `FileSystem.SYSTEM`, which exists on every platform this app targets but isn't visible
 *  from common code. */
internal expect val systemFileSystem: FileSystem

/** okio's `FileSystem.SYSTEM_TEMPORARY_DIRECTORY`, for the same reason — tests use it. */
internal expect val systemTemporaryDirectory: Path
