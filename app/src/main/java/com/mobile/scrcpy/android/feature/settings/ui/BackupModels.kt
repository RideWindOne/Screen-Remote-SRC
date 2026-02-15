package com.mobile.scrcpy.android.feature.settings.ui

import com.mobile.scrcpy.android.core.data.repository.SessionData
import com.mobile.scrcpy.android.core.domain.model.AppSettings
import kotlinx.serialization.Serializable

@Serializable
internal data class BackupData(
    val version: Int,
    val sessions: List<SessionData>,
    val groups: List<BackupGroupData>,
    val settings: AppSettings,
    val adbKeys: AdbKeysData,
)

@Serializable
internal data class BackupGroupData(
    val id: String,
    val name: String,
    val type: String,
    val path: String,
    val parentPath: String,
    val description: String,
    val createdAt: Long,
)

@Serializable
internal data class AdbKeysData(
    val privateKey: String = "",
    val publicKey: String = "",
    val tlsIdentityStateJson: String = "",
)
