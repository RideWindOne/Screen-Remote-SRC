package com.screen.remote.android.infrastructure.adb.helper

import android.content.Context
import java.io.File

private const val DADB_HELPER_ASSET_NAME = "dadb-device-helper.jar"

internal object DadbHelperAsset {
    private val extractionLock = Any()

    fun extract(context: Context): File =
        synchronized(extractionLock) {
            val helperDir = File(context.filesDir, "dadb-helpers").apply { mkdirs() }
            val helperFile = File(helperDir, DADB_HELPER_ASSET_NAME)
            val temporaryFile = File(helperDir, "$DADB_HELPER_ASSET_NAME.tmp")

            context.assets.open(DADB_HELPER_ASSET_NAME).use { input ->
                temporaryFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temporaryFile.renameTo(helperFile)) {
                temporaryFile.copyTo(helperFile, overwrite = true)
                temporaryFile.delete()
            }
            check(helperFile.isFile) { "Unable to extract the bundled DADB helper" }
            helperFile
        }
}
