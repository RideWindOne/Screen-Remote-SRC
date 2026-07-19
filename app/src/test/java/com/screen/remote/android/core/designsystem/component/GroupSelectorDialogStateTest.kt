package com.screen.remote.android.core.designsystem.component

import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.domain.model.GroupType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupSelectorDialogStateTest {
    @Test
    fun unavailableAndDuplicateGroupIdsAreRemovedBeforeEditing() {
        val result =
            sanitizeSelectedGroupIds(
                selectedGroupIds = listOf("missing-a", "group-a", "group-a", "missing-b"),
                availableGroupIds = setOf("group-a", "group-b"),
            )

        assertEquals(listOf("group-a"), result)
    }

    @Test
    fun selectingGroupAddsItImmediatelyWithoutSeparateApplyAction() {
        val state = GroupSelectorDialogState(initialSelectedGroupIds = listOf("group-a"))

        state.selectGroup("group-b")

        assertEquals(listOf("group-a", "group-b"), state.tempSelectedIds)
    }

    @Test
    fun selectingReplacementUpdatesEditedGroupImmediately() {
        val state = GroupSelectorDialogState(initialSelectedGroupIds = listOf("group-a", "group-b"))
        state.startEditing(group(id = "group-a", name = "A"))

        state.selectGroup("group-c")

        assertEquals(listOf("group-c", "group-b"), state.tempSelectedIds)
        assertNull(state.editingGroupId)
        assertNull(state.currentSelectedGroupId)
    }

    @Test
    fun selectingAlreadyAddedGroupDoesNotCreateDuplicateWhileEditing() {
        val state = GroupSelectorDialogState(initialSelectedGroupIds = listOf("group-a", "group-b"))
        state.startEditing(group(id = "group-a", name = "A"))

        state.selectGroup("group-b")

        assertEquals(listOf("group-a", "group-b"), state.tempSelectedIds)
        assertEquals("group-a", state.editingGroupId)
    }

    private fun group(
        id: String,
        name: String,
    ) = DeviceGroup(
        id = id,
        name = name,
        type = GroupType.SESSION,
        path = "/$name",
        parentPath = "/",
        description = "",
    )
}
