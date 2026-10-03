package dev.saketanand.setwise.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * The phone's language for formatting in composables. Unlike Locale.getDefault(), reading it
 * this way redraws the UI when the user changes the language while the app is open.
 */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
