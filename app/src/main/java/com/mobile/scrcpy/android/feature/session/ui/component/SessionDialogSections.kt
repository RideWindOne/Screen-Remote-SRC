/**
 * 会话对话框配置区域主入口
 * 
 * 文件拆分说明：
 * - sections/VideoSection.kt - 视频配置区域
 * - sections/AudioSection.kt - 音频配置区域
 * - sections/DisplaySection.kt - 显示配置区域（其他选项）
 * - sections/ControlSection.kt - 远程设备和连接选项配置区域
 * 
 * 本文件保留主入口函数，实际实现已移至各 sections 子文件
 */
package com.mobile.scrcpy.android.feature.session.ui.component

import androidx.compose.runtime.Composable
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.feature.session.ui.component.sections.AudioConfigSection
import com.mobile.scrcpy.android.feature.session.ui.component.sections.ConnectionOptionsSection
import com.mobile.scrcpy.android.feature.session.ui.component.sections.OtherOptionsSection
import com.mobile.scrcpy.android.feature.session.ui.component.sections.RemoteDeviceSection
import com.mobile.scrcpy.android.feature.session.ui.component.sections.VideoConfigSection

// 主入口函数保留在此文件，实际实现已移至 sections/ 子目录
// 为保持向后兼容，这些函数直接调用 sections 中的实现
