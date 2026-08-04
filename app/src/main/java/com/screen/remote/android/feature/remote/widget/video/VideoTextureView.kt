package com.screen.remote.android.feature.remote.widget.video

import android.graphics.SurfaceTexture
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.i18n.RemoteTexts

/**
 * TextureView 组件（用于全屏模式）
 *
 * 优势：
 * - SurfaceTexture 不会在后台被销毁（只要 TextureView 存在）
 * - 可以跨 Activity 共享
 * - 支持动画和变换
 *
 * 劣势：
 * - 性能略低于 SurfaceView（多一次纹理拷贝）
 * - 内存占用稍高
 *
 * @param onSurfaceTextureAvailable Surface 可用时的回调
 * @param onSurfaceTextureDestroyed Surface 销毁时的回调
 * @param modifier 修饰符
 * @param onTouch 触摸事件回调
 */
@Composable
fun VideoTextureView(
    onSurfaceTextureAvailable: (SurfaceTexture) -> Unit,
    onSurfaceTextureDestroyed: () -> Unit,
    modifier: Modifier = Modifier,
    onTouch: ((View, MotionEvent) -> Boolean)? = null,
) {
    val context = LocalContext.current

    // 记住 TextureView 实例，避免重组时重新创建
    val textureView =
        remember {
            AccessibleVideoTextureView(context)
        }

    AndroidView(
        factory = {
            textureView.apply {
                surfaceTextureListener =
                    object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            LogManager.d(
                                LogTags.REMOTE_DISPLAY,
                                "TextureView ${RemoteTexts.REMOTE_SURFACE_READY.english}: ${width}x$height",
                            )
                            onSurfaceTextureAvailable(surface)
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            LogManager.d(
                                LogTags.REMOTE_DISPLAY,
                                "TextureView size changed: ${width}x$height",
                            )
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            LogManager.d(
                                LogTags.REMOTE_DISPLAY,
                                "TextureView ${RemoteTexts.REMOTE_SURFACE_DESTROYED.english}",
                            )
                            onSurfaceTextureDestroyed()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                            // 每帧更新时调用，不需要处理
                        }
                    }
            }
        },
        update = { view ->
            view.setAccessibleOnTouchListener(onTouch)
        },
        modifier = modifier,
    )

}
