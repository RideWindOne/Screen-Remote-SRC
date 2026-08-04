package com.screen.remote.android.app.deeplink

import com.screen.remote.android.core.domain.model.ScrcpyConfig
import com.screen.remote.android.core.domain.model.SessionColor
import com.screen.remote.android.core.domain.model.parseSessionAddressCandidate
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed interface ScreenRemoteDeepLink {
    data object Sessions : ScreenRemoteDeepLink

    data class AddSession(
        val prefill: NewSessionPrefill = NewSessionPrefill(),
    ) : ScreenRemoteDeepLink

    data class EditSession(
        val sessionSelector: String,
    ) : ScreenRemoteDeepLink

    data class ScrcpySession(
        val sessionSelector: String,
        val parameters: Map<String, String> = emptyMap(),
    ) : ScreenRemoteDeepLink

    data class ManageSession(
        val sessionSelector: String,
        val destination: ManageDestination = ManageDestination(),
        val parameters: Map<String, String> = emptyMap(),
    ) : ScreenRemoteDeepLink

    data class Settings(
        val destination: SettingsDestination = SettingsDestination.ROOT,
    ) : ScreenRemoteDeepLink

    data class SettingValue(
        val setting: String,
        val value: String,
    ) : ScreenRemoteDeepLink

    data object GenerateAdbKeys : ScreenRemoteDeepLink

    data object DiagnosticLogs : ScreenRemoteDeepLink

    data object Disconnect : ScreenRemoteDeepLink
}

data class ManageDestination(
    val section: ManageSection = ManageSection.DEVICE,
    val filePath: String? = null,
)

data class NewSessionPrefill(
    val name: String? = null,
    val address: String? = null,
    val color: String? = null,
    val profileId: String? = null,
    val useProfileDefaults: Boolean? = null,
    val backupAddresses: List<String> = emptyList(),
    val groupIds: List<String> = emptyList(),
    val scrcpyParameters: Map<String, String> = emptyMap(),
) {
    fun toParameters(): Map<String, String> =
        buildMap {
            name?.let { put("name", it) }
            address?.let { put("address", it) }
            color?.let { put("color", it.toUrlSessionColor()) }
            profileId?.let { put("profileId", it) }
            useProfileDefaults?.let { put("useProfileDefaults", if (it) "on" else "off") }
            backupAddresses.takeIf(List<String>::isNotEmpty)?.let { put("backupAddresses", it.joinToString(",")) }
            groupIds.takeIf(List<String>::isNotEmpty)?.let { put("groupIds", it.joinToString(",")) }
            putAll(scrcpyParameters)
        }

    internal fun initialConfig(): ScrcpyConfig =
        ScrcpyConfig().withUrlParameters(scrcpyParameters).getOrThrow()
}

enum class ManageSection(
    internal val path: String,
) {
    DEVICE("device"),
    UTILITY("utility"),
    FILE("file"),
    APP("app"),
    PROCESS("process"),
    PORT_FORWARD("port-forward"),
    COMMAND("command"),
}

enum class SettingsDestination(
    internal val path: String,
) {
    ROOT("settings"),
    ABOUT("settings/about"),
    APPEARANCE("settings/appearance"),
    LANGUAGE("settings/language"),
    LOGS("settings/logs"),
    GROUPS("settings/groups"),
    ADB_KEYS("settings/adb-keys"),
    BACKUP("settings/backup"),
}

private val BOOLEAN_URL_VALUES = listOf("off", "on")

