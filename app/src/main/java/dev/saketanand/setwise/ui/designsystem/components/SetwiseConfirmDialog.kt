package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * Yes/no confirmation: "Discard Pull Day?", later "Delete this set?", "Finish without all sets?".
 *
 * @param isDestructive the confirm action deletes something: shown in the error colour.
 * @param onDismiss Cancel, back, or tapping outside.
 */
@Composable
fun SetwiseConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false,
    dismissText: String = stringResource(R.string.cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            SetwiseButton(
                text = confirmText,
                onClick = onConfirm,
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
                colors = if (isDestructive) SetwiseButtonDefaults.destructiveColors(SetwiseButtonStyle.Text)
                else SetwiseButtonDefaults.colors(SetwiseButtonStyle.Text),
            )
        },
        dismissButton = {
            SetwiseButton(
                text = dismissText,
                onClick = onDismiss,
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@ComponentPreviews
@Composable
private fun SetwiseConfirmDialogPreview() = SetwisePreview {
    SetwiseConfirmDialog(
        title = "Discard Pull Day?",
        message = "Pull Day is still in progress (6 sets done). Starting a new workout deletes it.",
        confirmText = "Discard and start",
        onConfirm = {},
        onDismiss = {},
        isDestructive = true,
    )
}
