package com.crazyfluff.shellfstudy.shared.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

suspend fun CoroutineScope.runDurably(block: suspend CoroutineScope.() -> Unit) {
    launch(block = block).join()
}
