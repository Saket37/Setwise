package dev.saketanand.setwise.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.setObserver

/**
 * Counts recompose scopes entered (each restartable composable that runs again) in every
 * composition of a window, sub-compositions such as lazy list items included. A test resets it,
 * changes one thing, and checks how much of the screen that recomposed.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
class RecompositionCounter : CompositionObserver {

    var scopes = 0
        private set

    /** Counts recompositions in [content]'s composition while it's shown. */
    @Composable
    fun Observe(content: @Composable () -> Unit) {
        val composition = currentComposer.composition
        DisposableEffect(composition) {
            val handle: CompositionObserverHandle? = composition.setObserver(this@RecompositionCounter)
            checkNotNull(handle) { "This composition can't be observed" }
            onDispose { handle.dispose() }
        }
        content()
    }

    fun reset() {
        scopes = 0
    }

    override fun onScopeEnter(scope: RecomposeScope) {
        scopes++
    }

    override fun onBeginComposition(composition: ObservableComposition) = Unit
    override fun onReadInScope(scope: RecomposeScope, value: Any) = Unit
    override fun onScopeExit(scope: RecomposeScope) = Unit
    override fun onEndComposition(composition: ObservableComposition) = Unit
    override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) = Unit
    override fun onScopeDisposed(scope: RecomposeScope) = Unit
}
