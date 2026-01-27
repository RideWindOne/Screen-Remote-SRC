package com.mobile.scrcpy.android.feature.remote.components.floating

// ==================== 双球体系统配置 ====================

/** 大球 A 直径（dp） */
internal const val BALL_A_SIZE_DP = 50

/** 小球 B 直径（dp） */
internal const val BALL_B_SIZE_DP = 45

// ==================== 手势识别配置 ====================

/** 点击最大时长（毫秒），超过此时间不算点击 */
internal const val CLICK_TIME_MS = 300L

/** 长按触发时长（毫秒），按住超过此时间触发长按模式 */
internal const val LONG_PRESS_TIME_MS = 300L

/** 预留功能触发时长（毫秒），长按超过此时间触发预留功能 */
internal const val RESERVED_FUNCTION_TIME_MS = 800L

/** 移动阈值（dp），手指移动超过此距离才算拖动 */
internal const val MOVE_SLOP_DP = 12f

/** 长按取消阈值（dp），检测移动的最小阈值（用于取消长按延迟） */
internal const val LONG_PRESS_CANCEL_SLOP_DP = 3f

/** 长按拖动时，小球距离大球的最大距离（dp） */
internal const val MAX_DISTANCE_FROM_B_DP = 40f

/** 方向识别阈值（dp），拖动超过此距离才识别方向 */
internal const val DIRECTION_THRESHOLD_DP = 15f

/** 方向触感延迟（毫秒），进入新扇形区域后延迟触发触感 */
internal const val DIRECTION_HAPTIC_DELAY_MS = 300L

/** 归位动画时长（毫秒） */
internal const val RESET_ANIMATION_DURATION_MS = 200L

// ==================== 贴边配置 ====================

/** 贴边触发距离（dp），小球边缘距离屏幕边缘小于此值时触发触感并开始贴边 */
internal const val EDGE_SNAP_THRESHOLD_DP = 40f

/** 贴边后露出的宽度（dp），隐藏2/3，露出1/3 */
internal const val EDGE_VISIBLE_WIDTH_DP = 15f  // BALL_A_SIZE_DP / 3 = 15dp

/** 拖出距离阈值（dp），拖动超过此距离时取消贴边 */
internal const val EDGE_DRAG_OUT_THRESHOLD_DP = 30f

// ==================== 触感反馈配置 ====================

/** 边缘触感重置距离（dp），离开边缘超过此距离后重置触感状态，允许再次触发 */
internal const val EDGE_HAPTIC_RESET_DISTANCE_DP = 40f
