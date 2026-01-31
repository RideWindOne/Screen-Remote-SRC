package com.mobile.scrcpy.android.core.common.constants

import androidx.compose.ui.graphics.Color

/**
 * 应用颜色常量
 * 包含浅色模式和深色模式的所有颜色定义
 */
object AppColors {
    // ========== 浅色模式 ==========

    /** iOS 蓝色 - 用于按钮、链接等 */
    val iOSBlue = Color(0xFF007AFF)

    /** 分隔线颜色 */
    val divider = Color(0xFFBBBBBB)

    /** Dialog 背景色 */
    val dialogBackground = Color(0xFFECECEC)

    /** 标题栏背景色 */
    val headerBackground = Color(0xFFE7E7E7)

    /** 分组标题文字颜色 */
    val sectionTitleText = Color(0xFF6E6E73)

    /** 副标题/提示文字颜色 */
    val subtitleText = Color(0xFF959595)

    /** 错误颜色 */
    val error = Color(0xFFFF3B30)

    /** 箭头颜色 */
    val arrow = Color(0xFFE5E5EA)

    /** iOS 风格选中背景色（浅灰） */
    val iOSSelectedBackground = Color(0xFFE8E8E8)

    /** 白色背景 */
    val white = Color.White

    /** 黑色文字 */
    val black = Color.Black

    /** 浅色模式 - DropdownMenu 背景（纯白，带阴影形成浮起效果） */
    val lightDropdownBackground = Color(0xFFFFFFFF)

    // ========== 深色模式 ==========

    /** 深色模式 - 页面背景（最外层） */
    val darkBackground = Color(0xFF121212)

    /** 深色模式 - 卡片/横条背景 */
    val darkCard = Color(0xFF1E1E1E)

    /** 深色模式 - Dialog 背景（比卡片更亮，形成浮起效果） */
    val darkDialogBackground = Color(0xFF2C2C2E)

    /** 深色模式 - Dialog 标题栏背景 */
    val darkDialogHeader = Color(0xFF3A3A3C)

    /** 深色模式 - DropdownMenu 背景（与 Dialog 同级，形成浮起效果） */
    val darkDropdownBackground = Color(0xFF2C2C2E)

    /** 深色模式 - 主文字 */
    val darkTextPrimary = Color(0xFFEDEDED)

    /** 深色模式 - 副文字/说明 */
    val darkTextSecondary = Color(0xFFB3B3B3)

    /** 深色模式 - 禁用/次要信息 */
    val darkTextDisabled = Color(0x61FFFFFF) // rgba(255,255,255,0.38)

    /** 深色模式 - 分割线 */
    val darkDivider = Color(0xFF2C2C2C)

    /** 深色模式 - 图标/箭头 */
    val darkIcon = Color(0xFF8A8A8A)

    /** 深色模式 - Switch 开启状态 */
    val darkSwitchOn = Color(0xFF4CAF50)

    /** 深色模式 - Switch 关闭状态轨道 */
    val darkSwitchOffTrack = Color(0xFF5A5A5A)

    /** 深色模式 - Switch 关闭状态圆点 */
    val darkSwitchOffThumb = Color(0xFFBDBDBD)

    /** 深色模式 - iOS 风格选中背景色 */
    val darkIOSSelectedBackground = Color(0xFF3A3A3C)

    /** 深色模式 - 主按钮 */
    val darkButtonPrimary = Color(0xFF1E88E5)

    /** 深色模式 - 次按钮 */
    val darkButtonSecondary = Color(0xFF3A3A3A)

    /** 深色模式 - 不可点击按钮 */
    val darkButtonDisabled = Color(0xFF5A5A5A)
}
