package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.core.common.AppConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbEncoderDetectionCommandTest {
    @Test
    fun `encoder detection keeps the uploaded server for reuse`() {
        val command = buildEncoderDetectionCommand()

        assertTrue(command.startsWith("CLASSPATH=${AppConstants.SCRCPY_SERVER_PATH} "))
        assertTrue(command.contains(" list_encoders=true"))
        assertTrue(command.contains(" cleanup=false"))
        assertFalse(command.contains("scrcpy-server2.jar"))
    }
}
