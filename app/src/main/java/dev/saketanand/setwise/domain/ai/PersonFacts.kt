package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.domain.model.UserSettings

/**
 * What the on-device writers may know about the person, from their profile: their first name
 * (to address them, at most once) and how many days a week they plan to train (the week's
 * goal). Both optional; with neither, the prompts are as before.
 */
data class PersonFacts(val firstName: String? = null, val plannedDaysPerWeek: Int? = null) {

    /** "Name: Saket", one fact per line like the workout facts. */
    fun nameLine(): String? = firstName?.let { "Name: $it" }

    companion object {
        fun from(settings: UserSettings) = PersonFacts(
            firstName = settings.name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() },
            plannedDaysPerWeek = settings.trainingDays.size.takeIf { it > 0 },
        )
    }
}
