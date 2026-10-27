package com.hungii.prototype

import kotlin.math.abs

/** Menu-price ceiling for the "Menu price under ₹250" filter. */
const val BUDGET_MENU_PRICE = 250

/** Everything the shortlist ranking depends on, besides the candidate meals. */
data class RankingContext(
    val tracker: TrackerState,
    val preferences: Preferences,
    val moneyLeft: Int,
)

/**
 * Orders candidates for the next swipe. Hard filters remove passed, shortlisted,
 * non-vegetarian (when requested) and over-budget menu items. The score then
 * favours the chosen taste and speed, and penalises meals that would overshoot
 * a fair share of the remaining money, protein, carbs, fat and calories.
 */
fun rankMeals(
    meals: List<Meal>,
    passed: Collection<String>,
    finalists: List<Meal>,
    context: RankingContext,
    limit: Int = 8,
): List<Meal> {
    if (context.tracker.opportunities <= 0) return emptyList()
    val prefs = context.preferences
    return meals
        .filter { meal ->
            meal.id !in passed &&
                finalists.none { it.id == meal.id } &&
                (!prefs.vegOnly || meal.veg == true) &&
                (!prefs.budgetOnly || (meal.itemPrice != null && meal.itemPrice <= BUDGET_MENU_PRICE))
        }.sortedByDescending { mealScore(it, context) }
        .take(limit)
}

/** Higher is better. Terms and their float/double mix are kept stable so ordering does not drift. */
internal fun mealScore(
    meal: Meal,
    context: RankingContext,
): Double {
    val tracker = context.tracker
    val prefs = context.preferences
    val mealsLeft = tracker.opportunities.coerceAtLeast(1)
    val moneyShare = context.moneyLeft / mealsLeft
    val nutrition = meal.nutrition
    val proteinLeft = tracker.proteinLeft.mid

    val craving = if (prefs.taste in meal.tags) 28.0 else 0.0
    val speed = if (prefs.fast && meal.etaMinutes != null) 45.0 - meal.etaMinutes else 0.0
    val overBudget = ((meal.estimatedPayable ?: meal.itemPrice ?: moneyShare.toDouble()) - moneyShare).coerceAtLeast(0.0) / 7
    val proteinBoost = if (prefs.highProtein) (nutrition?.protein?.mid ?: 0f) * 1.1 else 0.0
    val proteinMiss = abs((nutrition?.protein?.mid ?: (proteinLeft / mealsLeft)) - proteinLeft.coerceAtLeast(0f) / mealsLeft) / 5
    val carbsOver =
        ((nutrition?.carbs?.high ?: 0) - (tracker.carbGoal - tracker.intake.carbs.mid).coerceAtLeast(0f) / mealsLeft).coerceAtLeast(0f) / 6
    val fatOver =
        ((nutrition?.fat?.high ?: 0) - (tracker.fatGoal - tracker.intake.fat.mid).coerceAtLeast(0f) / mealsLeft).coerceAtLeast(0f) / 3
    val wait = (meal.etaMinutes ?: 25) / 8.0
    val caloriesOver = ((nutrition?.calories?.mid ?: 0f) - tracker.caloriesLeft.mid / mealsLeft).coerceAtLeast(0f) / 30
    return craving + speed - overBudget + proteinBoost - proteinMiss - carbsOver - fatOver - wait - caloriesOver
}
