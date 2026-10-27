package com.hungii.prototype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Today's tracker and the user's preferences. Every change first moves the tracker to today. */
class DayPlan(
    private val clock: Clock,
) {
    var tracker by mutableStateOf(TrackerState(clock.today()))
        private set
    var preferences by mutableStateOf(Preferences())
        private set

    /** The result of the latest free-text check-in, shown with an undo. */
    var checkInReceipt by mutableStateOf<UiText?>(null)
        private set
    private var undoCheckIn by mutableStateOf<(() -> Unit)?>(null)
    val canUndoCheckIn get() = undoCheckIn != null

    /** Starts a new day when the date has changed, keeping goals and allowance. */
    fun ensureToday(): Boolean {
        val today = clock.today()
        if (tracker.day == today) return false
        tracker = tracker.forDay(today)
        undoCheckIn = null
        checkInReceipt = null
        return true
    }

    fun updateTracker(transform: (TrackerState) -> TrackerState) {
        ensureToday()
        tracker = transform(tracker)
    }

    fun updatePreferences(transform: (Preferences) -> Preferences) {
        preferences = transform(preferences)
    }

    /** Saving the day editor replaces totals, so an older check-in can no longer be undone. */
    fun saveDay(
        tracker: TrackerState,
        displayName: String,
    ) {
        ensureToday()
        undoCheckIn = null
        this.tracker = tracker.copy(day = this.tracker.day)
        preferences = preferences.copy(displayName = displayName)
    }

    fun checkIn(input: String) {
        ensureToday()
        val before = tracker
        val beforePreferences = preferences
        val result = parseCheckIn(input, tracker, preferences)
        tracker = result.tracker
        preferences = result.preferences
        // Undo restores only what a check-in can change, keeping later logging intact.
        undoCheckIn = {
            tracker = tracker.copy(allowance = before.allowance, opportunities = before.opportunities)
            preferences = preferences.copy(taste = beforePreferences.taste, query = beforePreferences.query)
        }
        checkInReceipt = UiText.Joined(result.notes.map(::noteText))
    }

    fun undoLastCheckIn() {
        undoCheckIn?.invoke()
        undoCheckIn = null
        checkInReceipt = UiText.of(R.string.checkin_undone)
    }

    /** Replaces the day, e.g. with a stored, synced or confirmed Assistant copy. */
    fun restore(
        tracker: TrackerState,
        preferences: Preferences,
    ) {
        this.tracker = tracker
        this.preferences = preferences
    }

    fun reset() {
        tracker = TrackerState(clock.today())
        preferences = Preferences()
        undoCheckIn = null
        checkInReceipt = null
    }

    private fun noteText(note: CheckInNote): UiText =
        when (note) {
            is CheckInNote.AmountLeft -> UiText.of(R.string.checkin_amount_left, note.amount)
            CheckInNote.AllowanceUpdated -> UiText.of(R.string.checkin_allowance_updated)
            CheckInNote.AmountNeedsContext -> UiText.of(R.string.checkin_amount_needs_context)
            is CheckInNote.MealsLeft -> UiText.Plural(R.plurals.checkin_meals_left, note.count)
            is CheckInNote.SearchingTaste -> UiText.of(R.string.checkin_searching_taste, note.taste)
            is CheckInNote.NextSearch -> UiText.of(R.string.checkin_next_search, note.query)
        }
}
