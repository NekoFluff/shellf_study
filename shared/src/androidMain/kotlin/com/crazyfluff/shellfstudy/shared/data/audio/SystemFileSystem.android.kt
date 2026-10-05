package com.crazyfluff.shellfstudy.shared.data.audio

import okio.FileSystem
import okio.Path

internal actual val systemFileSystem: FileSystem = FileSystem.SYSTEM

internal actual val systemTemporaryDirectory: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
