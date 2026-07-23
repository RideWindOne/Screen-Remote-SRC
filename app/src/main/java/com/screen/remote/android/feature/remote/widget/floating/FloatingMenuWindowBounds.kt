package com.screen.remote.android.feature.remote.widget.floating

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.util.compat.getRootWindowInsetsCompat

internal data class FloatingMenuWindowBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

private data class FloatingMenuInsets(
    val left: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

/**
 * WindowManager.LayoutParams 的 x/y 相对于当前 Activity 窗口，而不是远端视频或固定物理屏幕。
 * 横竖屏切换、系统栏和分屏都必须使用同一套实时边界。
 */
internal class FloatingMenuWindowBoundsProvider(context: Context) {
    private val activity = context.findActivity()
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val resources = context.resources

    /**
     * TYPE_APPLICATION 浮窗在部分 ROM 上会被强制限制到状态栏/挖孔下方。
     * 球体本身仍可贴物理顶部；只有展开菜单时使用这个真实窗口顶部。
     */
    fun currentApplicationWindowTop(): Int {
        activity?.window?.decorView?.let(::getRootWindowInsetsCompat)?.let { insets ->
            return insets.applicationWindowTopInset()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.currentWindowMetrics.windowInsets.applicationWindowTopInset()
        }
        return 0
    }

    fun current(): FloatingMenuWindowBounds {
        val decorView = activity?.window?.decorView
        val decorWidth = decorView?.width ?: 0
        val decorHeight = decorView?.height ?: 0
        if (decorWidth > 0 && decorHeight > 0) {
            return createBounds(
                width = decorWidth,
                height = decorHeight,
                insets = decorView?.let(::getRootWindowInsetsCompat).safeFloatingInsets(),
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                return createBounds(
                    width = bounds.width(),
                    height = bounds.height(),
                    insets = metrics.windowInsets.safeFloatingInsets(),
                )
            }
        }

        @Suppress("DEPRECATION")
        val displayMetrics =
            DisplayMetrics().also { metrics ->
                windowManager.defaultDisplay.getMetrics(metrics)
            }
        if (displayMetrics.widthPixels > 0 && displayMetrics.heightPixels > 0) {
            return createBounds(displayMetrics.widthPixels, displayMetrics.heightPixels, FloatingMenuInsets())
        }

        return createBounds(
            width = resources.displayMetrics.widthPixels,
            height = resources.displayMetrics.heightPixels,
            insets = FloatingMenuInsets(),
        )
    }

    private fun createBounds(
        width: Int,
        height: Int,
        insets: FloatingMenuInsets,
    ): FloatingMenuWindowBounds {
        val left = insets.left.coerceIn(0, width)
        // 顶部悬浮球直接贴真机窗口边缘，不为状态栏或刘海额外预留空白。
        val top = 0
        return FloatingMenuWindowBounds(
            left = left,
            top = top,
            right = (width - insets.right).coerceAtLeast(left),
            bottom = (height - insets.bottom).coerceAtLeast(top),
        )
    }
}

@Suppress("DEPRECATION")
private fun WindowInsets?.safeFloatingInsets(): FloatingMenuInsets {
    this ?: return FloatingMenuInsets()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val insets = getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
        return FloatingMenuInsets(
            left = insets.left,
            right = insets.right,
            bottom = insets.bottom,
        )
    }

    val cutoutInsets = ApiCompatHelper.getDisplayCutoutSafeInsets(this)
    return FloatingMenuInsets(
        left = maxOf(stableInsetLeft, cutoutInsets.left),
        right = maxOf(stableInsetRight, cutoutInsets.right),
        bottom = maxOf(stableInsetBottom, cutoutInsets.bottom),
    )
}

@Suppress("DEPRECATION")
private fun WindowInsets.applicationWindowTopInset(): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
    }
    val cutoutInsets = ApiCompatHelper.getDisplayCutoutSafeInsets(this)
    return maxOf(stableInsetTop, cutoutInsets.top)
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current != null) {
        if (current is Activity) return current
        val baseContext = (current as? ContextWrapper)?.baseContext ?: return null
        if (baseContext === current) return null
        current = baseContext
    }
    return null
}
