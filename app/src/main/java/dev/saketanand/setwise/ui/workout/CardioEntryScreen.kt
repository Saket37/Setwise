package dev.saketanand.setwise.ui.workout

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.ui.ObserveAsEvents
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.NumberKind
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseNumberField
import dev.saketanand.setwise.ui.designsystem.components.SetwiseStepper
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTopAppBar
import dev.saketanand.setwise.ui.designsystem.preview.PreviewScreens
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.toWeightInput
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.CardioEntry].
 * @param onBack After logging, or back: the workout.
 */
@Composable
fun CardioEntryScreenRoot(
    onBack: () -> Unit,
    viewModel: CardioEntryViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val back = dropUnlessResumed(block = onBack)
    val fields = rememberCardioFields()

    // Start the fields with this workout's entry, once (they then keep what's typed, also
    // across process death). Empty fields show last time's values as hints.
    var isPrefilled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && !isPrefilled) {
            uiState.logged?.let { fields.fill(it) }
            isPrefilled = true
        }
    }
    LaunchedEffect(fields) {
        merge(*fields.all.map { field -> snapshotFlow { field.text.toString() }.drop(1) }.toTypedArray())
            .collect { viewModel.onAction(CardioEntryAction.OnInputEdited) }
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            // Not through dropUnlessResumed: events arrive from STARTED, where it would ignore them.
            CardioEntryEvent.Logged, CardioEntryEvent.Closed -> onBack()
            // TODO: replace with a snackbar once the screen has a SnackbarHost.
            CardioEntryEvent.SaveFailed -> Toast.makeText(context, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    CardioEntryScreen(
        uiState = uiState,
        fields = fields,
        onAction = { action ->
            when (action) {
                CardioEntryAction.OnBackClick -> back()
                else -> viewModel.onAction(action)
            }
        },
    )
}

/** The screen's text fields. */
class CardioFields(
    val minutes: TextFieldState,
    val seconds: TextFieldState,
    val speedFrom: TextFieldState,
    val speedTo: TextFieldState,
    val distance: TextFieldState,
) {
    val all get() = listOf(minutes, seconds, speedFrom, speedTo, distance)

    fun inputs() = CardioInputs(
        minutes = minutes.text.toString(),
        seconds = seconds.text.toString(),
        speedFrom = speedFrom.text.toString(),
        speedTo = speedTo.text.toString(),
        distance = distance.text.toString(),
    )

    fun fill(values: CardioValues) {
        values.durationSec?.let {
            minutes.setTextAndPlaceCursorAtEnd((it / 60).toString())
            seconds.setTextAndPlaceCursorAtEnd("%02d".format(Locale.ROOT, it % 60))
        }
        values.speedMinKmh?.let { speedFrom.setTextAndPlaceCursorAtEnd(it.toWeightInput()) }
        values.speedMaxKmh?.let { speedTo.setTextAndPlaceCursorAtEnd(it.toWeightInput()) }
        values.distanceKm?.let { distance.setTextAndPlaceCursorAtEnd(it.toWeightInput()) }
    }
}

@Composable
fun rememberCardioFields() = CardioFields(
    minutes = rememberTextFieldState(),
    seconds = rememberTextFieldState(),
    speedFrom = rememberTextFieldState(),
    speedTo = rememberTextFieldState(),
    distance = rememberTextFieldState(),
)

/**
 * Design "Treadmill": last time on top, then the inputs this exercise uses (duration always;
 * incline, speed range, distance, level), and "Log cardio". Empty fields use last time's values.
 * (The calorie estimate comes with milestone 10.)
 */
@Composable
fun CardioEntryScreen(
    uiState: CardioEntryUiState,
    fields: CardioFields,
    onAction: (CardioEntryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val last = uiState.lastTime
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        SetwiseTopAppBar(title = uiState.name, onBack = { onAction(CardioEntryAction.OnBackClick) })
        if (uiState.isLoading) return@Column
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (last != null) {
                Text(
                    text = stringResource(R.string.last_time, last.summary()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DurationSection(fields = fields, last = last)
            if (uiState.error == CardioInputError.MissingDuration) InputError(uiState.error)
            if (CardioMetric.INCLINE in uiState.metrics) {
                StepperSection(
                    title = stringResource(R.string.cardio_incline),
                    unit = stringResource(R.string.cardio_incline_unit),
                ) {
                    SetwiseStepper(
                        value = (uiState.inclinePct / CardioEntryUiState.INCLINE_STEP).roundToInt(),
                        label = NumberFormat.getNumberInstance(currentLocale())
                            .apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }
                            .format(uiState.inclinePct),
                        onDecrease = { onAction(CardioEntryAction.OnInclineChange(-1)) },
                        onIncrease = { onAction(CardioEntryAction.OnInclineChange(+1)) },
                        decreaseDescription = stringResource(R.string.decrease_incline),
                        increaseDescription = stringResource(R.string.increase_incline),
                        range = 0..(CardioEntryUiState.MAX_INCLINE / CardioEntryUiState.INCLINE_STEP).toInt(),
                    )
                }
            }
            if (CardioMetric.SPEED in uiState.metrics) {
                Section(title = stringResource(R.string.cardio_speed), unit = stringResource(R.string.unit_kmh)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabeledField(stringResource(R.string.cardio_speed_from), fields.speedFrom, last?.speedMinKmh?.toWeightInput(), Modifier.weight(1f))
                        LabeledField(stringResource(R.string.cardio_speed_to), fields.speedTo, last?.speedMaxKmh?.toWeightInput(), Modifier.weight(1f))
                    }
                }
                if (uiState.error == CardioInputError.SpeedOrder) InputError(uiState.error)
            }
            if (CardioMetric.DISTANCE in uiState.metrics) {
                Section(title = stringResource(R.string.cardio_distance), unit = stringResource(R.string.unit_km)) {
                    SetwiseNumberField(
                        state = fields.distance,
                        contentDescription = stringResource(R.string.cardio_distance),
                        placeholder = last?.distanceKm?.toWeightInput() ?: "0",
                        kind = NumberKind.Decimal,
                        imeAction = ImeAction.Done,
                        textStyle = MaterialTheme.typography.headlineMedium,
                        minHeight = 56.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (CardioMetric.LEVEL in uiState.metrics) {
                StepperSection(
                    title = stringResource(R.string.cardio_level),
                    unit = stringResource(R.string.cardio_level_unit),
                ) {
                    SetwiseStepper(
                        value = uiState.level,
                        label = uiState.level.toString(),
                        onDecrease = { onAction(CardioEntryAction.OnLevelChange(-1)) },
                        onIncrease = { onAction(CardioEntryAction.OnLevelChange(+1)) },
                        decreaseDescription = stringResource(R.string.decrease_level),
                        increaseDescription = stringResource(R.string.increase_level),
                        range = CardioEntryUiState.LEVEL_RANGE,
                    )
                }
            }
        }
        SetwiseButton(
            text = stringResource(if (uiState.isEditing) R.string.save_cardio else R.string.log_cardio),
            onClick = {
                // Closes the keyboard, so an error under the fields isn't hidden behind it (#137).
                focusManager.clearFocus()
                onAction(CardioEntryAction.OnLogClick(fields.inputs()))
            },
            enabled = !uiState.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        )
    }
}

/** "DURATION   [32] min : [00] sec". */
@Composable
private fun DurationSection(fields: CardioFields, last: CardioValues?) {
    Section(title = stringResource(R.string.cardio_duration)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DurationField(fields.minutes, stringResource(R.string.cardio_minutes), last?.durationSec?.let { (it / 60).toString() } ?: "30")
            UnitLabel(stringResource(R.string.unit_minutes))
            Text(":", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DurationField(fields.seconds, stringResource(R.string.cardio_seconds), last?.durationSec?.let { "%02d".format(Locale.ROOT, it % 60) } ?: "00")
            UnitLabel(stringResource(R.string.unit_sec))
        }
    }
}

@Composable
private fun DurationField(state: TextFieldState, description: String, placeholder: String) {
    SetwiseNumberField(
        state = state,
        contentDescription = description,
        placeholder = placeholder,
        kind = NumberKind.Integer,
        maxLength = 3,
        textStyle = MaterialTheme.typography.headlineMedium,
        minHeight = 56.dp,
        modifier = Modifier.width(88.dp),
    )
}

@Composable
private fun UnitLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LabeledField(label: String, state: TextFieldState, hint: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SetwiseNumberField(
            state = state,
            contentDescription = label,
            placeholder = hint ?: "0",
            kind = NumberKind.Decimal,
            textStyle = MaterialTheme.typography.headlineMedium,
            minHeight = 56.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A rounded block: "Speed range  km/h", then its inputs. */
@Composable
private fun Section(title: String, unit: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            unit?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        content()
    }
}

/** "Incline / percent grade" on the left, the stepper on the right. */
@Composable
private fun StepperSection(title: String, unit: String, stepper: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        stepper()
    }
}

// Previews: one per scenario

@PreviewScreens
@Composable
private fun CardioEntryTreadmillPreview() = SetwiseScreenPreview {
    CardioEntryScreen(
        uiState = CardioEntryUiState(
            isLoading = false,
            name = "Treadmill",
            metrics = setOf(CardioMetric.DURATION, CardioMetric.INCLINE, CardioMetric.SPEED, CardioMetric.DISTANCE),
            lastTime = CardioValues(1_800, inclinePct = 5.0, speedMinKmh = 5.5, speedMaxKmh = 7.5, distanceKm = 3.9),
            inclinePct = 6.0,
        ),
        fields = rememberCardioFields(),
        onAction = {},
    )
}

@PreviewScreens
@Composable
private fun CardioEntryEllipticalPreview() = SetwiseScreenPreview {
    CardioEntryScreen(
        uiState = CardioEntryUiState(
            isLoading = false,
            name = "Elliptical",
            metrics = setOf(CardioMetric.DURATION, CardioMetric.DISTANCE, CardioMetric.LEVEL),
            level = 8,
        ),
        fields = rememberCardioFields(),
        onAction = {},
    )
}
