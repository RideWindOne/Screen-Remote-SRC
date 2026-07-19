package com.screen.remote.android.feature.remote.widget.touch

import android.view.MotionEvent
import android.view.View
import com.screen.remote.android.infrastructure.scrcpy.connection.TouchAction

private data class RemoteTouchPoint(
    val x: Int,
    val y: Int,
)

/**
 * Allocation-conscious multi-pointer dispatcher.
 *
 * MotionEvent batches may contain historical MOVE samples. Only the newest sample is forwarded so
 * that a slow network never makes the remote device replay stale finger positions.
 */
internal class RemoteTouchEventDispatcher(
    private val sendTouch: (
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float,
    ) -> Unit,
) {
    private val activeRemotePoints = linkedMapOf<Int, RemoteTouchPoint>()
    private val eventPointerIds = HashSet<Int>(10)
    private val eventRemotePoints = HashMap<Int, RemoteTouchPoint>(10)

    fun handle(
        view: View,
        event: MotionEvent,
        deviceWidth: Int,
        deviceHeight: Int,
    ): Boolean {
        if (deviceWidth <= 0 || deviceHeight <= 0 || view.width <= 0 || view.height <= 0) {
            return false
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // Bypass Android's touch resampling/batching for latency-sensitive remote control.
            view.requestUnbufferedDispatch(event)
        }

        extractLatestEventData(event, view, deviceWidth, deviceHeight)
        releaseDisappearedPointers(deviceWidth, deviceHeight)

        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            releaseAllPointers(deviceWidth, deviceHeight)
            return true
        }

        val endedPointerId =
            when (event.actionMasked) {
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP,
                -> event.getPointerId(event.actionIndex)
                else -> null
            }

        // Update already-active pointers before adding a new one. scrcpy-server builds the
        // compound ACTION_POINTER_DOWN from its stored state, so the existing fingers must carry
        // their newest coordinates before the new pointer is injected.
        handlePointerMove(event, endedPointerId, deviceWidth, deviceHeight)
        handlePointerDown(event, endedPointerId, deviceWidth, deviceHeight)

        if (endedPointerId != null) {
            val remotePoint =
                eventRemotePoints[endedPointerId]
                    ?: activeRemotePoints[endedPointerId]
                    ?: toRemotePoint(event, event.actionIndex, view, deviceWidth, deviceHeight)
            releasePointer(endedPointerId, remotePoint, deviceWidth, deviceHeight)
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                view.performClick()
            }
        }

        return true
    }

    fun cancelActivePointers(
        deviceWidth: Int,
        deviceHeight: Int,
    ) {
        if (deviceWidth > 0 && deviceHeight > 0) {
            releaseAllPointers(deviceWidth, deviceHeight)
        } else {
            activeRemotePoints.clear()
        }
    }

    private fun extractLatestEventData(
        event: MotionEvent,
        view: View,
        deviceWidth: Int,
        deviceHeight: Int,
    ) {
        eventPointerIds.clear()
        eventRemotePoints.clear()

        for (index in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(index)
            eventPointerIds += pointerId
            eventRemotePoints[pointerId] = toRemotePoint(event, index, view, deviceWidth, deviceHeight)
        }
    }

    private fun handlePointerDown(
        event: MotionEvent,
        endedPointerId: Int?,
        deviceWidth: Int,
        deviceHeight: Int,
    ) {
        for (index in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(index)
            if (pointerId == endedPointerId || activeRemotePoints.containsKey(pointerId)) continue

            val remotePoint = eventRemotePoints[pointerId] ?: continue
            activeRemotePoints[pointerId] = remotePoint
            dispatch(
                action = TouchAction.ACTION_DOWN,
                pointerId = pointerId,
                remotePoint = remotePoint,
                pressure = remoteFingerPressure(TouchAction.ACTION_DOWN),
                deviceWidth = deviceWidth,
                deviceHeight = deviceHeight,
            )
        }
    }

    private fun handlePointerMove(
        event: MotionEvent,
        endedPointerId: Int?,
        deviceWidth: Int,
        deviceHeight: Int,
    ) {
        for (index in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(index)
            if (pointerId == endedPointerId) continue

            val previousPoint = activeRemotePoints[pointerId] ?: continue
            val remotePoint = eventRemotePoints[pointerId] ?: continue
            if (remotePoint == previousPoint) continue

            activeRemotePoints[pointerId] = remotePoint
            dispatch(
                action = TouchAction.ACTION_MOVE,
                pointerId = pointerId,
                remotePoint = remotePoint,
                pressure = remoteFingerPressure(TouchAction.ACTION_MOVE),
                deviceWidth = deviceWidth,
                deviceHeight = deviceHeight,
            )
        }
    }

    private fun releaseDisappearedPointers(
        deviceWidth: Int,
        deviceHeight: Int,
    ) {
        val iterator = activeRemotePoints.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in eventPointerIds) {
                dispatch(
                    action = TouchAction.ACTION_UP,
                    pointerId = entry.key,
                    remotePoint = entry.value,
                    pressure = 0f,
                    deviceWidth = deviceWidth,
                    deviceHeight = deviceHeight,
                )
                iterator.remove()
            }
        }
    }

    private fun releaseAllPointers(
        deviceWidth: Int,
        deviceHeight: Int,
    ) {
        val iterator = activeRemotePoints.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            dispatch(
                action = TouchAction.ACTION_UP,
                pointerId = entry.key,
                remotePoint = entry.value,
                pressure = 0f,
                deviceWidth = deviceWidth,
                deviceHeight = deviceHeight,
            )
            iterator.remove()
        }
    }

    private fun releasePointer(
        pointerId: Int,
        remotePoint: RemoteTouchPoint,
        deviceWidth: Int,
        deviceHeight: Int,
    ) {
        if (!activeRemotePoints.containsKey(pointerId)) return
        dispatch(TouchAction.ACTION_UP, pointerId, remotePoint, 0f, deviceWidth, deviceHeight)
        activeRemotePoints.remove(pointerId)
    }

    private fun dispatch(
        action: Int,
        pointerId: Int,
        remotePoint: RemoteTouchPoint,
        pressure: Float,
        deviceWidth: Int,
        deviceHeight: Int,
    ) {
        sendTouch(
            action,
            pointerId.toLong(),
            remotePoint.x,
            remotePoint.y,
            deviceWidth,
            deviceHeight,
            pressure,
        )
    }

    private fun toRemotePoint(
        event: MotionEvent,
        pointerIndex: Int,
        view: View,
        deviceWidth: Int,
        deviceHeight: Int,
    ): RemoteTouchPoint =
        RemoteTouchPoint(
            x =
                (event.getX(pointerIndex) / view.width * deviceWidth)
                    .toInt()
                    .coerceIn(0, deviceWidth - 1),
            y =
                (event.getY(pointerIndex) / view.height * deviceHeight)
                    .toInt()
                    .coerceIn(0, deviceHeight - 1),
        )
}

/**
 * Android pressure is device-specific and some panels report 0 for a newly added pointer. The
 * remote side is an injected virtual finger rather than a pressure-sensitive stylus, so keep every
 * active pointer at full pressure and reserve zero exclusively for UP.
 */
internal fun remoteFingerPressure(action: Int): Float =
    if (action == TouchAction.ACTION_UP) 0f else 1f
