package com.hungii.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantActionsTest {
    private val tracker = TrackerState(day = "2026-10-27")
    private val prefs = Preferences()

    private fun effect(
        action: String,
        value: String = "",
    ) = assistantEffect(AssistantProposal(action, value), tracker, prefs)

    @Test fun valuesInsideTheirRangesApply() {
        assertEquals(AssistantEffect.UpdateDay(tracker.copy(allowance = 350), prefs), effect("set_allowance", "350"))
        assertEquals(AssistantEffect.UpdateDay(tracker.copy(opportunities = 8), prefs), effect("set_opportunities", "8"))
        val logged = effect("log_calories", "420") as AssistantEffect.UpdateDay
        assertEquals(Span(420, 420), logged.tracker.intake.calories)
        val spent = effect("log_spending", "90") as AssistantEffect.UpdateDay
        assertEquals(90, spent.tracker.spent)
    }

    @Test fun outOfRangeOrNonNumericValuesAreRejected() {
        for ((action, value) in listOf(
            "set_opportunities" to "9",
            "set_calorie_goal" to "400",
            "log_fat" to "-1",
            "set_allowance" to "lots",
        )) {
            assertEquals(action, AssistantEffect.Rejected, effect(action, value))
        }
    }

    @Test fun tasteMustBeKnown() {
        assertEquals(AssistantEffect.UpdateDay(tracker, prefs.copy(taste = "light")), effect("set_taste", "light"))
        assertEquals(AssistantEffect.Rejected, effect("set_taste", "umami"))
    }

    @Test fun navigationSearchAndUnknownActions() {
        assertEquals(AssistantEffect.Navigate(Screen.Day), effect("navigate_day"))
        assertEquals(AssistantEffect.Search("masala dosa"), effect("search", "masala dosa"))
        assertEquals(AssistantEffect.Rejected, effect("search", " "))
        assertEquals(AssistantEffect.ReviewCart, effect("review_cart"))
        assertEquals(AssistantEffect.Rejected, effect("place_order", "now"))
    }
}
