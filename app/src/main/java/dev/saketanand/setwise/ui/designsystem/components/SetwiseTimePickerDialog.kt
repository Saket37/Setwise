package dev.saketanand.setwise.ui.designsystem.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import java.time.LocalTime

/**
 * Pick a time of day: a workout's start time (start sheet, active workout), later its end.
 * Follows the phone's 12/24-hour setting. A time [isAllowed] rejects (one in the future) can't be
 * confirmed: the dialog says why ([notAllowedMessage]) and OK is off; the clock can't grey out
 * times itself (#140).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetwiseTimePickerDialog(
    title: String,
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    isAllowed: (LocalTime) -> Boolean = { true },
    notAllowedMessage: String? = null,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    val picked = LocalTime.of(state.hour, state.minute)
    val allowed = isAllowed(picked)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimePicker(state = state)
                if (!allowed && notAllowedMessage != null) {
                    Text(
                        text = notAllowedMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            SetwiseButton(
                text = stringResource(R.string.ok),
                onClick = { onConfirm(picked) },
                enabled = allowed,
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

@PreviewComponents
@Composable
private fun SetwiseTimePickerDialogPreview() = SetwisePreview {
    SetwiseTimePickerDialog(title = "Start time", initial = LocalTime.of(18, 42), onConfirm = {}, onDismiss = {})
}
