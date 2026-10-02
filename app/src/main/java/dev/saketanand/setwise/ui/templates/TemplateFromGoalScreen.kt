package dev.saketanand.setwise.ui.templates

import androidx.compose.runtime.Composable
import dev.saketanand.setwise.ui.navigation.Route
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.TemplateFromGoal].
 * @param onDraftCreated Opens the draft in the template editor.
 */
@Composable
fun TemplateFromGoalScreenRoot(
    onDraftCreated: (templateId: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: TemplateFromGoalViewModel = koinViewModel(),
) {
    // TODO
}
