package com.screen.remote.android.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ScrcpyProfile(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val config: ScrcpyConfig = defaultProfileConfig(),
) {
    companion object {
        const val DEFAULT_ID = "default"

        fun default(): ScrcpyProfile =
            ScrcpyProfile(
                id = DEFAULT_ID,
                name = "Default",
            )
    }
}

private fun defaultProfileConfig(): ScrcpyConfig =
    ScrcpyConfig(
        maxSize = 1920,
    )

fun ScrcpyOptions.withProfile(profile: ScrcpyProfile): ScrcpyOptions =
    copy(config = profile.config)
