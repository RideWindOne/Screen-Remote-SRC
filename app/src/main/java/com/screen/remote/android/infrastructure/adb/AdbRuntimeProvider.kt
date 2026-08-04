package com.screen.remote.android.infrastructure.adb

import android.content.Context
import dadb.android.runtime.AdbRuntime
import dadb.android.runtime.AdbRuntimeOptions
import dadb.android.runtime.ExperimentalDadbAndroidApi
import java.io.File

@OptIn(ExperimentalDadbAndroidApi::class)
object AdbRuntimeProvider {
    @Volatile
    private var runtime: AdbRuntime? = null

    @Volatile
    private var runtimeRoot: File? = null

    fun init(
        context: Context,
        rootDir: File,
        options: AdbRuntimeOptions = AdbRuntimeOptions(),
    ) {
        if (runtime != null) {
            return
        }

        synchronized(this) {
            if (runtime == null) {
                runtimeRoot = rootDir
                runtime = AdbRuntime(rootDir, options, context.applicationContext)
            }
        }
    }

    fun get(): AdbRuntime {
        return checkNotNull(runtime) { "AdbRuntime is not initialized" }
    }

    fun rootDir(): File {
        return checkNotNull(runtimeRoot) { "AdbRuntime root is not initialized" }
    }
}
