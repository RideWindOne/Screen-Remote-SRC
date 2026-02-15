package com.mobile.scrcpy.android.feature.remote.widget.floating

internal class AssistiveBallState(
    menuButtons: Int,
) {
    var centerX = 0f
    var centerY = 0f
    var smallBallAngle = 180f
    var smallBallX = 0f
    var smallBallY = 0f
    var halfHidden = false
    var isMenuOpen = false
    val menuPositions = Array(menuButtons) { 0f to 0f }
}
