package dev.saketanand.setwise.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/** Screen: [ExerciseDetailScreenRoot]. Navigation args: savedStateHandle.toRoute<Route.ExerciseDetail>(). */
class ExerciseDetailViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel()
