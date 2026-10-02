package dev.saketanand.setwise.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/** Screen: [ActiveWorkoutScreenRoot]. Navigation args: savedStateHandle.toRoute<Route.ActiveWorkout>(). */
class ActiveWorkoutViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel()
