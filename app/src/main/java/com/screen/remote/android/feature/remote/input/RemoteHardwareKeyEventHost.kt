package com.screen.remote.android.feature.remote.input

import android.view.KeyEvent

/**
 * Activity bridge used by the active remote screen to capture hardware keys
 * before Android applies their local default behavior.
 */
fun interface RemoteHardwareKeyEventHandler {
    fun onKeyEvent(event: KeyEvent): Boolean
}

interface RemoteHardwareKeyEventHost {
    fun setRemoteHardwareKeyEventHandler(handler: RemoteHardwareKeyEventHandler?)
}
