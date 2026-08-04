package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionManagementAppOperationsTest {
    @Test
    fun `running packages collapse child processes and ignore native processes`() {
        assertEquals(
            setOf("com.example.alpha", "org.example.beta"),
            runningPackagesFromProcessNames(
                listOf(
                    "com.example.alpha",
                    "com.example.alpha:worker",
                    "org.example.beta:sync",
                    "surfaceflinger",
                    "invalid package.name",
                ),
            ),
        )
    }

    @Test
    fun `package paths preserve base and split APKs`() {
        assertEquals(
            listOf(
                "/data/app/example/base.apk",
                "/data/app/example/split_config.arm64_v8a.apk",
            ),
            parsePackageApkPaths(
                """
                package:/data/app/example/base.apk
                package:/data/app/example/split_config.arm64_v8a.apk
                package:/data/app/example/base.apk
                warning: ignored
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `split packages use apks extension`() {
        assertEquals("com.example.single.apk", packageExportFileName("com.example.single", listOf("base.apk")))
        assertEquals(
            "com.example.split.apks",
            packageExportFileName("com.example.split", listOf("base.apk", "split.apk")),
        )
    }

    @Test
    fun `package manager failure output is not counted as success`() {
        assertEquals(
            "Failure [DELETE_FAILED_INTERNAL_ERROR]",
            packageActionFailure("Failure [DELETE_FAILED_INTERNAL_ERROR]")
        )
        assertEquals("Error: Unknown package: com.example", packageActionFailure("Error: Unknown package: com.example"))
        assertEquals(null, packageActionFailure("Success"))
        assertEquals(null, packageActionFailure("Package com.example new state: enabled"))
    }
}
