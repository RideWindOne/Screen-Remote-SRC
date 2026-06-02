package com.screen.remote.android.core.common.constants

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

    /** 成功/启用状态 */
    val success = Color(0xFF34C759)

    /** 警告/提醒状态 */
    val warning = Color(0xFFFF9F0A)

    /** 危险操作强调色 */
    val destructive = Color(0xFFFF5252)

    /** 信息/浅蓝强调色 */
    val info = Color(0xFF5AC8FA)

    /** 中性色强调 */
    val neutralAccent = Color(0xFF8E8E93)

    /** 管理页固定无线端口强调色 */
    val managementPortAccent = Color(0xFF2A9D8F)

    /** 管理页分辨率强调色 */
    val managementResolutionAccent = Color(0xFF30B0C7)

    /** 管理页动画倍率强调色 */
    val managementAnimationAccent = Color(0xFFFF9500)

    /** 命令预设强调色 */
    val commandDeviceAccent = Color(0xFF53A7FF)
    val commandDisplayAccent = Color(0xFFFFA94D)
    val commandWindowAccent = Color(0xFF7B61FF)
    val commandAppAccent = Color(0xFF4CB782)
    val commandNetworkAccent = Color(0xFF12B7A2)
    val commandLogAccent = Color(0xFF5F6B7A)
    val commandMemoryAccent = Color(0xFFFF6B9D)
    val commandCpuAccent = Color(0xFFFF9800)
    val commandStorageAccent = Color(0xFF9C27B0)
    val commandProcessAccent = Color(0xFF00BCD4)
    val commandSystemAccent = Color(0xFF607D8B)

    /** 箭头颜色 */
    val arrow = Color(0xFFE5E5EA)

    /** iOS 风格浅灰轨道/选中背景（浅色模式） */
    val iOSSelectedBackground = Color(0xFFE9E9EB)

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
