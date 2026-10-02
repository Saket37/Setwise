package dev.saketanand.setwise.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Tabs in the bottom navigation bar, in display order. Add an entry here to add a tab. */
enum class TopLevelDestination(
    val route: Route,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    WORKOUT(Route.Home, "Workout", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    HISTORY(Route.History, "History", Icons.Filled.History, Icons.Outlined.History),
    SETTINGS(Route.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}
