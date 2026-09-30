package com.crazyfluff.shellfstudy.shared.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberAppVersion(): AppVersion? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName?.let { AppVersion(name = it, build = info.longVersionCode.toString()) }
        }.getOrNull()
    }
}
