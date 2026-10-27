package com.hungii.prototype

/** A change the cloud Assistant proposed. Nothing applies until the user confirms it. */
data class AssistantProposal(
    val action: String,
    val value: String,
)

sealed interface AssistantEffect {
    data class UpdateDay(
        val tracker: TrackerState,
        val preferences: Preferences,
    ) : AssistantEffect

    data class Search(
        val query: String,
    ) : AssistantEffect

    data class Navigate(
        val screen: Screen,
    ) : AssistantEffect

    data object ReviewCart : AssistantEffect

    data object SaveWinner : AssistantEffect

    /** Unknown action or a value outside its allowed range; nothing changes. */
    data object Rejected : AssistantEffect
}

private fun TrackerState.log(
    value: Int?,
    range: IntRange,
    update: (Int) -> TrackerState,
): TrackerState? = value?.takeIf { it in range }?.let(update)

/** Validates a proposal against the same bounds as manual entry, without side effects. */
fun assistantEffect(
    proposal: AssistantProposal,
    tracker: TrackerState,
    preferences: Preferences,
): AssistantEffect {
    val n = proposal.value.toIntOrNull()
    val intake = tracker.intake
    val updated: TrackerState? =
        when (proposal.action) {
            "set_allowance" -> tracker.log(n, 0..100_000) { tracker.copy(allowance = it) }
            "set_opportunities" -> tracker.log(n, 0..8) { tracker.copy(opportunities = it) }
            "set_calorie_goal" -> tracker.log(n, 500..10_000) { tracker.copy(calorieGoal = it) }
            "set_protein_goal" -> tracker.log(n, 1..1_000) { tracker.copy(proteinGoal = it) }
            "set_carb_goal" -> tracker.log(n, 1..1_500) { tracker.copy(carbGoal = it) }
            "set_fat_goal" -> tracker.log(n, 1..1_000) { tracker.copy(fatGoal = it) }
            "log_calories" -> tracker.log(n, 0..10_000) { tracker.copy(intake = intake.copy(calories = intake.calories + Span.exact(it))) }
            "log_protein" -> tracker.log(n, 0..1_000) { tracker.copy(intake = intake.copy(protein = intake.protein + Span.exact(it))) }
            "log_carbs" -> tracker.log(n, 0..1_500) { tracker.copy(intake = intake.copy(carbs = intake.carbs + Span.exact(it))) }
            "log_fat" -> tracker.log(n, 0..1_000) { tracker.copy(intake = intake.copy(fat = intake.fat + Span.exact(it))) }
            "log_spending" -> tracker.log(n, 0..100_000) { tracker.copy(spent = tracker.spent + it) }
            else -> null
        }
    if (updated != null) return AssistantEffect.UpdateDay(updated, preferences)
    return when (proposal.action) {
        "search" -> {
            proposal.value
                .take(120)
                .takeIf { it.isNotBlank() }
                ?.let(AssistantEffect::Search) ?: AssistantEffect.Rejected
        }

        "set_taste" -> {
            if (proposal.value in Tastes.all) {
                AssistantEffect.UpdateDay(tracker, preferences.copy(taste = proposal.value))
            } else {
                AssistantEffect.Rejected
            }
        }

        "navigate_home" -> {
            AssistantEffect.Navigate(Screen.Home)
        }

        "navigate_day" -> {
            AssistantEffect.Navigate(Screen.Day)
        }

        "navigate_saved" -> {
            AssistantEffect.Navigate(Screen.Saved)
        }

        "review_cart" -> {
            AssistantEffect.ReviewCart
        }

        "save_winner" -> {
            AssistantEffect.SaveWinner
        }

        else -> {
            AssistantEffect.Rejected
        }
    }
}
