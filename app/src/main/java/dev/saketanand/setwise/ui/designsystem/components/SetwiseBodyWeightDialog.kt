package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import dev.saketanand.setwise.util.toWeightInput

/**
 * "Body weight  [72.5] kg" with Save / Cancel (and Remove when [onRemove] is given). [onSave]
 * gets the text as typed; the caller validates and sets [isInvalid].
 */
@Composable
fun SetwiseBodyWeightDialog(
    current: Double?,
    isInvalid: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
    message: String? = null,
) {
    val text = rememberTextFieldState(current?.toWeightInput().orEmpty())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.body_weight)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SetwiseNumberField(
                        state = text,
                        contentDescription = stringResource(R.string.a11y_body_weight_kg),
                        placeholder = "70",
                        kind = NumberKind.Decimal,
                        imeAction = ImeAction.Done,
                        onKeyboardAction = { onSave(text.text.toString()) },
                        maxLength = 5,
                        textStyle = MaterialTheme.typography.headlineMedium,
                        minHeight = 56.dp,
                        modifier = Modifier.width(120.dp),
                    )
                    Text(stringResource(R.string.unit_kg), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isInvalid) {
                    Text(stringResource(R.string.onboarding_weight_invalid), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { DialogButton(R.string.save) { onSave(text.text.toString()) } },
        dismissButton = {
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
                DialogButton(R.string.cancel, onDismiss)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun DialogButton(text: Int, onClick: () -> Unit) {
    SetwiseButton(text = stringResource(text), onClick = onClick, style = SetwiseButtonStyle.Text, size = SetwiseButtonSize.Medium)
}

@PreviewComponents
@Composable
private fun SetwiseBodyWeightDialogPreview() = SetwisePreview {
    SetwiseBodyWeightDialog(current = 72.5, isInvalid = false, onSave = {}, onDismiss = {}, onRemove = {})
}
