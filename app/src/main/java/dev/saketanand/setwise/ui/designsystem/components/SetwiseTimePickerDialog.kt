package dev.saketanand.setwise.ui.designsystem.components

import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import java.time.LocalTime

/**
 * Pick a time of day: a workout's start time (start sheet, active workout), later its end.
 * Follows the phone's 12/24-hour setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetwiseTimePickerDialog(
    title: String,
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            SetwiseButton(
                text = stringResource(R.string.ok),
                onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) },
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
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

@ComponentPreviews
@Composable
private fun SetwiseTimePickerDialogPreview() = SetwisePreview {
    SetwiseTimePickerDialog(title = "Start time", initial = LocalTime.of(18, 42), onConfirm = {}, onDismiss = {})
}
