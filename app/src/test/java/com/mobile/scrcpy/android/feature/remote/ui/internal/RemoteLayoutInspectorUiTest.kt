package com.mobile.scrcpy.android.feature.remote.ui.internal

import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutBounds
import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutLabelSource
import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutNode
import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutNodeKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteLayoutInspectorUiTest {
    @Test
    fun shouldSuppressNodeLabel_doesNotTreatSectionTitleAsCenteredButtonLabel() {
        val packageName = "com.taobao.idlefish"
        val title =
            textNode(
                packageName = packageName,
                label = "其他登录方式",
                bounds = RemoteUiLayoutBounds(438, 1911, 642, 1971),
            )
        val tbLogin =
            imageNode(
                packageName = packageName,
                resourceId = "com.taobao.idlefish:id/ali_user_guide_tb_login_btn",
                label = "TB Login",
                bounds = RemoteUiLayoutBounds(288, 2019, 408, 2139),
            )
        val oneKeyLogin =
            imageNode(
                packageName = packageName,
                resourceId = "com.taobao.idlefish:id/login_onekey_btn",
                label = "Login OneKey",
                bounds = RemoteUiLayoutBounds(480, 2019, 600, 2139),
            )
        val ctidLogin =
            imageNode(
                packageName = packageName,
                resourceId = "com.taobao.idlefish:id/login_ctid_btn",
                label = "Login CTID",
                bounds = RemoteUiLayoutBounds(672, 2019, 792, 2139),
            )

        val nodes = listOf(title, tbLogin, oneKeyLogin, ctidLogin)

        assertFalse(shouldSuppressNodeLabel(oneKeyLogin, nodes))
    }

    @Test
    fun shouldSuppressNodeLabel_suppressesNodeWhenTextDirectlyLabelsImage() {
        val packageName = "com.example"
        val title =
            textNode(
                packageName = packageName,
                label = "Face ID",
                bounds = RemoteUiLayoutBounds(320, 820, 440, 872),
            )
        val faceLogin =
            imageNode(
                packageName = packageName,
                resourceId = "com.example:id/login_faceid_btn",
                label = "Login Faceid",
                bounds = RemoteUiLayoutBounds(320, 880, 440, 1000),
            )

        val nodes = listOf(title, faceLogin)

        assertTrue(shouldSuppressNodeLabel(faceLogin, nodes))
    }

    private fun textNode(
        packageName: String,
        label: String,
        bounds: RemoteUiLayoutBounds,
    ): RemoteUiLayoutNode =
        RemoteUiLayoutNode(
            bounds = bounds,
            label = label,
            labelSource = RemoteUiLayoutLabelSource.TEXT,
            componentKey = null,
            kind = RemoteUiLayoutNodeKind.TEXT,
            packageName = packageName,
            className = "android.widget.TextView",
            resourceId = "",
            text = label,
            contentDescription = "",
            isLeaf = true,
            clickable = false,
            focusable = false,
            focused = false,
            checkable = false,
            checked = false,
            scrollable = false,
            password = false,
            visibleToUser = true,
            hasInputDescendant = false,
            hasTextDescendant = false,
            hasButtonDescendant = false,
            hasCheckIndicatorDescendant = false,
        )

    private fun imageNode(
        packageName: String,
        resourceId: String,
        label: String,
        bounds: RemoteUiLayoutBounds,
    ): RemoteUiLayoutNode =
        RemoteUiLayoutNode(
            bounds = bounds,
            label = label,
            labelSource = RemoteUiLayoutLabelSource.RESOURCE_ID,
            componentKey = resourceId,
            kind = RemoteUiLayoutNodeKind.IMAGE,
            packageName = packageName,
            className = "android.widget.ImageView",
            resourceId = resourceId,
            text = "",
            contentDescription = "",
            isLeaf = true,
            clickable = true,
            focusable = true,
            focused = false,
            checkable = false,
            checked = false,
            scrollable = false,
            password = false,
            visibleToUser = true,
            hasInputDescendant = false,
            hasTextDescendant = false,
            hasButtonDescendant = false,
            hasCheckIndicatorDescendant = false,
        )
}
