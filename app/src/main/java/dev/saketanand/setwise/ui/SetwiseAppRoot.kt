package dev.saketanand.setwise.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.saketanand.setwise.ui.navigation.SetwiseNavHost
import dev.saketanand.setwise.ui.navigation.TopLevelDestination

/**
 * App shell: the NavHost, with the bottom navigation bar floating over it on the tabs only.
 *
 * The bar doesn't take its space away from the NavHost (it isn't a Scaffold bottomBar): if it
 * did, showing or hiding it would resize the screen that's mid-transition (e.g. the active
 * workout squashed, rest bar jumping, while going back to Home). Instead the tab screens leave
 * room for it themselves ([TabBarHeight], in SetwiseNavHost) and the bar slides in and out.
 */
@Composable
fun SetwiseAppRoot(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // The bar is hidden on full-screen flows like the active workout or exercise picker.
    val showBottomBar = TopLevelDestination.entries.any { currentDestination.isOn(it) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SetwiseNavHost(
            navController = navController,
            // Status and navigation bars. Plain padding (not consumed), as Scaffold did before:
            // screens still see the full insets, e.g. for the keyboard.
            modifier = Modifier.padding(WindowInsets.systemBars.asPaddingValues()),
        )
        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination.isOn(destination)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTab(destination) },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        }
    }
}

private fun NavDestination?.isOn(tab: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.route::class) } == true

/** Standard tab switch: one copy of each tab on the back stack, each tab's state kept. */
private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
