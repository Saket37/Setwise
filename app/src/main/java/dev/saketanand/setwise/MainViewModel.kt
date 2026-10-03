package dev.saketanand.setwise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.ui.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The app's first screen: onboarding until it's been finished or skipped, then Home.
 * Decided once per launch (finishing onboarding mustn't rebuild the navigation graph).
 * MainActivity keeps the splash up while it's still null.
 */
class MainViewModel(userSettingsRepository: UserSettingsRepository) : ViewModel() {

    private val _startDestination = MutableStateFlow<Route?>(null)
    val startDestination: StateFlow<Route?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val done = runCatching { userSettingsRepository.settings.first().onboardingDone }.getOrDefault(true)
            _startDestination.value = if (done) Route.Home else Route.Onboarding
        }
    }
}
