package com.mobile.scrcpy.android.core.common.event

/**
 * UI 事件 - 用户交互、输入、窗口操作
 */

/**
 * 键盘按下
 */
data class KeyDown(
    val scancode: Int,
    val keycode: Int,
    val keymod: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.UI

    override fun getDescription() = "键盘按下: keycode=$keycode"
}

/**
 * 键盘抬起
 */
data class KeyUp(
    val scancode: Int,
    val keycode: Int,
    val keymod: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.UI

    override fun getDescription() = "键盘抬起: keycode=$keycode"
}

/**
 * 鼠标移动
 */
data class MouseMotion(
    val x: Float,
    val y: Float,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.UI

    override fun getDescription() = "鼠标移动: ($x, $y)"

    override fun needsSampling() = true
}

/**
 * 鼠标按下
 */
data class MouseButtonDown(
    val x: Float,
    val y: Float,
    val button: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.UI

    override fun getDescription() = "鼠标按下: ($x, $y) button=$button"
}

/**
 * 鼠标抬起
 */
data class MouseButtonUp(
    val x: Float,
    val y: Float,
    val button: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.UI

    override fun getDescription() = "鼠标抬起: ($x, $y) button=$button"
}

/**
 * 触摸按下
 */
data class TouchDown(
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.UI

    override fun getDescription() = "触摸按下: pointer=$pointerId ($x, $y)"
}

/**
 * 触摸移动
 */
data class TouchMove(
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.UI

    override fun getDescription() = "触摸移动: pointer=$pointerId ($x, $y)"

    override fun needsSampling() = true
}

/**
 * 触摸抬起
 */
data class TouchUp(
    val pointerId: Int,
    val x: Float,
    val y: Float,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.UI

    override fun getDescription() = "触摸抬起: pointer=$pointerId ($x, $y)"
}

/**
 * 滚动事件
 */
data class Scroll(
    val x: Float,
    val y: Float,
    val hScroll: Float,
    val vScroll: Float,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.UI

    override fun getDescription() = "滚动: ($x, $y) h=$hScroll v=$vScroll"
}

/**
 * 剪贴板更新
 */
data class ClipboardUpdate(
    val content: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.UI

    override fun getDescription() = "剪贴板更新: ${content.take(20)}..."
}
