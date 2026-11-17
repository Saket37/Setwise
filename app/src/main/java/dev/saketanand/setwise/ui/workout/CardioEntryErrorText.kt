package dev.saketanand.setwise.ui.workout

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.saketanand.setwise.R

/** Why the entry can't be logged, under the fields it's about, scrolled into view when it appears. */
@Composable
internal fun InputError(error: CardioInputError) {
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { bringIntoView.bringIntoView() }
    Text(
        text = stringResource(
            when (error) {
                CardioInputError.MissingDuration -> R.string.cardio_missing_duration
                CardioInputError.SpeedOrder -> R.string.cardio_speed_order
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .bringIntoViewRequester(bringIntoView)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
