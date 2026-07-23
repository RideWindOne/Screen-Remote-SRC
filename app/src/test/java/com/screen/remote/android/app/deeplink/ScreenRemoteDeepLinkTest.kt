package com.screen.remote.android.app.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenRemoteDeepLinkTest {
    @Test
    fun `parses navigation links`() {
        assertEquals(ScreenRemoteDeepLink.Sessions, parseScreenRemoteDeepLink("screen-remote://open/sessions"))
        assertEquals(ScreenRemoteDeepLink.Actions, parseScreenRemoteDeepLink("screen-remote://open/actions"))
        assertEquals(
            ScreenRemoteDeepLink.Settings(SettingsDestination.LOGS),
            parseScreenRemoteDeepLink("screen-remote://open/settings/logs"),
        )
        assertEquals(
            ScreenRemoteDeepLink.DiagnosticLogs,
            parseScreenRemoteDeepLink("screen-remote://diagnostics/logs"),
        )
    }

    @Test
    fun `parses new session prefill`() {
        val link =
            ScreenRemoteDeepLink.AddSession(
                NewSessionPrefill(
                    name = "Living room",
                    address = "192.168.1.20:5555",
                    color = "PURPLE",
                    profileId = "profile-1",
                    useProfileDefaults = true,
                    backupAddresses = listOf("192.168.1.21:5555", "usb:serial-1"),
                    groupIds = listOf("group-1", "group-2"),
                    scrcpyParameters =
                        mapOf(
                            "maxFps" to "120",
                            "videoBitRate" to "8M",
                            "audio" to "on",
                        ),
                ),
            )

        assertEquals(link, parseScreenRemoteDeepLink(link.toUrl()))
        assertEquals(
            ScreenRemoteDeepLink.AddSession(NewSessionPrefill(name = "Living room")),
            parseScreenRemoteDeepLink("screen-remote://session/new?name=Living%20room"),
        )
    }

    @Test
    fun `round trips an encoded session id`() {
        listOf(
            ScreenRemoteDeepLink.EditSession("group/session + 1"),
            ScreenRemoteDeepLink.ScrcpySession(
                "group/session + 1",
                mapOf("maxFps" to "120", "videoBitRate" to "8M"),
            ),
            ScreenRemoteDeepLink.ManageSession(
                "group/session + 1",
                ManageDestination(ManageSection.FILE, "/sdcard/Download"),
            ),
        ).forEach { link ->
            assertEquals(link, parseScreenRemoteDeepLink(link.toUrl()))
        }
        assertEquals(
            ScreenRemoteDeepLink.Disconnect,
            parseScreenRemoteDeepLink("screen-remote://remote/disconnect"),
        )
    }

    @Test
    fun `parses endpoint actions and custom parameters`() {
        assertEquals(
            ScreenRemoteDeepLink.ScrcpySession(
                sessionSelector = "192.168.1.20:5555",
                parameters = mapOf("maxFps" to "120", "audio" to "on"),
            ),
            parseScreenRemoteDeepLink(
                "screen-remote://session/192.168.1.20%3A5555/scrcpy?maxFps=120&audio=on",
            ),
        )
        assertEquals(
            ScreenRemoteDeepLink.ScrcpySession(
                sessionSelector = "192.168.1.20:5555",
                parameters = mapOf("compatibilityMode" to "on"),
            ),
            parseScreenRemoteDeepLink(
                "screen-remote://session/192.168.1.20%3A5555/scrcpy?compatibilityMode=on",
            ),
        )
        assertEquals(
            ScreenRemoteDeepLink.ManageSession(
                sessionSelector = "192.168.1.20:5555",
                destination = ManageDestination(ManageSection.FILE, "/sdcard/Download"),
            ),
            parseScreenRemoteDeepLink(
                "screen-remote://session/192.168.1.20%3A5555/manage/file/sdcard/Download",
            ),
        )
        assertEquals(
            ScreenRemoteDeepLink.ManageSession(
                sessionSelector = "Living room",
                destination = ManageDestination(ManageSection.COMMAND),
                parameters = mapOf("command" to "getprop ro.product.model"),
            ),
            parseScreenRemoteDeepLink(
                "screen-remote://session/Living%20room/manage?section=command&command=getprop%20ro.product.model",
            ),
        )
    }

    @Test
    fun `query keys are normalized while routes selectors and values remain case sensitive`() {
        assertNull(parseScreenRemoteDeepLink("SCREEN-REMOTE://session/Z5/scrcpy?maxSize=1080"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://SESSION/Z5/scrcpy?maxSize=1080"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/Z5/SCRCPY?maxSize=1080"))
        assertEquals(
            ScreenRemoteDeepLink.ScrcpySession(
                sessionSelector = "Z5",
                parameters = mapOf("maxSize" to "1080"),
            ),
            parseScreenRemoteDeepLink("screen-remote://session/Z5/scrcpy?MAXSIZE=1080"),
        )
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/Z5/scrcpy?audio=ON"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/NEW?name=Z5"))
        assertEquals(
            ScreenRemoteDeepLink.ManageSession(
                sessionSelector = "Z5",
                destination = ManageDestination(ManageSection.FILE),
            ),
            parseScreenRemoteDeepLink("screen-remote://session/Z5/manage?SECTION=file"),
        )
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/Z5/manage?SECTION=FILE"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://setting/debugmode/ON"))
    }

    @Test
    fun `parses setting and adb commands`() {
        assertEquals(
            ScreenRemoteDeepLink.SettingValue("debugmode", "on"),
            parseScreenRemoteDeepLink("screen-remote://setting/debugmode/on"),
        )
        assertEquals(
            ScreenRemoteDeepLink.SettingValue("debugmode", "true"),
            parseScreenRemoteDeepLink("screen-remote://setting/debugmode/true"),
        )
        assertEquals(
            ScreenRemoteDeepLink.SettingValue("updatechannel", "prerelease"),
            parseScreenRemoteDeepLink("screen-remote://setting/updatechannel/prerelease"),
        )
        assertEquals(
            ScreenRemoteDeepLink.GenerateAdbKeys,
            parseScreenRemoteDeepLink("screen-remote://adb/keys/generate"),
        )
    }

    @Test
    fun `rejects URL authority credentials and ports without affecting encoded targets`() {
        assertNull(parseScreenRemoteDeepLink("screen-remote://user@session/Z5/scrcpy"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://session:123/Z5/scrcpy"))
        assertEquals(
            ScreenRemoteDeepLink.ScrcpySession("[2001:db8::1]:5555"),
            parseScreenRemoteDeepLink(
                "screen-remote://session/%5B2001%3Adb8%3A%3A1%5D%3A5555/scrcpy",
            ),
        )
        assertEquals(
            ScreenRemoteDeepLink.AddSession(NewSessionPrefill(address = "usb:SERIAL")),
            parseScreenRemoteDeepLink("screen-remote://session/new?address=usb%3ASERIAL"),
        )
    }

    @Test
    fun `edit selectors that look like TCP endpoints remain eligible for stored session lookup`() {
        assertEquals(
            ScreenRemoteDeepLink.EditSession("192.168.1.20:5555"),
            parseScreenRemoteDeepLink("screen-remote://session/edit/192.168.1.20%3A5555"),
        )
        assertEquals(
            ScreenRemoteDeepLink.EditSession("[2001:db8::1]:5555"),
            parseScreenRemoteDeepLink(
                "screen-remote://session/edit/%5B2001%3Adb8%3A%3A1%5D%3A5555",
            ),
        )
    }

    @Test
    fun `session selector prefers exact id then first matching name`() {
        data class Candidate(
            val id: String,
            val name: String,
        )

        val firstNamed = Candidate("id-1", "Living room")
        val secondNamed = Candidate("id-2", "Living room")
        val idWins = Candidate("Living room", "Other")
        val candidates = listOf(firstNamed, secondNamed, idWins)

        assertEquals(
            idWins,
            resolveSessionTarget(candidates, "Living room", Candidate::id, Candidate::name),
        )
        assertEquals(
            firstNamed,
            resolveSessionTarget(candidates.take(2), "Living room", Candidate::id, Candidate::name),
        )
        assertNull(resolveSessionTarget(candidates, "LIVING ROOM", Candidate::id, Candidate::name))
    }

    @Test
    fun `rejects unsupported or ambiguous links`() {
        assertNull(parseScreenRemoteDeepLink("https://example.com/open/sessions"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://runtime/session"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/id/delete"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/id/edit"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/new?unknown=value"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/new?address=invalid"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/new?color=cyan"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/new?backupAddresses=invalid"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/new?maxFps=fast"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://open/sessions?connect=true"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://setting/unknown/on"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://setting/theme/blue"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/id/manage?section=unknown"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/id/manage/device?path=%2Fsdcard"))
        assertNull(parseScreenRemoteDeepLink("screen-remote://session/id/manage/file?command=id"))
    }
}
