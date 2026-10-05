package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * "Rename workout": one text field with Save / Cancel. Save is disabled while the text is
 * blank; [onConfirm] gets it trimmed.
 */
@Composable
fun SetwiseTextInputDialog(
    title: String,
    label: String,
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.save),
) {
    // Opens focused with the old text selected: typing replaces it.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length)))
    }
    val text = field.text
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SetwiseTextField(value = field, onValueChange = { field = it }, label = label, focusRequester = focusRequester)
        },
        confirmButton = {
            SetwiseButton(
                text = confirmText,
                onClick = { onConfirm(text.trim()) },
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
                enabled = text.isNotBlank(),
            )
        },
        dismissButton = {
            SetwiseButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@PreviewComponents
@Composable
private fun SetwiseTextInputDialogPreview() = SetwisePreview {
    SetwiseTextInputDialog(title = "Rename workout", label = "Name", initialText = "Workout", onConfirm = {}, onDismiss = {})
}
