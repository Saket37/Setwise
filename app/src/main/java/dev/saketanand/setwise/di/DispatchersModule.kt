// The one place the real dispatchers are created; everything else gets them injected (and tests
// swap in their own).
@file:Suppress("InjectDispatcher")

package dev.saketanand.setwise.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

/** [IoDispatcher], [DefaultDispatcher] and [MainDispatcher]. */
val dispatchersModule = module {
    single<CoroutineDispatcher>(IoDispatcher) { Dispatchers.IO }
    single<CoroutineDispatcher>(DefaultDispatcher) { Dispatchers.Default }
    single<CoroutineDispatcher>(MainDispatcher) { Dispatchers.Main.immediate }
}
