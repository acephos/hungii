package com.hungii.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CheckInTest {
    private val tracker = TrackerState(day = "2026-10-27", allowance = 600, spent = 150, opportunities = 2)

    private fun parse(input: String) = parseCheckIn(input, tracker, Preferences())

    @Test fun amountLeftSetsAllowanceAboveTodaysSpending() {
        val result = parse("₹300 left, 2 meals, spicy")
        assertEquals(450, result.tracker.allowance)
        assertEquals(2, result.tracker.opportunities)
        assertEquals("spicy", result.preferences.taste)
        assertEquals("spicy food", result.preferences.query)
        assertEquals(
            listOf(CheckInNote.AmountLeft(300), CheckInNote.MealsLeft(2), CheckInNote.SearchingTaste("spicy")),
            result.notes,
        )
    }

    @Test fun dailyBudgetReplacesTheAllowance() {
        val result = parse("Daily budget rs 800")
        assertEquals(800, result.tracker.allowance)
        assertEquals(listOf<CheckInNote>(CheckInNote.AllowanceUpdated), result.notes)
    }

    @Test fun amountWithoutContextChangesNothing() {
        val result = parse("₹200")
        assertEquals(tracker, result.tracker)
        assertEquals(listOf<CheckInNote>(CheckInNote.AmountNeedsContext), result.notes)
    }

    @Test fun anythingElseBecomesTheNextSearchWithoutGuessingNutrition() {
        val result = parse("  paneer tikka roll  ")
        assertEquals(tracker, result.tracker)
        assertEquals("paneer tikka roll", result.preferences.query)
        assertEquals(listOf<CheckInNote>(CheckInNote.NextSearch("paneer tikka roll")), result.notes)
    }

    @Test fun undoRestoresOnlyWhatACheckInChanged() {
        val day = DayPlan(FakeClock())
        day.restore(tracker, Preferences(query = "dosa"))
        day.checkIn("₹100 left, 1 meal, cheesy")
        day.updateTracker { it.copy(spent = it.spent + 50) }
        day.undoLastCheckIn()
        assertEquals(600, day.tracker.allowance)
        assertEquals(2, day.tracker.opportunities)
        assertEquals(200, day.tracker.spent)
        assertEquals("dosa", day.preferences.query)
        assertEquals(Tastes.ANY, day.preferences.taste)
        assertFalse(day.canUndoCheckIn)
    }
}
