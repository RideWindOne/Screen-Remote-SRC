package com.screen.remote.android.feature.codec.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.designsystem.component.IOSStyledDropdownMenu
import com.screen.remote.android.core.designsystem.component.IOSStyledDropdownMenuItem
import com.screen.remote.android.feature.session.ui.component.CompactTextField

@Composable
fun FilterDropdown(
    label: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppDimens.listItemHeight)
                    .widthIn(min = 50.dp)
                    .clickable { showMenu = true }
                    .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IOSStyledDropdownMenu(
            expanded = showMenu,
            offset = DpOffset(0.dp, 108.dp),
            onDismissRequest = { showMenu = false },
        ) {
            options.forEach { option ->
                IOSStyledDropdownMenuItem(
                    text = option,
                    onClick = {
                        onOptionSelected(option)
                        showMenu = false
                    },
                )
            }
        }
    }
}

@Composable
fun CodecFilterBar(
    searchText: String,
    onSearchChange: (String) -> Unit,
    searchPlaceholder: String,
    filters: List<FilterConfig>,
    searchWeight: Float = 2f,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Surface(
            modifier = Modifier.weight(searchWeight),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            CompactTextField(
                value = searchText,
                onValueChange = onSearchChange,
                placeholder = searchPlaceholder,
            )
        }

        filters.forEach { filter ->
            FilterDropdown(
                label = filter.currentLabel,
                options = filter.options,
                onOptionSelected = filter.onOptionSelected,
                modifier = Modifier.weight(filter.weight),
            )
        }
    }
}

data class FilterConfig(
    val currentLabel: String,
    val options: List<String>,
    val onOptionSelected: (String) -> Unit,
    val weight: Float = 1f,
)
