package com.mobile.scrcpy.android.feature.remote.model

data class RemoteUiLayoutBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int
        get() = (right - left).coerceAtLeast(0)

    val height: Int
        get() = (bottom - top).coerceAtLeast(0)

    fun hasArea(): Boolean = width > 0 && height > 0

    fun area(): Long = width.toLong() * height.toLong()
}

enum class RemoteUiLayoutNodeKind {
    INPUT,
    BUTTON,
    TEXT,
    TOGGLE,
    IMAGE,
    CONTAINER,
    OTHER,
}

enum class RemoteUiLayoutLabelSource {
    TEXT,
    CONTENT_DESCRIPTION,
    RESOURCE_ID,
    FALLBACK,
}

data class RemoteUiLayoutNode(
    val bounds: RemoteUiLayoutBounds,
    val label: String,
    val labelSource: RemoteUiLayoutLabelSource,
    val componentKey: String?,
    val kind: RemoteUiLayoutNodeKind,
    val packageName: String,
    val className: String,
    val resourceId: String,
    val text: String,
    val contentDescription: String,
    val isLeaf: Boolean,
    val clickable: Boolean,
    val focusable: Boolean,
    val focused: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val scrollable: Boolean,
    val password: Boolean,
    val visibleToUser: Boolean,
    val hasInputDescendant: Boolean,
    val hasTextDescendant: Boolean,
    val hasButtonDescendant: Boolean,
    val hasCheckIndicatorDescendant: Boolean,
) {
    val shortClassName: String
        get() = className.substringAfterLast('.').ifBlank { className }
}

data class RemoteUiLayoutSnapshot(
    val viewportBounds: RemoteUiLayoutBounds,
    val nodes: List<RemoteUiLayoutNode>,
)
