package com.screen.remote.android.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.TrafficStats
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.util.Base64
import com.screen.remote.android.app.ScreenRemoteApp
import com.screen.remote.android.app.deeplink.ScreenRemoteDeepLink
import com.screen.remote.android.app.deeplink.ManageDestination
import com.screen.remote.android.app.deeplink.ManageSection
import com.screen.remote.android.app.deeplink.SettingsDestination
import com.screen.remote.android.app.deeplink.UrlSetting
import com.screen.remote.android.app.deeplink.toUrl
import com.screen.remote.android.core.common.manager.LiveLogStore
import com.screen.remote.android.core.data.datastore.LocalDecoderCache
import com.screen.remote.android.core.data.datastore.PreferencesManager
import com.screen.remote.android.core.data.repository.SessionRepository
import com.screen.remote.android.core.domain.model.AppSettings
import com.screen.remote.android.infrastructure.scrcpy.client.ScrcpyDiagnosticsRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** Debug-only, read-only diagnostics bridge consumed by the local MCP server. */
class ScreenRemoteDiagnosticsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle =
        runCatching {
            when (method) {
                METHOD_DEVICES -> devicesPayload()
                METHOD_SESSION -> sessionPayload(includeComponents = true)
                METHOD_SOCKETS -> socketPayload()
                METHOD_CODECS -> codecsPayload()
                METHOD_LOGS -> logsPayload()
                METHOD_METRICS -> metricsPayload()
                METHOD_LINKS -> linksPayload()
                else -> error("Unknown diagnostics method: $method")
            }
        }.fold(
            onSuccess = ::encodedResult,
            onFailure = { error ->
                encodedResult(
                    envelope().put("error", error.message ?: error::class.java.simpleName),
                )
            },
        )

    private fun devicesPayload(): JSONObject {
        val devices = ScreenRemoteApp.instance.adbConnectionManager.connectedDevices.value
        return envelope().put(
            "devices",
            JSONArray().apply {
                devices.forEach { device ->
                    put(
                        JSONObject()
                            .put("deviceId", device.deviceId)
                            .put("name", device.name)
                            .put("model", device.model)
                            .put("manufacturer", device.manufacturer)
                            .put("androidVersion", device.androidVersion)
                            .put("serialNumber", device.serialNumber)
                            .put("connectionType", device.connectionType.name),
                    )
                }
            },
        )
    }

    private fun sessionPayload(includeComponents: Boolean): JSONObject {
        val client = ScrcpyDiagnosticsRegistry.currentClient
        val session = client?.sessionManager?.currentOrNull
        return envelope().apply {
            put("available", client != null)
            put("active", session != null)
            put("connectionState", client?.connectionState?.value?.javaClass?.simpleName ?: JSONObject.NULL)
            put("sessionId", session?.sessionId ?: JSONObject.NULL)
            put("deviceId", client?.getCurrentDeviceId() ?: JSONObject.NULL)
            put("sessionState", session?.sessionState?.value?.javaClass?.simpleName ?: JSONObject.NULL)
            client?.videoResolution?.value?.let { resolution ->
                put("videoResolution", JSONObject().put("width", resolution.first).put("height", resolution.second))
            }
            session?.options?.let { options ->
                put(
                    "configuration",
                    JSONObject()
                        .put("audioEnabled", options.config.enableAudio)
                        .put("maxSize", options.config.maxSize)
                        .put("maxFps", options.config.maxFps)
                        .put("videoBitRate", options.config.videoBitRate)
                        .put("audioBitRate", options.config.audioBitRate)
                        .put("tunnelMode", options.config.tunnelMode.name)
                        .put("selectedVideoCodec", options.capabilityCache.selectedVideoCodec)
                        .put("selectedAudioCodec", options.capabilityCache.selectedAudioCodec),
                )
            }
            if (includeComponents && session != null) {
                val snapshot = session.componentSnapshot.value
                put(
                    "components",
                    JSONObject().apply {
                        snapshot.componentStates.forEach { (component, state) ->
                            put(component.name, state.javaClass.simpleName)
                        }
                    },
                )
            }
        }
    }

    private fun socketPayload(): JSONObject {
        val session = ScrcpyDiagnosticsRegistry.currentClient?.sessionManager?.currentOrNull
        return envelope().apply {
            put("active", session != null)
            if (session != null) {
                val sockets = session.componentSnapshot.value.socketConnections
                put("connectedSockets", JSONArray(sockets.connectedSockets.map { it.name }))
                put("expectedSocketCount", sockets.expectedSocketCount)
                put("audioEnabled", sockets.audioEnabled)
                put("allRequiredSocketsConnected", sockets.allRequiredSocketsConnected)
                put("videoSocketConnected", sockets.isVideoSocketConnected)
                put("requiredConnectionOrder", JSONArray(listOf("Video", "Audio", "Control")))
            }
        }
    }

    private fun codecsPayload(): JSONObject {
        val (video, audio) =
            runBlocking(Dispatchers.Default) {
                LocalDecoderCache.getVideoDecoders() to LocalDecoderCache.getAudioDecoders()
            }
        return envelope()
            .put("videoDecoders", JSONArray(video.map(::codecJson)))
            .put("audioDecoders", JSONArray(audio.map(::codecJson)))
    }

    private fun codecJson(codec: com.screen.remote.android.core.domain.model.DecoderCapability): JSONObject =
        JSONObject()
            .put("name", codec.name)
            .put("mimeTypes", JSONArray(codec.mimeTypes))
            .put("acceleration", codec.acceleration.name)
            .put("vendor", codec.isVendor)
            .put("aliasOf", codec.aliasOf ?: JSONObject.NULL)
            .put("lowLatencyMimeTypes", JSONArray(codec.lowLatencyMimeTypes))

    private fun logsPayload(): JSONObject =
        envelope().put(
            "entries",
            JSONArray().apply {
                LiveLogStore.entries.value.forEach { entry ->
                    put(
                        JSONObject()
                            .put("id", entry.id)
                            .put("level", entry.level)
                            .put("tag", entry.tag)
                            .put("message", entry.message),
                    )
                }
            },
        )

    private fun metricsPayload(): JSONObject {
        val client = ScrcpyDiagnosticsRegistry.currentClient
        val uid = Process.myUid()
        return envelope()
            .put("uid", uid)
            .put("networkTxBytes", supportedTrafficValue(TrafficStats.getUidTxBytes(uid)))
            .put("networkRxBytes", supportedTrafficValue(TrafficStats.getUidRxBytes(uid)))
            .put("liveLogEntryCount", LiveLogStore.entries.value.size)
            .put("connectedDeviceCount", ScreenRemoteApp.instance.adbConnectionManager.connectedDevices.value.size)
            .put("sessionActive", client?.sessionManager?.currentOrNull != null)
            .put("videoStreamReady", client?.videoStreamState?.value != null)
            .put("audioStreamReady", client?.audioStreamState?.value != null)
    }

    private fun linksPayload(): JSONObject {
        val (sessions, settings) =
            runBlocking(Dispatchers.IO) {
                SessionRepository(ScreenRemoteApp.instance).sessionDataFlow.first() to
                    PreferencesManager(ScreenRemoteApp.instance).settingsFlow.first()
            }
        return envelope()
            .put("catalogVersion", 2)
            .put(
                "selectionRule",
                "Match the case-sensitive sessionId first, then the first case-sensitive session name, then treat a valid host:port as a transient target.",
            ).put(
                "navigation",
                JSONArray(
                    listOf(
                        linkJson("sessions", ScreenRemoteDeepLink.Sessions.toUrl()),
                        linkJson("newSession", ScreenRemoteDeepLink.AddSession().toUrl()),
                        *SettingsDestination.entries
                            .map { destination ->
                                linkJson(
                                    destination.catalogName(),
                                    ScreenRemoteDeepLink.Settings(destination).toUrl(),
                                )
                            }.toTypedArray(),
                        linkJson("diagnosticLogs", ScreenRemoteDeepLink.DiagnosticLogs.toUrl()),
                        linkJson("disconnect", ScreenRemoteDeepLink.Disconnect.toUrl()),
                        linkJson("generateAdbKeys", ScreenRemoteDeepLink.GenerateAdbKeys.toUrl()),
                    ),
                ),
            ).put(
                "settingsPages",
                JSONArray(
                    SettingsDestination.entries.map { destination ->
                        linkJson(
                            destination.catalogName(),
                            ScreenRemoteDeepLink.Settings(destination).toUrl(),
                        )
                    },
                ),
            ).put(
                "templates",
                JSONObject()
                    .put("scrcpy", "screen-remote://session/{sessionId|name|host:port}/scrcpy?maxFps=120&audio=on")
                    .put(
                        "newSession",
                        "screen-remote://session/new?name={name}&address={address}&color={color}&maxFps=120&audio=on",
                    )
                    .put("editSession", "screen-remote://session/edit/{sessionId|name}")
                    .put("manage", "screen-remote://session/{sessionId|name|host:port}/manage/{section}")
                    .put("manageFile", "screen-remote://session/{sessionId|name|host:port}/manage/file/{path}")
                    .put("manageCommand", "screen-remote://session/{sessionId|name|host:port}/manage/command?command={command}")
                    .put("openSetting", "screen-remote://open/settings/{destination}")
                    .put("setSetting", "screen-remote://setting/{setting}/{value}"),
            ).put(
                "newSessionParameters",
                JSONObject()
                    .put("name", "Non-empty session name")
                    .put("address", "TCP host:port, usb:serial, or mdns:service")
                    .put("color", JSONArray(listOf("blue", "red", "green", "orange", "purple")))
                    .put("profileId", "Non-empty profile ID")
                    .put("useProfileDefaults", JSONArray(listOf("off", "on")))
                    .put("backupAddresses", "Comma-separated TCP, USB, or mDNS addresses")
                    .put("groupIds", "Comma-separated session group IDs")
                    .put("scrcpyParameters", "Accepts every parameter listed in scrcpyParameters"),
            ).put(
                "scrcpyParameters",
                JSONArray(
                    listOf(
                        "maxSize",
                        "videoBitRate",
                        "maxFps",
                        "videoEncoder",
                        "videoDecoder",
                        "audio",
                        "audioBitRate",
                        "audioEncoder",
                        "audioDecoder",
                        "gameMode",
                        "fullScreen",
                        "floatingBall",
                        "hardwareDecoding",
                        "followOrientation",
                        "clipboard",
                        "turnScreenOff",
                        "powerOffOnClose",
                        "cleanupOnDisconnect",
                        "stayAwake",
                        "keepDeviceAwake",
                        "showTouches",
                        "ignoreVideoEncoderConstraints",
                        "displayId",
                        "newDisplayEnabled",
                        "newDisplay",
                        "virtualDisplaySystemDecorations",
                        "preserveVirtualDisplayContent",
                        "startApp",
                        "codecOptions",
                        "tunnelMode",
                    ),
                ),
            ).put(
                "scrcpyParameterAliases",
                JSONObject()
                    .put("enableAudio", "audio")
                    .put("useFullScreen", "fullScreen")
                    .put("showFloatingBall", "floatingBall")
                    .put("enableHardwareDecoding", "hardwareDecoding")
                    .put("followRemoteOrientation", "followOrientation")
                    .put("clipboardSync", "clipboard"),
            ).put(
                "settings",
                JSONArray(UrlSetting.entries.map { settingJson(it, settings) }),
            ).put(
                "management",
                JSONObject()
                    .put(
                        "sections",
                        JSONArray(
                            ManageSection.entries.map { section ->
                                JSONObject()
                                    .put("name", section.name.lowercase().replace('_', '-'))
                                    .put(
                                        "queryParameters",
                                        JSONArray(
                                            when (section) {
                                                ManageSection.FILE -> listOf("path")
                                                ManageSection.COMMAND -> listOf("command")
                                                else -> emptyList()
                                            },
                                        ),
                                    )
                            },
                        ),
                    ).put("commandExecution", "prefill-only"),
            ).put(
                "sessions",
                JSONArray().apply {
                    sessions.forEach { session ->
                        put(
                            JSONObject()
                                .put("sessionId", session.id)
                                .put("name", session.name)
                                .put("scrcpyUrl", ScreenRemoteDeepLink.ScrcpySession(session.id).toUrl())
                                .put("manageUrl", ScreenRemoteDeepLink.ManageSession(session.id).toUrl())
                                .put(
                                    "manageUrls",
                                    JSONObject().apply {
                                        ManageSection.entries.forEach { section ->
                                            put(
                                                section.name.lowercase().replace('_', '-'),
                                                ScreenRemoteDeepLink.ManageSession(
                                                    session.id,
                                                    ManageDestination(section),
                                                ).toUrl(),
                                            )
                                        }
                                    },
                                )
                                .put(
                                    "manageFilesUrl",
                                    ScreenRemoteDeepLink.ManageSession(
                                        session.id,
                                        ManageDestination(ManageSection.FILE),
                                    ).toUrl(),
                                )
                                .put("editUrl", ScreenRemoteDeepLink.EditSession(session.id).toUrl())
                                .put("scrcpyByNameUrl", ScreenRemoteDeepLink.ScrcpySession(session.name).toUrl())
                                .put("manageByNameUrl", ScreenRemoteDeepLink.ManageSession(session.name).toUrl()),
                        )
                    }
                },
            )
    }

    private fun settingJson(
        setting: UrlSetting,
        settings: AppSettings,
    ): JSONObject =
        JSONObject()
            .put("name", setting.path)
            .put("type", setting.type.name.lowercase())
            .put("currentValue", setting.currentValue(settings))
            .put("allowedValues", JSONArray(setting.allowedValues))
            .put(
                "urls",
                JSONObject().apply {
                    setting.allowedValues.forEach { value ->
                        put(value, ScreenRemoteDeepLink.SettingValue(setting.path, value).toUrl())
                    }
                },
            )

    private fun UrlSetting.currentValue(settings: AppSettings): String =
        when (this) {
            UrlSetting.DEBUG_MODE -> settings.enableDebugMode.toUrlBoolean()
            UrlSetting.ACTIVITY_LOG -> settings.enableActivityLog.toUrlBoolean()
            UrlSetting.AUDIO_LOG -> settings.enableAudioStreamLog.toUrlBoolean()
            UrlSetting.VIDEO_LOG -> settings.enableVideoStreamLog.toUrlBoolean()
            UrlSetting.CONTROL_LOG -> settings.enableControlStreamLog.toUrlBoolean()
            UrlSetting.EVENT_LOG -> settings.enableEventStreamLog.toUrlBoolean()
            UrlSetting.SHELL_LOG -> settings.enableShellStreamLog.toUrlBoolean()
            UrlSetting.MANAGEMENT_LOG -> settings.enableManagementLog.toUrlBoolean()
            UrlSetting.HAPTIC -> settings.enableFloatingHapticFeedback.toUrlBoolean()
            UrlSetting.PERFORMANCE_STATS -> settings.showPerformanceStats.toUrlBoolean()
            UrlSetting.AUTO_UPDATE -> settings.autoCheckUpdates.toUrlBoolean()
            UrlSetting.UPDATE_CHANNEL -> settings.updateChannel.name.lowercase()
            UrlSetting.THEME -> settings.themeMode.name.lowercase()
            UrlSetting.LANGUAGE -> settings.language.name.lowercase()
        }

    private fun Boolean.toUrlBoolean(): String = if (this) "on" else "off"

    private fun SettingsDestination.catalogName(): String =
        when (this) {
            SettingsDestination.ROOT -> "settings"
            SettingsDestination.ABOUT -> "settingsAbout"
            SettingsDestination.APPEARANCE -> "settingsAppearance"
            SettingsDestination.LANGUAGE -> "settingsLanguage"
            SettingsDestination.LOGS -> "settingsLogs"
            SettingsDestination.GROUPS -> "settingsGroups"
            SettingsDestination.ADB_KEYS -> "settingsAdbKeys"
            SettingsDestination.BACKUP -> "settingsBackup"
        }

    private fun linkJson(
        name: String,
        url: String,
    ): JSONObject = JSONObject().put("name", name).put("url", url)

    private fun supportedTrafficValue(value: Long): Any =
        if (value == TrafficStats.UNSUPPORTED.toLong() || value < 0L) JSONObject.NULL else value

    private fun envelope(): JSONObject =
        JSONObject()
            .put("schemaVersion", 1)
            .put("generatedAtEpochMs", System.currentTimeMillis())

    private fun encodedResult(payload: JSONObject): Bundle =
        Bundle().apply {
            putString(
                RESULT_PAYLOAD_BASE64,
                Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
            )
        }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException("Diagnostics provider is read-only")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Diagnostics provider is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Diagnostics provider is read-only")

    private companion object {
        const val RESULT_PAYLOAD_BASE64 = "payload_base64"
        const val METHOD_DEVICES = "devices"
        const val METHOD_SESSION = "session"
        const val METHOD_SOCKETS = "sockets"
        const val METHOD_CODECS = "codecs"
        const val METHOD_LOGS = "logs"
        const val METHOD_METRICS = "metrics"
        const val METHOD_LINKS = "links"
    }
}
