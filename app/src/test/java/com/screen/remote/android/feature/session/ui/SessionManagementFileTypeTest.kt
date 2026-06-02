package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionManagementFileTypeTest {
    @Test
    fun `classifies common media and text file types`() {
        assertEquals(RemoteFileKind.Audio, classifyRemoteFileKind("song.mp3"))
        assertEquals(RemoteFileKind.Video, classifyRemoteFileKind("movie.mp4"))
        assertEquals(RemoteFileKind.Image, classifyRemoteFileKind("photo.png"))
        assertEquals(RemoteFileKind.Text, classifyRemoteFileKind("notes.txt"))
        assertEquals(RemoteFileKind.Binary, classifyRemoteFileKind("archive.zip"))
    }

    @Test
    fun `archive name combines up to three selected entry names`() {
        assertEquals(
            "Documents_Movies.zip",
            buildArchiveDownloadName(
                listOf(directory("Documents"), directory("Movies")),
            ),
        )
        assertEquals(
            "Documents_Movies_Pictures.zip",
            buildArchiveDownloadName(
                listOf(directory("Documents"), directory("Movies"), directory("Pictures")),
            ),
        )
    }

    @Test
    fun `archive name appends more when over three entries are selected`() {
        assertEquals(
            "Documents_Movies_Pictures_more.zip",
            buildArchiveDownloadName(
                listOf(
                    directory("Documents"),
                    directory("Movies"),
                    directory("Pictures"),
                    directory("Music"),
                ),
            ),
        )
    }

    @Test
    fun `archive name strips file extensions and sanitizes separators`() {
        assertEquals(
            "release_notes_photo.zip",
            buildArchiveDownloadName(
                listOf(file("release notes.txt"), file("photo.jpg")),
            ),
        )
    }

    private fun directory(name: String): RemoteFileEntry = entry(name, isDirectory = true)

    private fun file(name: String): RemoteFileEntry = entry(name, isDirectory = false)

    private fun entry(
        name: String,
        isDirectory: Boolean,
    ): RemoteFileEntry =
        RemoteFileEntry(
            name = name,
            fullPath = "/sdcard/$name",
            isDirectory = isDirectory,
            sizeBytes = null,
            detail = "",
        )
}
