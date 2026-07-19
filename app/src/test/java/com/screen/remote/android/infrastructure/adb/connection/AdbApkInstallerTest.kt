package com.screen.remote.android.infrastructure.adb.connection

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class AdbApkInstallerTest {
    @Test
    fun `stages apk for installer and removes it after success`() =
        runBlocking {
            val cacheDir = Files.createTempDirectory("apk-installer-test").toFile()
            var stagedPath: String? = null

            val result =
                stageAndInstallApk(
                    cacheDir = cacheDir,
                    openInputStream = { ByteArrayInputStream("apk-data".toByteArray()) },
                    install = { path ->
                        stagedPath = path
                        assertEquals("apk-data", java.io.File(path).readText())
                        Result.success(true)
                    },
                )

            assertTrue(result.getOrThrow())
            assertFalse(java.io.File(requireNotNull(stagedPath)).exists())
            cacheDir.deleteRecursively()
            Unit
        }

    @Test
    fun `removes staged apk when installer fails`() =
        runBlocking {
            val cacheDir = Files.createTempDirectory("apk-installer-test").toFile()
            var stagedPath: String? = null

            val result =
                stageAndInstallApk(
                    cacheDir = cacheDir,
                    openInputStream = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
                    install = { path ->
                        stagedPath = path
                        Result.failure(IllegalStateException("install failed"))
                    },
                )

            assertTrue(result.isFailure)
            assertFalse(java.io.File(requireNotNull(stagedPath)).exists())
            cacheDir.deleteRecursively()
            Unit
        }

    @Test
    fun `fails without leaving a temp apk when uri cannot be read`() =
        runBlocking {
            val cacheDir = Files.createTempDirectory("apk-installer-test").toFile()

            val result =
                stageAndInstallApk(
                    cacheDir = cacheDir,
                    openInputStream = { null },
                    install = { Result.success(true) },
                )

            assertTrue(result.isFailure)
            assertTrue(java.io.File(cacheDir, "adb-apk-install").listFiles().orEmpty().isEmpty())
            cacheDir.deleteRecursively()
            Unit
        }
}
