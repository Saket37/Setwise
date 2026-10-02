package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.ScreenPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.designsystem.theme.buttonLarge
import dev.saketanand.setwise.ui.designsystem.theme.spacing

@Composable
fun SetwiseButton(
    modifier: Modifier = Modifier,
    onclick: () -> Unit,
    startIcon: Int? = null,
    cornerRadius: Dp = 28.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    borderStroke: BorderStroke? = null,
    text: String,
    enabled: Boolean = true
) {
    Button(
        onClick = onclick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = borderStroke,
        contentPadding = PaddingValues(vertical = MaterialTheme.spacing.md),
        enabled = enabled
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {

            if (startIcon != null) {
                Icon(
                    painterResource(startIcon),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                HorizontalGap(10.dp)
            }
            Text(text = text, style = MaterialTheme.typography.buttonLarge)
        }
    }
}

@ScreenPreviews
@Composable
fun SetwiseButtonPreview() = SetwiseScreenPreview {
    SetwiseButton(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        onclick = {},
        startIcon = R.drawable.ic_play,
        cornerRadius = 28.dp,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        borderStroke = null,
        text = "Start an empty workout"
    )
}