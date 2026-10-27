package com.hungii.prototype

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerTest {
    private val logged =
        TrackerState(
            day = "2026-10-26",
            calorieGoal = 1800,
            allowance = 450,
            spent = 300,
            opportunities = 1,
            intake = nutrition(900, 40, 100, 30),
        )

    @Test fun newDayKeepsGoalsAndAllowanceButClearsTotals() {
        val today = logged.forDay("2026-10-27")
        assertEquals("2026-10-27", today.day)
        assertEquals(1800, today.calorieGoal)
        assertEquals(450, today.allowance)
        assertEquals(0, today.spent)
        assertEquals(2, today.opportunities)
        assertEquals(Nutrition.zero, today.intake)
        assertEquals(logged, logged.forDay("2026-10-26"))
    }

    @Test fun storedTotalsFromAnotherDayAreDropped() {
        val stored = logged.json()
        assertEquals(logged, TrackerState.from(stored, "2026-10-26"))
        val restored = TrackerState.from(stored, "2026-10-27")
        assertEquals(0, restored.spent)
        assertEquals(1800, restored.calorieGoal)
    }

    @Test fun syncedKeyOrderStaysStable() {
        val keys = profileJson(logged, Preferences()).keys().asSequence().toList()
        assertEquals(
            listOf(
                "day",
                "calorieGoal",
                "proteinGoal",
                "carbGoal",
                "fatGoal",
                "allowance",
                "spent",
                "opportunities",
                "intake",
                "preferences",
            ).sorted(),
            keys.sorted(),
        )
    }

    @Test fun loggingAfterMidnightIsSavedUnderTheNewDay() {
        // The app stayed open overnight: totals belong to yesterday until something touches them.
        val clock = FakeClock(FakeClock.at("2026-10-26T23:30"))
        val day = DayPlan(clock)
        day.restore(logged, Preferences())
        clock.set("2026-10-27T08:15")
        day.updateTracker { it.copy(intake = it.intake + nutrition(350, 20, 40, 10)) }
        assertEquals("2026-10-27", day.tracker.day)
        assertEquals(350, day.tracker.intake.calories.low)
        assertEquals(0, day.tracker.spent)
        // Reloading the saved copy the same morning keeps the breakfast.
        assertEquals(
            350,
            TrackerState
                .from(day.tracker.json(), clock.today())
                .intake.calories.low,
        )
    }

    @Test fun ensureTodayReportsOnlyRealChanges() {
        val clock = FakeClock()
        val day = DayPlan(clock)
        assertFalse(day.ensureToday())
        clock.set("2026-10-28T00:00:01")
        assertTrue(day.ensureToday())
        assertEquals("2026-10-28", day.tracker.day)
    }

    @Test fun millisUntilNextDayLandsJustAfterMidnight() {
        val clock = FakeClock(FakeClock.at("2026-10-27T23:59"))
        assertEquals(61_000L, clock.millisUntilNextDay())
    }

    @Test fun preferencesRejectUnknownTastes() {
        val prefs = Preferences.from(JSONObject().put("taste", "salty").put("query", "dal"))
        assertEquals(Tastes.ANY, prefs.taste)
        assertEquals("dal", prefs.query)
        assertEquals(0, prefs.filterCount)
        assertEquals(2, Preferences(taste = "spicy", vegOnly = true).filterCount)
    }

    @Test fun savingTheDayKeepsUntouchedNamesAndClearsCheckInUndo() {
        val day = DayPlan(FakeClock())
        day.checkIn("₹300 left")
        assertTrue(day.canUndoCheckIn)
        day.saveDay(day.tracker.copy(calorieGoal = 2000), "Asha")
        assertFalse(day.canUndoCheckIn)
        assertEquals("Asha", day.preferences.displayName)
        assertEquals(2000, day.tracker.calorieGoal)
    }
}
