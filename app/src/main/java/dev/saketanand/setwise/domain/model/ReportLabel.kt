package dev.saketanand.setwise.domain.model

/** A label: matches [pattern] and none of [unless] ("Body Fat Mass" isn't body fat %). */
internal class ReportLabel(pattern: String, unless: String? = null) {
    private val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    private val unlessRegex = unless?.let { Regex(it, RegexOption.IGNORE_CASE) }
    fun find(text: String): MatchResult? = if (unlessRegex?.containsMatchIn(text) == true) null else regex.find(text)
}
