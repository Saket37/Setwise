package dev.saketanand.setwise.ui.onboarding

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.ObserveAsEvents
import dev.saketanand.setwise.ui.designsystem.components.IconTile
import dev.saketanand.setwise.ui.designsystem.components.NumberKind
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseDayPicker
import dev.saketanand.setwise.ui.designsystem.components.SetwiseNumberField
import dev.saketanand.setwise.ui.designsystem.components.SetwiseSettingsGroup
import dev.saketanand.setwise.ui.designsystem.components.SetwiseSwitchRow
import dev.saketanand.setwise.ui.designsystem.preview.PreviewScreens
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.navigation.Route
import java.time.DayOfWeek
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.drop
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.Onboarding]. First launch only.
 * @param onFinished Finished or skipped: open the app.
 */
@Composable
fun OnboardingScreenRoot(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    // The weight's text lives here (typing stays in sync); it's sent with Continue.
    val bodyWeight = rememberTextFieldState()

    ObserveAsEvents(viewModel.onFinished) { onFinished() }
    // Back goes to the previous step; on the first step the system handles it (leaves the app).
    BackHandler(enabled = uiState.stepIndex > 0) { viewModel.onAction(OnboardingAction.OnBack) }
    // Clear the "invalid weight" message as soon as the text changes.
    LaunchedEffect(bodyWeight) {
        snapshotFlow { bodyWeight.text.toString() }.drop(1).collect { viewModel.onAction(OnboardingAction.OnBodyWeightEdited) }
    }

    // "Allow notifications": the system prompt; whatever the answer, onboarding moves on.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onAction(OnboardingAction.OnNotificationsAnswered)
    }

    OnboardingScreen(
        uiState = uiState,
        bodyWeight = bodyWeight,
        onAction = { action ->
            // The step only exists on Android 13+ (OnboardingViewModel), where the permission is asked at runtime.
            if (action is OnboardingAction.OnContinue && uiState.step == OnboardingStep.Notifications) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onAction(action)
            }
        },
    )
}

