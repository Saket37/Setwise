package dev.saketanand.setwise.di

import org.koin.core.qualifier.named

/**
 * A CoroutineScope that lives as long as the app: for work that must finish even if the screen
 * that started it is closed (e.g. saving the last set typed before leaving the active workout).
 */
val ApplicationScope = named("ApplicationScope")

/**
 * Coroutine dispatchers, injected rather than hard-coded so tests can swap in a test dispatcher:
 * [IoDispatcher] for files and disk, [DefaultDispatcher] for CPU work, [MainDispatcher]
 * (Main.immediate) for state the UI reads.
 */
val IoDispatcher = named("IoDispatcher")
val DefaultDispatcher = named("DefaultDispatcher")
val MainDispatcher = named("MainDispatcher")
