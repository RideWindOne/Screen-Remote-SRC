package com.screen.remote.android.core.common.util

import android.content.Context

data class LocalDisplaySpec(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
)

fun Context.resolveLocalDisplaySpec(): LocalDisplaySpec {
    val metrics = resources.displayMetrics
    return LocalDisplaySpec(
        width = metrics.widthPixels,
        height = metrics.heightPixels,
        densityDpi = metrics.densityDpi,
    )
}
