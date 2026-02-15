package com.mobile.scrcpy.android.feature.remote.presentation

import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutNodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteUiLayoutParserTest {
    @Test
    fun parse_returnsMeaningfulLeafNodes() {
        val xml =
            """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.example" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[0,0][1080,2400]">
                <node index="0" text="登录" resource-id="com.example:id/title" class="android.widget.TextView" package="com.example" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[120,180][360,260]" />
                <node index="1" text="" resource-id="com.example:id/username" class="android.widget.EditText" package="com.example" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="true" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[80,400][1000,520]" />
                <node index="2" text="" resource-id="com.example:id/password" class="android.widget.EditText" package="com.example" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="true" selected="false" visible-to-user="true" bounds="[80,560][1000,680]" />
              </node>
            </hierarchy>
            """.trimIndent()

        val nodes = RemoteUiLayoutParser.parse(xml)

        assertEquals(3, nodes.size)

        val usernameNode = nodes.find { it.resourceId.endsWith("username") }
        val passwordNode = nodes.find { it.resourceId.endsWith("password") }
        val titleNode = nodes.find { it.text == "登录" }

        assertNotNull(usernameNode)
        assertNotNull(passwordNode)
        assertNotNull(titleNode)

        assertEquals(RemoteUiLayoutNodeKind.INPUT, usernameNode?.kind)
        assertEquals(RemoteUiLayoutNodeKind.INPUT, passwordNode?.kind)
        assertEquals(RemoteUiLayoutNodeKind.TEXT, titleNode?.kind)
        assertEquals("Username", usernameNode?.label)
        assertEquals("Password", passwordNode?.label)
        assertEquals("登录", titleNode?.label)
        assertTrue(usernameNode?.focused == true)
        assertEquals(false, usernameNode?.checked)
    }

    @Test
    fun parse_filtersInvisibleContainerNodes() {
        val xml =
            """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.example" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[0,0][1080,2400]">
                <node index="0" text="" resource-id="" class="android.widget.LinearLayout" package="com.example" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[0,0][1080,2400]">
                  <node index="0" text="" resource-id="com.example:id/hidden" class="android.widget.Button" package="com.example" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="false" bounds="[80,800][400,920]" />
                </node>
              </node>
            </hierarchy>
            """.trimIndent()

        val nodes = RemoteUiLayoutParser.parse(xml)

        assertTrue(nodes.isEmpty())
        assertFalse(nodes.any { it.resourceId.endsWith("hidden") })
    }

    @Test
    fun parse_classifiesCompoundButtonAsToggle() {
        val xml =
            """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.example" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[0,0][1080,2400]">
                <node index="0" text="" resource-id="com.example:id/enable_feature" class="android.widget.CompoundButton" package="com.example" content-desc="" checkable="true" checked="true" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[840,200][1000,272]" />
              </node>
            </hierarchy>
            """.trimIndent()

        val nodes = RemoteUiLayoutParser.parse(xml)
        val toggleNode = nodes.single()

        assertEquals(RemoteUiLayoutNodeKind.TOGGLE, toggleNode.kind)
        assertTrue(toggleNode.checkable)
        assertTrue(toggleNode.checked)
    }

    @Test
    fun parse_classifiesSmallClickableScbViewAsToggle() {
        val xml =
            """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.example" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[0,0][1080,2400]">
                <node index="0" text="" resource-id="com.example:id/scb_call_type3" class="android.view.View" package="com.example" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[230,691][275,736]" />
              </node>
            </hierarchy>
            """.trimIndent()

        val nodes = RemoteUiLayoutParser.parse(xml)
        val toggleNode = nodes.single()

        assertEquals(RemoteUiLayoutNodeKind.TOGGLE, toggleNode.kind)
        assertFalse(toggleNode.checkable)
        assertFalse(toggleNode.checked)
    }

    @Test
    fun parse_doesNotClassifyLargeSwitchNamedViewAsToggle() {
        val xml =
            """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.example" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[0,0][1080,2400]">
                <node index="0" text="" resource-id="com.example:id/switch_container" class="android.view.View" package="com.example" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[100,300][820,480]" />
              </node>
            </hierarchy>
            """.trimIndent()

        val nodes = RemoteUiLayoutParser.parse(xml)
        val node = nodes.single()

        assertEquals(RemoteUiLayoutNodeKind.OTHER, node.kind)
    }

    @Test
    fun parse_humanizesOnekeyLoginImageResourceName() {
        val xml =
            """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.taobao.idlefish" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[0,0][1080,2400]">
                <node index="0" text="" resource-id="com.taobao.idlefish:id/login_onekey_btn" class="android.widget.ImageView" package="com.taobao.idlefish" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[480,2019][600,2139]" />
              </node>
            </hierarchy>
            """.trimIndent()

        val nodes = RemoteUiLayoutParser.parse(xml)
        val node = nodes.single()

        assertEquals(RemoteUiLayoutNodeKind.IMAGE, node.kind)
        assertEquals("Login OneKey", node.label)
    }
}
