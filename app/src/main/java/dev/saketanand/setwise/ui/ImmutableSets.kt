package dev.saketanand.setwise.ui

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet

/** This set with [element] added, or removed if it's already in it (a toggled chip). */
fun <E> ImmutableSet<E>.toggled(element: E): ImmutableSet<E> =
    (if (element in this) this - element else this + element).toImmutableSet()