enum class UrlSetting(
    val path: String,
    val type: UrlSettingType,
    val allowedValues: List<String>,
) {
    DEBUG_MODE("debugmode", UrlSettingType.BOOLEAN, BOOLEAN_URL_VALUES),
    ACTIVITY_LOG("activitylog", UrlSettingType.BOOLEAN, BOOLEAN_URL_VALUES),
    AUDIO_LOG("audiolog", UrlSettingType.BOOLEAN, BOOLEAN_URL_VALUES),
    VIDEO_LOG("videolog", UrlSettingType.BOOLEAN, BOOLEAN_URL_VALUES),
    CONTROL_LOG("controllog", UrlSettingType.BOOLEAN, BOOLEAN_URL_VALUES),
    EVENT_LOG("eventlog", UrlSettingType.BOOLEAN, BOOLEAN_URL_VALUES),
    SHELL_LOG("shelllog", UrlSettingType.BOOLEAN, BOOLEAN_URL_VALUES),
    MANAGEMENT_LOG("managementlog", UrlSettingType.BOOLEAN, BOOLEAN_URL_VALUES),
    HAPTIC("haptic", UrlSettingType.BOOLEAN, BOOLEAN_URL_VALUES),
    PERFORMANCE_STATS("performancestats", UrlSettingType.BOOLEAN, BOOLEAN_URL_VALUES),
    AUTO_UPDATE("autoupdate", UrlSettingType.BOOLEAN, BOOLEAN_URL_VALUES),
    UPDATE_CHANNEL("updatechannel", UrlSettingType.ENUM, listOf("stable", "prerelease")),
    THEME("theme", UrlSettingType.ENUM, listOf("system", "light", "dark")),
    LANGUAGE("language", UrlSettingType.ENUM, listOf("auto", "chinese", "english")),
    ;

    fun accepts(value: String): Boolean =
        when (type) {
            UrlSettingType.BOOLEAN -> runCatching { value.requireBoolean(path) }.isSuccess
            UrlSettingType.ENUM -> value in allowedValues
        }

    companion object {
        fun fromName(value: String): UrlSetting? =
            entries.firstOrNull { it.path.equals(value, ignoreCase = true) }
    }
}

enum class UrlSettingType {
    BOOLEAN,
    ENUM,
}

fun parseScreenRemoteDeepLink(value: String): ScreenRemoteDeepLink? =
    runCatching {
        val uri = URI(value)
        if (uri.scheme != SCHEME) return null
        if (uri.rawFragment != null) return null
        if (uri.rawUserInfo != null || uri.port != -1) return null
        val host = uri.host ?: return null
        val segments = uri.pathSegments()
        val parameters = parseQuery(uri.rawQuery)
        when (host) {
            "open" -> if (parameters.isEmpty()) parseOpenLink(segments) else null
            "session" -> parseSessionLink(segments, parameters)
            "setting" -> parseSettingLink(segments, parameters)
            "adb" -> if (segments == listOf(
                    "keys",
                    "generate"
                ) && parameters.isEmpty()
            ) ScreenRemoteDeepLink.GenerateAdbKeys else null

            "diagnostics" -> if (segments == listOf("logs") && parameters.isEmpty()) ScreenRemoteDeepLink.DiagnosticLogs else null
            "remote" -> if (segments == listOf("disconnect") && parameters.isEmpty()) ScreenRemoteDeepLink.Disconnect else null
            else -> null
        }
    }.getOrNull()

fun ScreenRemoteDeepLink.toUrl(): String =
    when (this) {
        ScreenRemoteDeepLink.Sessions -> "$SCHEME://open/sessions"
        is ScreenRemoteDeepLink.AddSession -> "$SCHEME://session/new${prefill.toParameters().toQuery()}"
        is ScreenRemoteDeepLink.EditSession -> "$SCHEME://session/edit/${encodePathSegment(sessionSelector)}"
        is ScreenRemoteDeepLink.ScrcpySession ->
            "$SCHEME://session/${encodePathSegment(sessionSelector)}/scrcpy${parameters.toQuery()}"

        is ScreenRemoteDeepLink.ManageSession -> {
            val destinationPath =
                buildList {
                    add(destination.section.path)
                    destination.filePath
                        ?.trim('/')
                        ?.takeIf(String::isNotBlank)
                        ?.split('/')
                        ?.forEach { add(encodePathSegment(it)) }
                }.joinToString("/")
            "$SCHEME://session/${encodePathSegment(sessionSelector)}/manage/$destinationPath${parameters.toQuery()}"
        }

        is ScreenRemoteDeepLink.Settings -> "$SCHEME://open/${destination.path}"
        is ScreenRemoteDeepLink.SettingValue ->
            "$SCHEME://setting/${encodePathSegment(setting)}/${encodePathSegment(value)}"

        ScreenRemoteDeepLink.GenerateAdbKeys -> "$SCHEME://adb/keys/generate"
        ScreenRemoteDeepLink.DiagnosticLogs -> "$SCHEME://diagnostics/logs"
        ScreenRemoteDeepLink.Disconnect -> "$SCHEME://remote/disconnect"
    }

