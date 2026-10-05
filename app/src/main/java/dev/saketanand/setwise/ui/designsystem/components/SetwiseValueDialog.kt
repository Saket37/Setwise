package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import kotlinx.collections.immutable.ImmutableList

/** What a [SetwiseValueDialog] takes: a name, or a whole or decimal number with its unit. */
enum class ValueKind { Text, Integer, Decimal }

/**
 * A setting typed in a dialog: name, age, height… [onSave] gets the text as typed (the caller
 * checks it and sets [errorMessage] if it doesn't fit); [onRemove] clears it (hidden if null).
 */
@Composable
fun SetwiseValueDialog(
    title: String,
    current: String,
    kind: ValueKind,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    unit: String? = null,
    placeholder: String = "",
    errorMessage: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    val text = rememberTextFieldState(current)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (kind == ValueKind.Text) {
                    SetwiseInputBar(state = text, placeholder = placeholder.ifEmpty { title }, onSubmit = { onSave(text.text.toString()) })
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SetwiseNumberField(
                            state = text,
                            contentDescription = title,
                            placeholder = placeholder,
                            kind = if (kind == ValueKind.Decimal) NumberKind.Decimal else NumberKind.Integer,
                            imeAction = ImeAction.Done,
                            onKeyboardAction = { onSave(text.text.toString()) },
                            maxLength = 5,
                            textStyle = MaterialTheme.typography.headlineMedium,
                            minHeight = 56.dp,
                            modifier = Modifier.width(120.dp),
                        )
                        unit?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                errorMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { DialogTextButton(stringResource(R.string.save)) { onSave(text.text.toString()) } },
        dismissButton = { RemoveAndCancel(onRemove, onDismiss) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/** One of a few [options] (labels), or none: e.g. sex for the BMR formula. */
@Composable
fun <T> SetwiseChoiceDialog(
    title: String,
    message: String?,
    options: ImmutableList<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { RemoveAndCancel(onRemove, onDismiss) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun RemoveAndCancel(onRemove: (() -> Unit)?, onDismiss: () -> Unit) {
    Row {
        if (onRemove != null) {
            SetwiseButton(
                text = stringResource(R.string.remove),
                onClick = onRemove,
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
                colors = SetwiseButtonDefaults.destructiveColors(SetwiseButtonStyle.Text),
            )
        }
        DialogTextButton(stringResource(R.string.cancel), onDismiss)
    }
}

@Composable
private fun DialogTextButton(text: String, onClick: () -> Unit) {
    SetwiseButton(text = text, onClick = onClick, style = SetwiseButtonStyle.Text, size = SetwiseButtonSize.Medium)
}

@ComponentPreviews
@Composable
private fun SetwiseValueDialogPreview() = SetwisePreview {
    SetwiseValueDialog(title = "Height", current = "178", kind = ValueKind.Integer, unit = "cm", onSave = {}, onDismiss = {}, onRemove = {})
}
