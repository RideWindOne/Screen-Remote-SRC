package com.screen.remote.android.core.common.constants

import androidx.compose.ui.graphics.Color

/**
 * 应用颜色常量
 * 包含浅色模式和深色模式的所有颜色定义
 */
object AppColors {
    // ========== 浅色模式 ==========

    /** 浅色分组页面背景 */
    val lightBackground = Color(0xFFF5F5F5)

    /** 浅色卡片与顶栏表面 */
    val lightSurface = Color(0xFFFFFFFF)

    /** 浅色次级填充与选中背景 */
    val lightSurfaceMuted = Color(0xFFE9E9EE)

    /** 浅色主文字 */
    val lightTextPrimary = Color(0xFF1C1C1E)

    /** 浅色次文字 */
    val lightTextSecondary = Color(0xFF6E6E73)

    /** 浅色分隔线基色，由组件叠加透明度 */
    val lightDivider = Color(0xFF3C3C43)

    /** iOS 蓝色 - 用于按钮、链接等 */
    val iOSBlue = Color(0xFF007AFF)

    /** 错误颜色 */
    val error = Color(0xFFFF3B30)

    /** 成功/启用状态 */
    val success = Color(0xFF34C759)

    /** 警告/提醒状态 */
    val warning = Color(0xFFFF9F0A)

    /** 危险操作强调色 */
    val destructive = Color(0xFFFF5252)

    /** 信息/浅蓝强调色 */
    val info = Color(0xFF5AC8FA)

    /** 命令预设强调色 */
    val commandDeviceAccent = Color(0xFF53A7FF)
    val commandWindowAccent = Color(0xFF7B61FF)
    val commandAppAccent = Color(0xFF4CB782)
    val commandMemoryAccent = Color(0xFFFF6B9D)

    /** iOS 风格浅灰轨道/选中背景（浅色模式） */
    val iOSSelectedBackground = Color(0xFFE9E9EB)

    /** 白色背景 */
    val white = Color.White

    /** 黑色文字 */
    val black = Color.Black

    // ========== 深色模式 ==========

    /** 深色模式 - 页面背景（最外层） */
    val darkBackground = Color(0xFF121212)

    /** 深色模式 - 卡片/横条背景 */
    val darkCard = Color(0xFF1E1E1E)

    /** 深色模式 - Dialog 背景（比卡片更亮，形成浮起效果） */
    val darkDialogBackground = Color(0xFF2C2C2E)

    /** 深色模式 - Dialog 标题栏背景 */
    val darkDialogHeader = Color(0xFF3A3A3C)

    /** 深色模式 - 主文字 */
    val darkTextPrimary = Color(0xFFF2F2F7)

    /** 深色模式 - 副文字/说明 */
    val darkTextSecondary = Color(0xFFAEAEB2)

    /** 深色模式 - 分割线 */
    val darkDivider = Color(0xFF545458)

    /** 深色模式 - 图标/箭头 */
    val darkIcon = Color(0xFF8A8A8A)

    /** 深色模式 - iOS 风格选中背景色 */
    val darkIOSSelectedBackground = Color(0xFF3A3A3C)

    /** 深色模式 - 主按钮 */
    val darkButtonPrimary = Color(0xFF0A84FF)

}
