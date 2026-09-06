package com.screen.remote.android.feature.remote.widget.floating

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.screen.remote.android.feature.remote.presentation.ControlViewModel
import kotlinx.coroutines.CoroutineScope
import kotlin.math.hypot

data class FloatingMenuActions(
    val controlViewModel: ControlViewModel,
    val captureTargetDeviceScreenshot: suspend () -> Result<String>,
    val toggleDeviceResolutionAdaptation: suspend () -> Result<Boolean>,
    val isDeviceResolutionAdapted: () -> Boolean,
    val disconnect: suspend () -> Unit,
    val reconnect: suspend () -> Unit,
    val backToApp: () -> Unit,
    val disableStayAwake: suspend () -> Result<Boolean>,
    val showKeyboardInput: () -> Unit,
    val requestUploadFilePicker: () -> Unit,
    val requestLayoutInspectorRender: () -> Unit,
    val rotateTargetDevice: suspend () -> Result<Boolean>,
    val hapticEnabled: Boolean,
)

internal const val BALL_A_SIZE_DP = 48
internal const val BALL_B_SIZE_DP = 41
internal const val FLOATING_BALL_INITIAL_BOTTOM_MARGIN_DP = 85f
internal const val FLOATING_BALL_INITIAL_RIGHT_MARGIN_DP = 20f
internal const val CLICK_TIME_MS = 300L
internal const val LONG_PRESS_TIME_MS = 300L
internal const val RESERVED_FUNCTION_TIME_MS = 800L
internal const val MOVE_SLOP_DP = 12f
internal const val LONG_PRESS_CANCEL_SLOP_DP = 3f
internal const val MAX_DISTANCE_FROM_B_DP = 40f
internal const val DIRECTION_THRESHOLD_DP = 15f
internal const val DIRECTION_HAPTIC_DELAY_MS = 300L
internal const val RESET_ANIMATION_DURATION_MS = 200L
internal const val EDGE_SNAP_THRESHOLD_DP = 40f
internal const val EDGE_DRAG_OUT_THRESHOLD_DP = 30f
internal const val EDGE_HAPTIC_RESET_DISTANCE_DP = 40f

internal class FloatingMenuGestureState {
    var downTime = 0L
    var downRawX = 0f
    var downRawY = 0f
    var lastRawX = 0f
    var lastRawY = 0f
    var hasMoved = false
    var isLongPress = false
    var canEnterLongPress = false
    var isSecondStageLongPress = false
    var longPressHandler: Handler? = null
    var longPressRunnable: Runnable? = null
    var reservedFunctionHandler: Handler? = null
    var reservedFunctionRunnable: Runnable? = null
    var ballBCenterX = 0f
    var ballBCenterY = 0f
    var lastAngle: Double? = null
    var downOffsetX = 0f
    var downOffsetY = 0f
    var isSnappedToEdge = false
    var snappedEdge: Edge? = null
    var hasTriggeredEdgeHaptic = false
    var detectedDirection: Direction? = null
    var directionLocked = false
    var lastHapticDirection: Direction? = null
    var directionEnterTime = 0L
    var hasTriggeredHapticInCurrentDirection = false
    var isMenuShown = false

    enum class Edge {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
    }

    enum class Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT,
    }

    fun reset() {
        hasMoved = false
        isLongPress = false
        canEnterLongPress = false
        isSecondStageLongPress = false
        lastAngle = null
        downOffsetX = 0f
        downOffsetY = 0f
        detectedDirection = null
        directionLocked = false
        hasTriggeredEdgeHaptic = false
        lastHapticDirection = null
        directionEnterTime = 0L
        hasTriggeredHapticInCurrentDirection = false
    }

    fun cancelLongPressCallbacks() {
        longPressRunnable?.let { longPressHandler?.removeCallbacks(it) }
        reservedFunctionRunnable?.let { reservedFunctionHandler?.removeCallbacks(it) }
    }

    fun initHandlers() {
        longPressHandler = Handler(Looper.getMainLooper())
        reservedFunctionHandler = Handler(Looper.getMainLooper())
    }

    fun cleanup() {
        cancelLongPressCallbacks()
        longPressHandler = null
        reservedFunctionHandler = null
        longPressRunnable = null
        reservedFunctionRunnable = null
    }
}

