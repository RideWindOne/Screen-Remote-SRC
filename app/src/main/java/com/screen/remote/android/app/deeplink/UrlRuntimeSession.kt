package com.screen.remote.android.app.deeplink

import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.domain.model.ScrcpyOptions

internal fun SessionData.toUrlRuntimeSession(
    runtimeId: String,
    parameters: Map<String, String>,
    effectiveOptions: ScrcpyOptions? = null,
): Result<SessionData> {
    val effectiveSession = effectiveOptions?.let { fromScrcpyOptions(it) } ?: this
    return effectiveSession.config.withUrlParameters(parameters).map { config ->
        effectiveSession.copy(
            id = runtimeId,
            profileId = "",
            useProfileDefaults = false,
            config = config,
        )
    }
}
