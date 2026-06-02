package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagementFileCopyTest {
    @Test
    fun `builds copy command with collision checks`() {
        val entries =
            listOf(
                remoteEntry(name = "one file.txt", path = "/sdcard/source/one file.txt"),
                remoteEntry(name = "folder", path = "/sdcard/source/folder", isDirectory = true),
            )

        val command = buildRemoteCopyCommand(entries, "/sdcard/target").getOrThrow()

        assertTrue(command.contains("[ ! -e '/sdcard/target/one file.txt' ]"))
        assertTrue(command.contains("[ ! -e '/sdcard/target/folder' ]"))
        assertTrue(command.contains("cp -R '/sdcard/source/one file.txt' '/sdcard/target'"))
        assertTrue(command.contains("cp -R '/sdcard/source/folder' '/sdcard/target'"))
    }

    @Test
    fun `builds move command with the same collision protection`() {
        val entry = remoteEntry(name = "folder", path = "/sdcard/source/folder", isDirectory = true)

        val command = buildRemoteMoveCommand(listOf(entry), "/sdcard/target").getOrThrow()

        assertTrue(command.contains("[ ! -e '/sdcard/target/folder' ]"))
        assertTrue(command.contains("mv '/sdcard/source/folder' '/sdcard/target'"))
    }

    @Test
    fun `rejects copying a directory into its descendant`() {
        val entry = remoteEntry(name = "folder", path = "/sdcard/folder", isDirectory = true)

        val result = buildRemoteCopyCommand(listOf(entry), "/sdcard/folder/child")

        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects moving a directory into itself`() {
        val entry = remoteEntry(name = "folder", path = "/sdcard/folder", isDirectory = true)

        val result = buildRemoteMoveCommand(listOf(entry), "/sdcard/folder")

        assertTrue(result.isFailure)
    }

    @Test
    fun `quotes apostrophes in copied paths`() {
        val entry = remoteEntry(name = "it's.txt", path = "/sdcard/it's.txt")

        val command = buildRemoteCopyCommand(listOf(entry), "/sdcard/target").getOrThrow()

        assertTrue(command.contains("'/sdcard/it'\"'\"'s.txt'"))
        assertTrue(command.contains("'/sdcard/target/it'\"'\"'s.txt'"))
    }

    private fun remoteEntry(
        name: String,
        path: String,
        isDirectory: Boolean = false,
    ): RemoteFileEntry =
        RemoteFileEntry(
            name = name,
            fullPath = path,
            isDirectory = isDirectory,
            sizeBytes = null,
            detail = "",
        )
}
