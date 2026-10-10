package dev.saketanand.setwise.ui.designsystem.preview

import java.time.LocalDate

/**
 * The previews' "now": Tue 6 Oct 2026, 18:00 UTC. [SetwisePreview] sets the app's clocks to it,
 * and sample states are built from it, so previews and their screenshots (#50) don't change
 * with the real date and time.
 */
const val PREVIEW_NOW_MILLIS = 1_791_309_600_000L

/** The day of [PREVIEW_NOW_MILLIS]. */
val PreviewToday: LocalDate = LocalDate.of(2026, 10, 6)

/** The boot clock (rest countdown) in previews. */
const val PREVIEW_BOOT_MILLIS = 3_600_000L
