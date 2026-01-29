package com.mobile.scrcpy.android.feature.remote.components.video

import android.graphics.SurfaceTexture
import android.os.Build
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.util.toSurface
import com.mobile.scrcpy.android.core.i18n.RemoteTexts

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
 */
@Composable
fun VideoTextureView(
    onSurfaceTextureAvailable: (SurfaceTexture) -> Unit,
    onSurfaceTextureDestroyed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // 记住 TextureView 实例，避免重组时重新创建
    val textureView =
        remember {
            TextureView(context)
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
                                "TextureView ${RemoteTexts.REMOTE_SURFACE_READY.get()}: ${width}x$height",
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
                                "TextureView ${RemoteTexts.REMOTE_SURFACE_DESTROYED.get()}",
                            )
                            onSurfaceTextureDestroyed()
                            // 返回 false 表示不自动释放 SurfaceTexture（我们手动管理）
                            return false
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                            // 每帧更新时调用，不需要处理
                        }
                    }
            }
        },
        update = { view ->
            // 每次重组时检查 SurfaceTexture 状态
            val surfaceTexture = view.surfaceTexture
            if (surfaceTexture != null) {
                // 在 API 26+ 上检查 isReleased，否则假设有效
                val isValid =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        !surfaceTexture.isReleased
                    } else {
                        true
                    }
                if (isValid) {
                    // SurfaceTexture 仍然有效，无需操作
                }
            }
        },
        modifier = modifier,
    )

    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            // TextureView 销毁时释放 SurfaceTexture
            textureView.surfaceTexture?.release()
        }
    }
}
