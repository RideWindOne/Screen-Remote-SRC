package com.mobile.scrcpy.android.feature.remote.components.video

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager

import com.mobile.scrcpy.android.core.i18n.RemoteTexts
/**
 * SurfaceView 组件（用于普通模式）
 * 
 * 优势：
 * - 性能最优，直接渲染到屏幕
 * - 内存占用最低
 * 
 * 劣势：
 * - 后台时 Surface 会被销毁（需要 dummy Surface 技术）
 * - 不支持某些 UI 变换
 * 
 * @param onSurfaceCreated Surface 创建时的回调
 * @param onSurfaceChanged Surface 尺寸变化时的回调
 * @param onSurfaceDestroyed Surface 销毁时的回调
 * @param onTouch 触摸事件回调
 * @param modifier 修饰符
 */
@Composable
fun VideoSurfaceView(
    onSurfaceCreated: (SurfaceHolder) -> Unit,
    onSurfaceChanged: (SurfaceHolder, Int, Int) -> Unit,
    onSurfaceDestroyed: (SurfaceHolder) -> Unit,
    onTouch: ((android.view.View, android.view.MotionEvent) -> Boolean)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        onSurfaceCreated(holder)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        if (width > 0 && height > 0) {
                            LogManager.d(
                                LogTags.REMOTE_DISPLAY,
                                "${RemoteTexts.REMOTE_SURFACE_READY.get()}: ${width}x${height}"
                            )
                            onSurfaceChanged(holder, width, height)
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        LogManager.d(
                            LogTags.REMOTE_DISPLAY,
                            RemoteTexts.REMOTE_SURFACE_DESTROYED.get()
                        )
                        onSurfaceDestroyed(holder)
                    }
                })

                // 设置触摸监听
                onTouch?.let { touchHandler ->
                    setOnTouchListener { view, event ->
                        touchHandler(view, event)
                    }
                }
            }
        },
        update = { view ->
            // 每次重组时检查 Surface 状态
            val holder = view.holder
            val surface = holder.surface
            if (surface != null && surface.isValid) {
                // Surface 有效，可以通知外部
                LogManager.d(
                    LogTags.REMOTE_DISPLAY,
                    "✅ update: ${RemoteTexts.REMOTE_SURFACE_RESTORED.get()}"
                )
            } else {
                LogManager.w(
                    LogTags.REMOTE_DISPLAY,
                    "⚠️ update: ${RemoteTexts.REMOTE_SURFACE_UNAVAILABLE.get()}"
                )
            }
        },
        modifier = modifier
    )
}