/** One step at a time: step dots and "Skip setup" on top, the question, Continue / Skip at the bottom. */
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    bodyWeight: TextFieldState,
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Keep the buttons above the keyboard (body weight); the Scaffold-less root already pads for the navigation bar.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepDots(current = uiState.stepIndex, count = uiState.stepCount, modifier = Modifier.weight(1f))
            SetwiseButton(
                text = stringResource(R.string.skip_setup),
                onClick = { onAction(OnboardingAction.OnSkipAll) },
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
                textStyle = MaterialTheme.typography.titleSmall,
                enabled = !uiState.isFinishing,
            )
        }

        AnimatedContent(
            targetState = uiState.step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "onboardingStep",
        ) { step ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 48.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    OnboardingStep.Welcome -> StepText(R.string.onboarding_welcome_title, R.string.onboarding_welcome_message)
                    OnboardingStep.TrainingDays -> {
                        StepText(R.string.onboarding_days_title, R.string.onboarding_days_message)
                        SetwiseDayPicker(
                            selected = uiState.trainingDays,
                            onToggle = { onAction(OnboardingAction.OnDayToggle(it)) },
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    OnboardingStep.BodyWeight -> {
                        StepText(R.string.onboarding_weight_title, R.string.onboarding_weight_message)
                        BodyWeightInput(state = bodyWeight, isInvalid = uiState.isBodyWeightInvalid, onDone = {
                            onAction(OnboardingAction.OnContinue(bodyWeight.text.toString()))
                        })
                    }
                    OnboardingStep.CheckIns -> {
                        StepText(R.string.onboarding_checkins_title, R.string.onboarding_checkins_message)
                        SetwiseSettingsGroup(title = null, modifier = Modifier.padding(top = 4.dp)) {
                            SetwiseSwitchRow(
                                label = stringResource(R.string.ask_about_unlogged_days),
                                checked = uiState.askAboutUnloggedDays,
                                onCheckedChange = { onAction(OnboardingAction.OnAskToggle(it)) },
                                showDivider = false,
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_change_later),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OnboardingStep.Notifications -> {
                        StepText(R.string.onboarding_notifications_title, R.string.onboarding_notifications_message)
                        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            NotificationBenefit(
                                icon = R.drawable.ic_timer,
                                title = R.string.onboarding_notifications_countdown_title,
                                message = R.string.onboarding_notifications_countdown_message,
                            )
                            NotificationBenefit(
                                icon = R.drawable.ic_bell,
                                title = R.string.onboarding_notifications_alert_title,
                                message = R.string.onboarding_notifications_alert_message,
                            )
                        }
                        Text(
                            text = stringResource(R.string.onboarding_notifications_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        OnboardingButtons(uiState = uiState, onContinue = {
            onAction(OnboardingAction.OnContinue(bodyWeight.text.toString()))
        }, onAction = onAction)
    }
}

/** The step's title and message, as two rows of the step's Column. */
@Composable
private fun ColumnScope.StepText(title: Int, message: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "⏱ Rest countdown / Time left before your next set…": what the permission is for. */
@Composable
private fun NotificationBenefit(icon: Int, title: Int, message: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(
            icon = icon,
            size = 44.dp,
            iconSize = 22.dp,
            cornerRadius = 12.dp,
            contentColor = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Big "72.5 kg" input; the keyboard's Done continues. */
@Composable
private fun BodyWeightInput(state: TextFieldState, isInvalid: Boolean, onDone: () -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SetwiseNumberField(
                state = state,
                contentDescription = stringResource(R.string.a11y_body_weight_kg),
                placeholder = "70",
                kind = NumberKind.Decimal,
                imeAction = ImeAction.Done,
                onKeyboardAction = { onDone() },
                maxLength = 5,
                textStyle = MaterialTheme.typography.displayLarge,
                minHeight = 80.dp,
                modifier = Modifier.width(160.dp),
            )
            Text(
                text = stringResource(R.string.unit_kg),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isInvalid) {
            Text(
                text = stringResource(R.string.onboarding_weight_invalid),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Primary: "Get started" / "Continue" / "Finish" / "Allow notifications". Secondary: "Skip" (this
 * step; "Not now" for notifications), except on Welcome.
 */
@Composable
private fun OnboardingButtons(uiState: OnboardingUiState, onContinue: () -> Unit, onAction: (OnboardingAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SetwiseButton(
            text = stringResource(
                when {
                    uiState.step == OnboardingStep.Welcome -> R.string.get_started
                    uiState.step == OnboardingStep.Notifications -> R.string.allow_notifications
                    uiState.isLastStep -> R.string.finish_setup
                    else -> R.string.continue_action
                }
            ),
            onClick = onContinue,
            enabled = !uiState.isFinishing,
            modifier = Modifier.fillMaxWidth(),
        )
        // Keeps the layout steady: the Skip button's space is kept on Welcome.
        Box(modifier = Modifier.height(44.dp), contentAlignment = Alignment.Center) {
            if (uiState.step != OnboardingStep.Welcome) {
                SetwiseButton(
                    text = stringResource(if (uiState.step == OnboardingStep.Notifications) R.string.not_now else R.string.skip),
                    onClick = { onAction(OnboardingAction.OnSkipStep) },
                    style = SetwiseButtonStyle.Text,
                    size = SetwiseButtonSize.Medium,
                    enabled = !uiState.isFinishing,
                )
            }
        }
    }
}

/** "● ○ ○ ○": where you are; read out as "Step 1 of 4". */
@Composable
private fun StepDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.a11y_onboarding_step, current + 1, count)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (index == current) 20.dp else 6.dp)
                    .background(
                        if (index <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        CircleShape,
                    ),
            )
        }
    }
}

// Previews: one per step

@PreviewScreens
@Composable
private fun OnboardingWelcomePreview() = SetwiseScreenPreview {
    OnboardingScreen(uiState = OnboardingUiState(), bodyWeight = rememberTextFieldState(), onAction = {})
}

@PreviewScreens
@Composable
private fun OnboardingDaysPreview() = SetwiseScreenPreview {
    OnboardingScreen(
        uiState = OnboardingUiState(step = OnboardingStep.TrainingDays, trainingDays = persistentSetOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
        bodyWeight = rememberTextFieldState(),
        onAction = {},
    )
}

@PreviewScreens
@Composable
private fun OnboardingWeightPreview() = SetwiseScreenPreview {
    OnboardingScreen(uiState = OnboardingUiState(step = OnboardingStep.BodyWeight), bodyWeight = rememberTextFieldState("72.5"), onAction = {})
}

@PreviewScreens
@Composable
private fun OnboardingCheckInsPreview() = SetwiseScreenPreview {
    OnboardingScreen(uiState = OnboardingUiState(step = OnboardingStep.CheckIns), bodyWeight = rememberTextFieldState(), onAction = {})
}

@PreviewScreens
@Composable
private fun OnboardingNotificationsPreview() = SetwiseScreenPreview {
    OnboardingScreen(uiState = OnboardingUiState(step = OnboardingStep.Notifications), bodyWeight = rememberTextFieldState(), onAction = {})
}
