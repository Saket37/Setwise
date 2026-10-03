package dev.saketanand.setwise.di

import org.koin.core.qualifier.named

/**
 * A CoroutineScope that lives as long as the app: for work that must finish even if the screen
 * that started it is closed (e.g. saving the last set typed before leaving the active workout).
 */
val ApplicationScope = named("ApplicationScope")
