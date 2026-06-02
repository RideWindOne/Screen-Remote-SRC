package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RemotePathBreadcrumbTest {
    @Test
    fun buildRemotePathBreadcrumbIncludesRootAndEverySegment() {
        assertEquals(
            listOf(
                RemotePathBreadcrumbItem(label = "/", path = "/"),
                RemotePathBreadcrumbItem(label = "sdcard", path = "/sdcard"),
                RemotePathBreadcrumbItem(label = "Download", path = "/sdcard/Download"),
                RemotePathBreadcrumbItem(label = "My Files", path = "/sdcard/Download/My Files"),
            ),
            buildRemotePathBreadcrumb("/sdcard/Download/My Files"),
        )
    }

    @Test
    fun buildRemotePathBreadcrumbNormalizesRepeatedAndTrailingSlashes() {
        assertEquals(
            listOf(
                RemotePathBreadcrumbItem(label = "/", path = "/"),
                RemotePathBreadcrumbItem(label = "sdcard", path = "/sdcard"),
                RemotePathBreadcrumbItem(label = "Pictures", path = "/sdcard/Pictures"),
            ),
            buildRemotePathBreadcrumb("//sdcard///Pictures//"),
        )
    }

    @Test
    fun buildRemotePathBreadcrumbHandlesRoot() {
        assertEquals(
            listOf(RemotePathBreadcrumbItem(label = "/", path = "/")),
            buildRemotePathBreadcrumb("/"),
        )
    }

    @Test
    fun breadcrumbItemPathCanDriveNavigation() {
        val target = buildRemotePathBreadcrumb("/sdcard/Download/My Files")[2]

        assertEquals("/sdcard/Download", target.path)
    }

    @Test
    fun navigateFileBrowserUpNormalizesBeforeMoving() {
        assertEquals("/sdcard", navigateFileBrowserUp("//sdcard///Download//"))
        assertEquals("/", navigateFileBrowserUp("/sdcard/"))
        assertEquals("/sdcard", navigateFileBrowserUp("/"))
    }

    @Test
    fun parseFindFileBrowserEntryParsesFilesWithSpaces() {
        assertEquals(
            RemoteFileEntry(
                name = "My File.txt",
                fullPath = "/sdcard/Download/My File.txt",
                isDirectory = false,
                sizeBytes = 1536,
                detail = "2026-07-17 10:30",
            ),
            parseFindFileBrowserEntry(
                path = "/sdcard/Download",
                line = "f\t2026-07-17 10:30\t1536\tMy File.txt",
            ),
        )
    }

    @Test
    fun parseFindFileBrowserEntryParsesDirectories() {
        assertEquals(
            RemoteFileEntry(
                name = "Pictures",
                fullPath = "/sdcard/Pictures",
                isDirectory = true,
                sizeBytes = null,
                detail = "2026-07-17 09:08",
            ),
            parseFindFileBrowserEntry(
                path = "/sdcard",
                line = "d\t2026-07-17 09:08\t4096\tPictures",
            ),
        )
    }

    @Test
    fun parseFindFileBrowserEntryIgnoresMalformedRows() {
        assertEquals(null, parseFindFileBrowserEntry(path = "/sdcard", line = "bad row"))
        assertEquals(null, parseFindFileBrowserEntry(path = "/sdcard", line = "f\t2026-07-17 10:30\t."))
        assertEquals(null, parseFindFileBrowserEntry(path = "/sdcard", line = "f\t2026-07-17 10:30\t.."))
        assertEquals(null, parseFindFileBrowserEntry(path = "/sdcard", line = "f\t2026-07-17 10:30\tinvalid\tfile.txt"))
    }

    @Test
    fun parseLsFileBrowserEntryParsesFileSizeAndSpaces() {
        assertEquals(
            RemoteFileEntry(
                name = "My File.txt",
                fullPath = "/sdcard/Download/My File.txt",
                isDirectory = false,
                sizeBytes = 1536,
                detail = "2026-07-17 10:30",
            ),
            parseLsFileBrowserEntry(
                path = "/sdcard/Download",
                line = "-rw-rw---- 1 1023 1023 1536 2026-07-17 10:30 My File.txt",
            ),
        )
    }

    @Test
    fun parseLsFileBrowserEntryHidesDirectorySize() {
        assertEquals(
            RemoteFileEntry(
                name = "Pictures",
                fullPath = "/sdcard/Pictures",
                isDirectory = true,
                sizeBytes = null,
                detail = "2026-07-17 09:08",
            ),
            parseLsFileBrowserEntry(
                path = "/sdcard",
                line = "drwxrws--- 2 1023 1023 4096 2026-07-17 09:08 Pictures/",
            ),
        )
    }
}