private fun parseOpenLink(segments: List<String>): ScreenRemoteDeepLink? =
    when (segments.joinToString("/")) {
        "sessions" -> ScreenRemoteDeepLink.Sessions
        SettingsDestination.ROOT.path -> ScreenRemoteDeepLink.Settings()
        SettingsDestination.ABOUT.path -> ScreenRemoteDeepLink.Settings(SettingsDestination.ABOUT)
        SettingsDestination.APPEARANCE.path -> ScreenRemoteDeepLink.Settings(SettingsDestination.APPEARANCE)
        SettingsDestination.LANGUAGE.path -> ScreenRemoteDeepLink.Settings(SettingsDestination.LANGUAGE)
        SettingsDestination.LOGS.path -> ScreenRemoteDeepLink.Settings(SettingsDestination.LOGS)
        SettingsDestination.GROUPS.path -> ScreenRemoteDeepLink.Settings(SettingsDestination.GROUPS)
        SettingsDestination.ADB_KEYS.path -> ScreenRemoteDeepLink.Settings(SettingsDestination.ADB_KEYS)
        SettingsDestination.BACKUP.path -> ScreenRemoteDeepLink.Settings(SettingsDestination.BACKUP)
        else -> null
    }

private fun parseSessionLink(
    segments: List<String>,
    parameters: Map<String, String>,
): ScreenRemoteDeepLink? {
    if (segments == listOf("new")) return parseNewSessionLink(parameters)
    if (segments.size == 2 && segments[0] == "edit" && parameters.isEmpty()) {
        return ScreenRemoteDeepLink.EditSession(segments[1])
    }
    if (segments.size < 2 || segments[0].isBlank()) return null
    val selector = segments[0]
    return when (segments[1]) {
        "scrcpy" ->
            if (segments.size == 2) {
                val normalizedParameters =
                    normalizeScrcpyUrlParameters(parameters).getOrElse { return null }
                if (ScrcpyConfig().withUrlParameters(normalizedParameters).isFailure) return null
                ScreenRemoteDeepLink.ScrcpySession(selector, normalizedParameters)
            } else {
                null
            }

        "manage" -> parseManageLink(selector, segments.drop(2), parameters)
        else -> null
    }
}

private fun parseNewSessionLink(parameters: Map<String, String>): ScreenRemoteDeepLink? {
    val normalizedParameters =
        normalizeParameterKeys(parameters, NEW_SESSION_PARAMETERS + SCRCPY_URL_PARAMETER_NAMES)
            ?: return null
    val name =
        normalizedParameters["name"]?.takeIf(String::isNotBlank)
            ?: if ("name" in normalizedParameters) return null else null
    val address =
        normalizedParameters["address"]?.takeIf(String::isNotBlank)
            ?: if ("address" in normalizedParameters) return null else null
    if (address != null && parseSessionAddressCandidate(address) == null) return null
    val color =
        normalizedParameters["color"]?.let { value ->
            when (value) {
                "blue" -> SessionColor.BLUE.name
                "red" -> SessionColor.RED.name
                "green" -> SessionColor.GREEN.name
                "orange" -> SessionColor.ORANGE.name
                "purple" -> SessionColor.PURPLE.name
                else -> return null
            }
        }
    val profileId =
        normalizedParameters["profileId"]?.takeIf(String::isNotBlank)
            ?: if ("profileId" in normalizedParameters) return null else null
    val useProfileDefaults =
        normalizedParameters["useProfileDefaults"]?.let { value ->
            runCatching { value.requireBoolean("useProfileDefaults") }.getOrElse { return null }
        }
    val backupAddresses =
        normalizedParameters["backupAddresses"]
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
    if (backupAddresses.any { parseSessionAddressCandidate(it) == null }) return null
    val groupIds =
        normalizedParameters["groupIds"]
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()
            .orEmpty()
    if ("groupIds" in normalizedParameters && groupIds.isEmpty()) return null
    val scrcpyParameters =
        normalizeScrcpyUrlParameters(
            normalizedParameters.filterKeys { it in SCRCPY_URL_PARAMETER_NAMES },
        ).getOrElse { return null }
    if (ScrcpyConfig().withUrlParameters(scrcpyParameters).isFailure) return null
    return ScreenRemoteDeepLink.AddSession(
        NewSessionPrefill(
            name = name,
            address = address,
            color = color,
            profileId = profileId,
            useProfileDefaults = useProfileDefaults,
            backupAddresses = backupAddresses,
            groupIds = groupIds,
            scrcpyParameters = scrcpyParameters,
        ),
    )
}