internal class FloatingMenuLongPressScheduler(
    private val state: FloatingMenuGestureState,
    private val hapticEnabled: Boolean,
) {
    fun schedule() {
        val longPressRunnable =
            Runnable {
                if (!state.hasMoved) {
                    state.canEnterLongPress = true
                    if (hapticEnabled) {
                        performHapticFeedbackCompat(HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }
        val reservedFunctionRunnable =
            Runnable {
                if (!state.hasMoved && state.canEnterLongPress) {
                    state.isSecondStageLongPress = true
                    if (hapticEnabled) {
                        performHapticFeedbackCompat(HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }

        state.longPressRunnable = longPressRunnable
        state.reservedFunctionRunnable = reservedFunctionRunnable
        state.longPressHandler?.postDelayed(longPressRunnable, LONG_PRESS_TIME_MS)
        state.reservedFunctionHandler?.postDelayed(reservedFunctionRunnable, RESERVED_FUNCTION_TIME_MS)
    }
}

/**
 * 手势识别处理器（纯 WindowManager 实现）
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingMenuGestureHandler(
    context: Context,
    private val ballA: View,
    private val ballB: View,
    private val windowManager: WindowManager,
    private val paramsA: WindowManager.LayoutParams,
    private val paramsB: WindowManager.LayoutParams,
    actions: FloatingMenuActions,
    scope: CoroutineScope,
    hapticEnabled: Boolean,
) : View.OnTouchListener {
    private val state = FloatingMenuGestureState()
    private val detector = FloatingMenuGestureDetector(context, state, hapticEnabled)
    private val menuManager =
        FloatingMenuViewManager(
            context = context,
            windowManager = windowManager,
            paramsA = paramsA,
            paramsB = paramsB,
            ballA = ballA,
            ballB = this.ballB,
            actions = actions,
            scope = scope,
            state = state,
            hapticEnabled = hapticEnabled,
        )
    private val edgeSnap =
        FloatingMenuEdgeSnap(
            context = context,
            ballA = ballA,
            ballB = this.ballB,
            windowManager = windowManager,
            paramsA = paramsA,
            paramsB = paramsB,
            state = state,
            menuManager = menuManager,
            hapticEnabled = hapticEnabled,
        )
    private val ballMovement =
        FloatingMenuBallMovement(
            context = context,
            ballA = ballA,
            ballB = this.ballB,
            windowManager = windowManager,
            paramsA = paramsA,
            paramsB = paramsB,
            state = state,
            edgeSnap = edgeSnap,
            menuManager = menuManager,
        )
    private val longPressScheduler =
        FloatingMenuLongPressScheduler(
            state = state,
            hapticEnabled = hapticEnabled,
        )
    private val completionHandler =
        FloatingMenuGestureCompletionHandler(
            ballA = ballA,
            paramsA = paramsA,
            actions = actions,
            scope = scope,
            state = state,
            detector = detector,
            edgeSnap = edgeSnap,
            ballMovement = ballMovement,
            menuManager = menuManager,
            hapticEnabled = hapticEnabled,
        )

    override fun onTouch(
        v: View,
        event: MotionEvent,
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isTouchInsideCircle(v, event)) {
                    return false
                }
                handleDown(event)
            }

            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> completionHandler.handleUp()
            MotionEvent.ACTION_CANCEL -> handleCancel()
        }
        return true
    }

    private fun isTouchInsideCircle(
        view: View,
        event: MotionEvent,
    ): Boolean {
        if (view is FloatingBallView) {
            return view.containsTouch(event.x, event.y)
        }
        val radius = view.width / 2f
        val centerX = view.width / 2f
        val centerY = view.height / 2f
        val distance = hypot((event.x - centerX).toDouble(), (event.y - centerY).toDouble())
        return distance <= radius
    }

    private fun handleDown(event: MotionEvent) {
        edgeSnap.cancelAnimation()
        state.cancelLongPressCallbacks()
        state.initHandlers()

        state.downTime = System.currentTimeMillis()
        state.downRawX = event.rawX
        state.downRawY = event.rawY
        state.lastRawX = event.rawX
        state.lastRawY = event.rawY
        state.hasMoved = false
        state.isLongPress = false
        state.canEnterLongPress = false
        state.isSecondStageLongPress = false

        state.ballBCenterX = paramsB.x + ballB.width / 2f
        state.ballBCenterY = paramsB.y + ballB.height / 2f

        val ballACenterX = paramsA.x + ballA.width / 2f
        val ballACenterY = paramsA.y + ballA.height / 2f
        state.downOffsetX = event.rawX - ballACenterX
        state.downOffsetY = event.rawY - ballACenterY

        if (!state.isMenuShown) {
            longPressScheduler.schedule()
        }

    }

    private fun handleMove(event: MotionEvent) {
        val dx = event.rawX - state.downRawX
        val dy = event.rawY - state.downRawY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (state.isMenuShown) {
            state.cancelLongPressCallbacks()
            state.isLongPress = false
            if (detector.checkMovementThreshold(dx, dy)) {
                ballMovement.moveAAndBTogether(event)
            }
            return
        }

        detector.checkLongPressTransition(distance)

        if (!detector.checkMovementThreshold(dx, dy)) {
            return
        }

        if (state.isLongPress) {
            ballMovement.moveAAroundB(event, detector)
        } else {
            ballMovement.moveAAndBTogether(event)
        }
    }

    private fun handleCancel() {
        state.cancelLongPressCallbacks()
        state.reset()
    }

    fun cleanup() {
        edgeSnap.cleanup()
        state.cleanup()
        menuManager.cleanup()

        try {
            windowManager.removeViewImmediate(ballA)
        } catch (_: Exception) {
        }

        try {
            windowManager.removeViewImmediate(ballB)
        } catch (_: Exception) {
        }
    }
}
