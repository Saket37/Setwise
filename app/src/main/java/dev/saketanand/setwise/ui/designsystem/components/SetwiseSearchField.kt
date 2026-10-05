package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * Pill-shaped search box: search icon, [placeholder] while empty, and a clear (✕) button
 * once there's text.
 *
 * Takes a [TextFieldState] (create it with `rememberTextFieldState()`): the text is edited in
 * place, in the same frame as the keystroke, so the cursor never jumps. Observe it with
 * `snapshotFlow { state.text }` to run the search.
 */
@Composable
fun SetwiseSearchField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)

    BasicTextField(
        state = state,
        // The placeholder isn't read out by screen readers, so it's also the field's label.
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = placeholder },
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        lineLimits = TextFieldLineLimits.SingleLine,
        // Results update while typing; the Search key just closes the keyboard.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = { keyboard?.hide() },
        decorator = { innerTextField ->
            Row(
                modifier = Modifier
                    .heightIn(min = 52.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                    .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                HorizontalGap(10.dp)
                Box(modifier = Modifier.weight(1f)) {
                    if (state.text.isEmpty()) {
                        Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    innerTextField()
                }
                if (state.text.isNotEmpty()) {
                    SetwiseIconButton(
                        icon = R.drawable.ic_close,
                        contentDescription = stringResource(R.string.clear_search),
                        onClick = { state.clearText() },
                        size = 40.dp,
                        iconSize = 18.dp,
                        colors = SetwiseIconButtonDefaults.plainColors(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                } else {
                    HorizontalGap(12.dp) // same right inset as the left, without the button
                }
            }
        },
    )
}

@PreviewComponents
@Composable
private fun SetwiseSearchFieldPreview() = SetwisePreview {
    Column {
        SetwiseSearchField(state = rememberTextFieldState(), placeholder = "Search exercises")
        VerticalGap(12.dp)
        SetwiseSearchField(state = rememberTextFieldState("bench"), placeholder = "Search exercises")
    }
}
