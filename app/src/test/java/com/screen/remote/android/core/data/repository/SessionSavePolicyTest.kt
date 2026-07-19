package com.screen.remote.android.core.data.repository

import com.screen.remote.android.core.domain.model.ConnectionTransport
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSavePolicyTest {
    @Test
    fun `saving an existing session replaces it without adding a row`() {
        val original = session("session-1", "Phone")
        val updated = original.copy(name = "Phone updated", config = original.config.copy(gameMode = true))

        val result = upsertSessionById(listOf(original), updated)

        assertEquals(listOf(updated), result)
    }

    @Test
    fun `saving a new session appends it`() {
        val original = session("session-1", "Phone")
        val added = session("session-2", "Tablet")

        val result = upsertSessionById(listOf(original), added)

        assertEquals(listOf(original, added), result)
    }

    @Test
    fun `saving collapses duplicate rows with the same session id`() {
        val original = session("session-1", "Phone")
        val duplicate = original.copy(name = "Phone duplicate")
        val updated = original.copy(config = original.config.copy(gameMode = true))

        val result = upsertSessionById(listOf(original, duplicate), updated)

        assertEquals(listOf(updated), result)
    }

    @Test
    fun `deleting a group removes its references and duplicate group ids from sessions`() {
        val first = session("session-1", "Phone").copy(groupIds = listOf("group-a", "group-b", "group-a"))
        val second = session("session-2", "Tablet").copy(groupIds = listOf("group-b"))

        val result = removeGroupReferences(listOf(first, second), "group-a")

        assertEquals(listOf("group-b"), result[0].groupIds)
        assertEquals(listOf("group-b"), result[1].groupIds)
    }

    private fun session(
        id: String,
        name: String,
    ): SessionData =
        SessionData(
            id = id,
            name = name,
            connectionCandidates =
                listOf(
                    ConnectionCandidateData(
                        transport = ConnectionTransport.TCP.name,
                        host = "192.168.1.2",
                        port = 5555,
                    ),
                ),
            color = "BLUE",
        )
}
