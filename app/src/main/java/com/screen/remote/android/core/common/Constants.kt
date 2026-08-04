package com.screen.remote.android.core.common

/**
 * 常量统一导出文件
 *
 * 所有常量已按功能拆分到 constants/ 目录下的独立文件：
 * - AppColors.kt - 颜色常量
 * - AppDimens.kt - 尺寸常量
 * - AppTextSizes.kt - 文字大小常量
 * - NetworkConstants.kt - 网络常量
 * - AdbPairingConstants.kt - ADB 配对常量
 * - ScrcpyConstants.kt - Scrcpy 常量
 * - AppConstants.kt - 应用常量
 * - FilePathConstants.kt - 文件路径常量
 * - UIConstants.kt - UI 常量
 * - SessionColors.kt - 会话颜色常量
 * - PlaceholderTexts.kt - 占位符文本
 * - LogTags.kt - 日志标签常量
 *
 * 使用方式：
 * ```kotlin
 * import com.screen.remote.android.core.common.AppColors
 * import com.screen.remote.android.core.common.AppDimens
 * import com.screen.remote.android.core.common.LogTags
 * // 或者使用通配符导入
 * import com.screen.remote.android.core.common.*
 * ```
 */

// 重新导出所有常量对象，保持向后兼容
typealias AppColors = com.screen.remote.android.core.common.constants.AppColors
typealias AppDimens = com.screen.remote.android.core.common.constants.AppDimens
typealias AppTextSizes = com.screen.remote.android.core.common.constants.AppTextSizes
typealias IosDesignTokens = com.screen.remote.android.core.common.constants.IosDesignTokens
typealias NetworkConstants = com.screen.remote.android.core.common.constants.NetworkConstants
typealias AdbPairingConstants = com.screen.remote.android.core.common.constants.AdbPairingConstants
typealias ScrcpyConstants = com.screen.remote.android.core.common.constants.ScrcpyConstants
typealias AppConstants = com.screen.remote.android.core.common.constants.AppConstants
typealias PlaceholderTexts = com.screen.remote.android.core.common.constants.PlaceholderTexts
typealias LogTags = com.screen.remote.android.core.common.constants.LogTags
