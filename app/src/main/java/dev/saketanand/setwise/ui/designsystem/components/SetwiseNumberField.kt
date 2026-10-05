package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import dev.saketanand.setwise.ui.designsystem.theme.numberMedium

/**
 * Small centred number box for set values ("62.5", "8"), later cardio and template targets.
 * Shows [placeholder] (e.g. last time's value) while empty, and a Volt outline while focused.
 *
 * Only accepts what [kind] allows (digits, plus one decimal separator for [NumberKind.Decimal]),
 * up to [maxLength] characters, so the ViewModel never sees letters.
 *
 * @param contentDescription what the field is, for screen readers ("Weight in kg, set 3").
 */
@Composable
fun SetwiseNumberField(
    state: TextFieldState,
    contentDescription: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    kind: NumberKind = NumberKind.Integer,
    imeAction: ImeAction = ImeAction.Next,
    onKeyboardAction: KeyboardActionHandler? = null,
    maxLength: Int = 6,
    /** numberMedium for set values; larger (e.g. displayLarge) for a single big input. */
    textStyle: TextStyle = MaterialTheme.typography.numberMedium,
    minHeight: Dp = 36.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val fieldTextStyle = textStyle.copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )

    BasicTextField(
        state = state,
        modifier = modifier
            .heightIn(min = minHeight)
            .semantics { this.contentDescription = contentDescription },
        textStyle = fieldTextStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = remember(kind, maxLength) { numberInput(kind, maxLength) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (kind == NumberKind.Decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction,
        ),
        onKeyboardAction = onKeyboardAction,
        interactionSource = interactionSource,
        decorator = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
                    .border(2.dp, if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent, shape),
                contentAlignment = Alignment.Center,
            ) {
                if (state.text.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = fieldTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
        },
    )
}

enum class NumberKind { Integer, Decimal }

/** Rejects any edit that would make the text not a number of [kind], or longer than [maxLength]. */
private fun numberInput(kind: NumberKind, maxLength: Int) = InputTransformation {
    val text = asCharSequence()
    val valid = text.length <= maxLength && when (kind) {
        NumberKind.Integer -> text.all { it.isDigit() }
        // "62.5" or "62,5" (keyboards in many locales type a comma); one separator at most.
        NumberKind.Decimal -> text.all { it.isDigit() || it == '.' || it == ',' } &&
            text.count { it == '.' || it == ',' } <= 1
    }
    if (!valid) revertAllChanges()
}

@PreviewComponents
@Composable
private fun SetwiseNumberFieldPreview() = SetwisePreview {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SetwiseNumberField(rememberTextFieldState(), "Weight", Modifier.width(64.dp), placeholder = "62.5", kind = NumberKind.Decimal)
        SetwiseNumberField(rememberTextFieldState("8"), "Reps", Modifier.width(52.dp), placeholder = "8")
    }
}
