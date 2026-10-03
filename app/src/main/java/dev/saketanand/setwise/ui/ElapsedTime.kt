package dev.saketanand.setwise.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Time since [startedAtMillis], updated once a second while on screen (the Resume card,
 * the active workout clock). Read it in the smallest composable that shows it, so each tick
 * redraws only that text.
 */
@Composable
fun rememberElapsedTime(startedAtMillis: Long): Duration {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            // Wake on the next whole second of elapsed time, so the clock doesn't drift.
            delay(1_000 - (now - startedAtMillis).mod(1_000L))
        }
    }
    return (now - startedAtMillis).coerceAtLeast(0).milliseconds
}
