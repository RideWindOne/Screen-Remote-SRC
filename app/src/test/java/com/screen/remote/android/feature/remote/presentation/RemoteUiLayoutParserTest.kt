package com.screen.remote.android.feature.remote.presentation

import com.screen.remote.android.feature.remote.model.RemoteUiLayoutNodeKind
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

    @Test
    fun parse_recognizesSemanticAgreementIndicatorsAndSelectedState() {
        val xml =
            """
            <hierarchy rotation="0">
              <node class="android.widget.FrameLayout" package="com.example" bounds="[0,0][1080,2400]" visible-to-user="true">
                <node resource-id="com.example:id/protocol_checkbox" class="android.widget.ImageView" package="com.example" clickable="true" enabled="true" focusable="true" selected="true" bounds="[80,1600][128,1648]" visible-to-user="true" />
                <node class="android.widget.ImageView" package="com.example" content-desc="勾选服务协议和个人信息保护指引" enabled="true" bounds="[80,1700][122,1742]" visible-to-user="true" />
                <node class="android.widget.Button" package="com.example" content-desc="未选中，同意" clickable="true" enabled="true" focusable="true" bounds="[80,1800][128,1848]" visible-to-user="true" />
                <node class="android.widget.ImageView" package="com.example" content-desc="unselected agreement" focusable="true" bounds="[80,1900][128,1948]" visible-to-user="true" />
              </node>
            </hierarchy>
            """.trimIndent()

        val nodes = RemoteUiLayoutParser.parse(xml)
        val selectedToggle = nodes.first { it.resourceId.endsWith("protocol_checkbox") }
        val describedToggle = nodes.first { it.contentDescription.startsWith("勾选") }
        val unselectedToggle = nodes.first { it.contentDescription.startsWith("未选中") }
        val englishUnselectedToggle = nodes.first { it.contentDescription.startsWith("unselected") }

        assertEquals(RemoteUiLayoutNodeKind.TOGGLE, selectedToggle.kind)
        assertTrue(selectedToggle.selected)
        assertTrue(selectedToggle.isEffectivelyChecked)
        assertEquals(RemoteUiLayoutNodeKind.TOGGLE, describedToggle.kind)
        assertEquals(RemoteUiLayoutNodeKind.TOGGLE, unselectedToggle.kind)
        assertFalse(unselectedToggle.isEffectivelyChecked)
        assertFalse(englishUnselectedToggle.isEffectivelyChecked)
    }

    @Test
    fun parse_usesDescendantAccessibilityLabelAndDropsPrivateUseGlyph() {
        val xml =
            """
            <hierarchy rotation="0">
              <node class="android.widget.FrameLayout" package="com.example" bounds="[0,0][1080,2400]" visible-to-user="true">
                <node class="android.widget.EditText" package="com.example" clickable="true" enabled="true" focusable="true" password="true" bounds="[80,600][1000,760]" visible-to-user="true">
                  <node text="&#xE7ED;" class="android.view.View" package="com.example" content-desc="请输入登录密码" bounds="[80,600][1000,760]" visible-to-user="true" />
                </node>
              </node>
            </hierarchy>
            """.trimIndent()

        val input = RemoteUiLayoutParser.parse(xml).single()

        assertEquals(RemoteUiLayoutNodeKind.INPUT, input.kind)
        assertEquals("请输入登录密码", input.label)
    }

    @Test
    fun parse_promotesLabeledCustomLoginWrapperAndPreservesDisabledState() {
        val xml =
            """
            <hierarchy rotation="0">
              <node class="android.widget.FrameLayout" package="com.example" bounds="[0,0][1080,2400]" visible-to-user="true">
                <node resource-id="com.example:id/confirm_btn" class="android.widget.RelativeLayout" package="com.example" clickable="true" enabled="false" focusable="true" bounds="[120,800][960,940]" visible-to-user="true">
                  <node text="登录" class="android.widget.TextView" package="com.example" enabled="false" bounds="[460,830][620,910]" visible-to-user="true" />
                </node>
              </node>
            </hierarchy>
            """.trimIndent()

        val button = RemoteUiLayoutParser.parse(xml).single()

        assertEquals(RemoteUiLayoutNodeKind.BUTTON, button.kind)
        assertEquals("登录", button.label)
        assertFalse(button.enabled)
    }

    @Test
    fun parse_recognizesAgreementImageInObfuscatedGroupAndConfirmContainer() {
        val xml =
            """
            <hierarchy rotation="0">
              <node class="android.widget.FrameLayout" package="com.example" bounds="[0,0][1080,2400]" visible-to-user="true">
                <node resource-id="com.example:id/login_protocol" class="android.widget.LinearLayout" package="com.example" clickable="true" focusable="true" bounds="[60,1500][1020,1640]" visible-to-user="true">
                  <node resource-id="com.example:id/0_resource_name_obfuscated" class="android.widget.ImageView" package="com.example" clickable="true" enabled="true" focusable="true" bounds="[80,1510][116,1546]" visible-to-user="true" />
                  <node text="我已阅读并同意《用户协议》《隐私政策》" class="android.widget.TextView" package="com.example" bounds="[130,1510][960,1600]" visible-to-user="true" />
                </node>
                <node resource-id="com.example:id/confirm_container" class="android.widget.FrameLayout" package="com.example" clickable="true" enabled="true" focusable="true" bounds="[80,1700][140,1844]" visible-to-user="true">
                  <node class="android.widget.ImageView" package="com.example" bounds="[86,1742][134,1790]" visible-to-user="true" />
                </node>
              </node>
            </hierarchy>
            """.trimIndent()

        val nodes = RemoteUiLayoutParser.parse(xml)
        val obfuscatedAgreementToggle = nodes.first { it.resourceId.endsWith("0_resource_name_obfuscated") }
        val confirmContainer = nodes.first { it.resourceId.endsWith("confirm_container") }

        assertEquals(RemoteUiLayoutNodeKind.TOGGLE, obfuscatedAgreementToggle.kind)
        assertTrue(obfuscatedAgreementToggle.label.isBlank())
        assertEquals(RemoteUiLayoutNodeKind.TOGGLE, confirmContainer.kind)
    }
}
