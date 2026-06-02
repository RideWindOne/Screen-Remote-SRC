package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalInstalledAppTest {
    @Test
    fun `collect installed apk paths keeps base and readable splits in install order`() {
        val readable = setOf("/app/base.apk", "/app/config.arm64.apk")

        assertEquals(
            listOf("/app/base.apk", "/app/config.arm64.apk"),
            collectInstalledApkPaths(
                sourceDir = "/app/base.apk",
                splitSourceDirs = arrayOf("/app/config.arm64.apk", "/app/missing.apk"),
                isFile = readable::contains,
            ),
        )
    }

    @Test
    fun `collect installed apk paths removes duplicate paths`() {
        assertEquals(
            listOf("/app/base.apk"),
            collectInstalledApkPaths(
                sourceDir = "/app/base.apk",
                splitSourceDirs = arrayOf("/app/base.apk"),
                isFile = { true },
            ),
        )
    }
}
