package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/** One entry of a [SetwiseOverflowMenu]. [isDestructive] items are shown in the error colour. */
@Immutable
data class SetwiseMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val isDestructive: Boolean = false,
)

/** ⋮ button that opens a small menu: exercise options ("Remove exercise"), row actions. */
@Composable
fun SetwiseOverflowMenu(
    contentDescription: String,
    items: List<SetwiseMenuItem>,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        SetwiseIconButton(
            icon = R.drawable.ic_more_vert,
            contentDescription = contentDescription,
            onClick = { expanded = true },
            size = size,
            iconSize = 20.dp,
            colors = SetwiseIconButtonDefaults.plainColors(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (item.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

@ComponentPreviews
@Composable
private fun SetwiseOverflowMenuPreview() = SetwisePreview {
    SetwiseOverflowMenu(
        contentDescription = "Exercise options",
        items = listOf(SetwiseMenuItem("Remove exercise", onClick = {}, isDestructive = true)),
    )
}
