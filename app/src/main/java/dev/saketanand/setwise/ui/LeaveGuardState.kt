package dev.saketanand.setwise.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.components.SetwiseConfirmDialog

/**
 * Leaving a screen with something entered asks "Discard changes?" first (Keep editing /
 * Discard), from the system back as from the screen's own back arrow ([leave]); without changes
 * it just goes (#139).
 */
@Stable
class LeaveGuardState internal constructor(isAsking: Boolean) {
    var isAsking by mutableStateOf(isAsking)
        internal set

    /** The back arrow: asks first when there are changes, else [onLeave]. */
    fun leave(hasChanges: Boolean, onLeave: () -> Unit) {
        if (hasChanges) isAsking = true else onLeave()
    }
}

/** Kept across rotation: an open "Discard changes?" stays open. */
@Composable
fun rememberLeaveGuardState(): LeaveGuardState = rememberSaveable(saver = LeaveGuardSaver) { LeaveGuardState(isAsking = false) }

private val LeaveGuardSaver = Saver<LeaveGuardState, Boolean>(save = { it.isAsking }, restore = { LeaveGuardState(it) })

/** The system back while [hasChanges], and the dialog once asked. [onLeave]: discarded, go. */
@Composable
fun LeaveGuard(state: LeaveGuardState, hasChanges: Boolean, onLeave: () -> Unit) {
    BackHandler(enabled = hasChanges) { state.isAsking = true }
    if (state.isAsking) {
        SetwiseConfirmDialog(
            title = stringResource(R.string.discard_changes_title),
            message = stringResource(R.string.discard_entered_message),
            confirmText = stringResource(R.string.discard),
            dismissText = stringResource(R.string.keep_editing),
            onConfirm = {
                state.isAsking = false
                onLeave()
            },
            onDismiss = { state.isAsking = false },
            isDestructive = true,
        )
    }
}