private fun parseManageLink(
    selector: String,
    segments: List<String>,
    parameters: Map<String, String>,
): ScreenRemoteDeepLink? {
    val normalizedParameters = normalizeParameterKeys(parameters, MANAGE_PARAMETERS) ?: return null
    if (segments.isNotEmpty() && "section" in normalizedParameters) return null
    val section =
        if (segments.isEmpty()) {
            normalizedParameters["section"]?.let { requested ->
                ManageSection.entries.firstOrNull { it.path == requested } ?: return null
            } ?: ManageSection.DEVICE
        } else {
            ManageSection.entries.firstOrNull { it.path == segments[0] } ?: return null
        }
    val pathFromSegments =
        if (section == ManageSection.FILE && segments.size > 1) {
            "/" + segments.drop(1).joinToString("/")
        } else {
            null
        }
    if (section != ManageSection.FILE && segments.size > 1) return null
    val allowedParameters =
        when (section) {
            ManageSection.FILE -> setOf("section", "path")
            ManageSection.COMMAND -> setOf("section", "command")
            else -> setOf("section")
        }
    if (normalizedParameters.keys.any { it !in allowedParameters }) return null
    val filePath = normalizedParameters["path"] ?: pathFromSegments
    if (section != ManageSection.FILE && filePath != null) return null
    return ScreenRemoteDeepLink.ManageSession(
        sessionSelector = selector,
        destination = ManageDestination(section = section, filePath = filePath),
        parameters = normalizedParameters - "path" - "section",
    )
}

private fun parseSettingLink(
    segments: List<String>,
    parameters: Map<String, String>,
): ScreenRemoteDeepLink? {
    if (segments.size != 2 || parameters.isNotEmpty()) return null
    val setting = UrlSetting.fromName(segments[0]) ?: return null
    if (!setting.accepts(segments[1])) return null
    return ScreenRemoteDeepLink.SettingValue(setting.path, segments[1])
}

private fun URI.pathSegments(): List<String> =
    rawPath
        .orEmpty()
        .split('/')
        .filter(String::isNotEmpty)
        .map(::decodeComponent)

private fun parseQuery(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    return rawQuery
        .split('&')
        .associate { item ->
            val separator = item.indexOf('=')
            require(separator > 0) { "Query parameters must use key=value" }
            decodeComponent(item.substring(0, separator)) to decodeComponent(item.substring(separator + 1))
        }
}

private fun Map<String, String>.toQuery(): String =
    entries
        .sortedBy(Map.Entry<String, String>::key)
        .joinToString(prefix = if (isEmpty()) "" else "?", separator = "&") { (key, value) ->
            "${encodePathSegment(key)}=${encodePathSegment(value)}"
        }

private fun decodeComponent(value: String): String =
    URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())

private fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun String.toUrlSessionColor(): String =
    when (this) {
        SessionColor.BLUE.name -> "blue"
        SessionColor.RED.name -> "red"
        SessionColor.GREEN.name -> "green"
        SessionColor.ORANGE.name -> "orange"
        SessionColor.PURPLE.name -> "purple"
        else -> error("Unsupported session color: $this")
    }

private const val SCHEME = "screen-remote"
private val NEW_SESSION_PARAMETERS =
    setOf(
        "name",
        "address",
        "color",
        "profileId",
        "useProfileDefaults",
        "backupAddresses",
        "groupIds",
    )
private val MANAGE_PARAMETERS = setOf("section", "path", "command")

private fun normalizeParameterKeys(
    parameters: Map<String, String>,
    supportedKeys: Set<String>,
): Map<String, String>? =
    buildMap {
        parameters.forEach { (key, value) ->
            val canonicalKey =
                supportedKeys.firstOrNull { it.equals(key, ignoreCase = true) }
                    ?: return null
            if (canonicalKey in this) return null
            put(canonicalKey, value)
        }
    }

internal fun <T> resolveSessionTarget(
    candidates: List<T>,
    selector: String,
    sessionId: (T) -> String,
    sessionName: (T) -> String,
): T? =
    candidates.firstOrNull { sessionId(it) == selector }
        ?: candidates.firstOrNull { sessionName(it) == selector }
