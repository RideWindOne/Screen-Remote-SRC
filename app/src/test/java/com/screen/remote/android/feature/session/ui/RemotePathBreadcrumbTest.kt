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

}
