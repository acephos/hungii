package com.hungii.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MealRankingTest {
    private val today = TrackerState(day = "2026-10-27")

    private fun context(
        tracker: TrackerState = today,
        preferences: Preferences = Preferences(),
        moneyLeft: Int = tracker.allowance - tracker.spent,
    ) = RankingContext(tracker, preferences, moneyLeft)

    @Test fun hardFiltersRemovePassedShortlistedNonVegAndOverBudgetMeals() {
        val meals =
            listOf(
                meal("passed"),
                meal("kept"),
                meal("chicken", veg = false),
                meal("unknown", veg = null),
                meal("pricey", price = 320.0),
                meal("noprice", price = null),
                meal("ok", price = 250.0),
            )
        val ranked =
            rankMeals(meals, listOf("passed"), listOf(meals[1]), context(preferences = Preferences(vegOnly = true, budgetOnly = true)))
        assertEquals(listOf("ok"), ranked.map { it.id })
    }

    @Test fun noMealsLeftMeansNoCandidates() {
        assertTrue(rankMeals(listOf(meal("a")), emptyList(), emptyList(), context(today.copy(opportunities = 0))).isEmpty())
    }

    @Test fun cravingAndSpeedPreferencesLeadTheOrder() {
        val meals = listOf(meal("plain", eta = 20), meal("spicy", name = "Spicy paneer", eta = 35))
        assertEquals("spicy", rankMeals(meals, emptyList(), emptyList(), context(preferences = Preferences(taste = "spicy")))[0].id)
        assertEquals("plain", rankMeals(meals, emptyList(), emptyList(), context(preferences = Preferences(fast = true)))[0].id)
    }

    @Test fun basketsBeyondAFairShareOfMoneyRankLower() {
        val meals = listOf(meal("expensive", estimatedPayable = 600.0), meal("fair", estimatedPayable = 250.0))
        assertEquals(listOf("fair", "expensive"), rankMeals(meals, emptyList(), emptyList(), context()).map { it.id })
    }

    @Test fun atMostEightCandidates() {
        val meals = (1..12).map { meal("m$it") }
        assertEquals(8, rankMeals(meals, emptyList(), emptyList(), context()).size)
    }

    /** The original inline formula, kept verbatim, so the refactor cannot change ordering. */
    private fun originalScore(
        it: Meal,
        t: TrackerState,
        p: Preferences,
        moneyLeft: Int,
    ): Double {
        val opportunities = t.opportunities
        val mealMoneyGuide = moneyLeft / opportunities.coerceAtLeast(1)
        val proteinLeft = t.proteinLeft
        val caloriesLeft = t.caloriesLeft
        val intake = t.intake
        return (if (p.taste in it.tags) 28.0 else 0.0) + (if (p.fast && it.etaMinutes != null) 45.0 - it.etaMinutes else 0.0) -
            ((it.estimatedPayable ?: it.itemPrice ?: mealMoneyGuide.toDouble()) - mealMoneyGuide).coerceAtLeast(0.0) / 7 +
            (if (p.highProtein) (it.nutrition?.protein?.mid ?: 0f) * 1.1 else 0.0) -
            kotlin.math.abs(
                (it.nutrition?.protein?.mid ?: (proteinLeft.mid / opportunities.coerceAtLeast(1))) -
                    proteinLeft.mid.coerceAtLeast(0f) / opportunities.coerceAtLeast(1),
            ) / 5 -
            (
                (it.nutrition?.carbs?.high ?: 0) - (t.carbGoal - intake.carbs.mid).coerceAtLeast(
                    0f,
                ) / opportunities.coerceAtLeast(1)
            ).coerceAtLeast(0f) /
            6 -
            (
                (it.nutrition?.fat?.high ?: 0) - (t.fatGoal - intake.fat.mid).coerceAtLeast(
                    0f,
                ) / opportunities.coerceAtLeast(1)
            ).coerceAtLeast(0f) /
            3 -
            (it.etaMinutes ?: 25) / 8.0 -
            ((it.nutrition?.calories?.mid ?: 0f) - caloriesLeft.mid / opportunities.coerceAtLeast(1)).coerceAtLeast(0f) / 30
    }

    @Test fun scoreMatchesTheOriginalFormulaExactly() {
        val random = Random(7)
        repeat(2_000) {
            val tracker =
                today.copy(
                    opportunities = random.nextInt(1, 6),
                    spent = random.nextInt(0, 500),
                    intake = nutrition(random.nextInt(0, 2500), random.nextInt(0, 160), random.nextInt(0, 300), random.nextInt(0, 90)),
                )
            val prefs =
                Preferences(
                    taste = Tastes.all.random(random),
                    highProtein = random.nextBoolean(),
                    fast = random.nextBoolean(),
                )
            val candidate =
                meal(
                    id = "m$it",
                    price = if (random.nextInt(5) == 0) null else random.nextInt(80, 500).toDouble(),
                    eta = if (random.nextInt(5) == 0) null else random.nextInt(10, 60),
                    nutrition =
                        if (random.nextBoolean()) {
                            null
                        } else {
                            nutrition(random.nextInt(200, 1200), random.nextInt(5, 60), random.nextInt(10, 120), random.nextInt(2, 50))
                        },
                    name = listOf("Spicy bowl", "Cheesy wrap", "Light salad", "Dal").random(random),
                    estimatedPayable = if (random.nextBoolean()) null else random.nextInt(100, 700).toDouble(),
                )
            val moneyLeft = tracker.allowance - tracker.spent
            assertEquals(
                originalScore(candidate, tracker, prefs, moneyLeft),
                mealScore(candidate, RankingContext(tracker, prefs, moneyLeft)),
                0.0,
            )
        }
    }
}
