package com.crazyfluff.shellfstudy.shared.lifecycle

import kotlin.concurrent.Volatile
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/** The device's network as of the last path update. Before the first one arrives it is assumed
 *  usable, so nothing waits on a monitor that hasn't reported yet. */
data class IosNetworkStatus(val online: Boolean = true, val expensive: Boolean = false)

object IosNetwork {
    @Volatile
    var current: IosNetworkStatus = IosNetworkStatus()
        internal set
}

/**
 * Keeps [IosNetwork.current] up to date and calls [onChange] whenever it changes.
 *
 * Android gets this for free — WorkManager holds outbox and download work until a network constraint
 * is met. iOS has no equivalent, so without this the outbox only noticed a returning connection at its
 * next backoff retry, up to five minutes later, and work done offline sat unsent for that long. The
 * first path update only records the starting state: launching online is not a reconnection, and the
 * launch path already requests its own drain.
 */
@OptIn(ExperimentalForeignApi::class)
fun startIosConnectivityMonitor(onChange: (previous: IosNetworkStatus, current: IosNetworkStatus) -> Unit) {
    var initialised = false
    val monitor = nw_path_monitor_create()
    nw_path_monitor_set_update_handler(monitor) { path ->
        val previous = IosNetwork.current
        val status = IosNetworkStatus(
            online = nw_path_get_status(path) == nw_path_status_satisfied,
            expensive = nw_path_is_expensive(path)
        )
        IosNetwork.current = status
        if (initialised && status != previous) onChange(previous, status)
        initialised = true
    }
    // Main queue, so the handler's state and the callbacks it fires never race each other. Never
    // cancelled: started once from initKoin, it lives as long as the process.
    nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
    nw_path_monitor_start(monitor)
}
