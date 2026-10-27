package com.hungii.prototype

/** What a free-text check-in changed, rendered by the UI in the user's language. */
sealed interface CheckInNote {
    data class AmountLeft(
        val amount: Int,
    ) : CheckInNote

    data object AllowanceUpdated : CheckInNote

    data object AmountNeedsContext : CheckInNote

    data class MealsLeft(
        val count: Int,
    ) : CheckInNote

    data class SearchingTaste(
        val taste: String,
    ) : CheckInNote

    data class NextSearch(
        val query: String,
    ) : CheckInNote
}

data class CheckInResult(
    val tracker: TrackerState,
    val preferences: Preferences,
    val notes: List<CheckInNote>,
)

private val amountPattern = Regex("""(?:₹|rs\.?\s*|rupees?\s*)(\d+)""")
private val mealsPattern = Regex("""\b([0-8])\s*(?:more\s+)?meals?\s*(?:left|remaining)?""")

/**
 * Understands short updates such as "₹300 left, 2 meals, spicy". It never guesses
 * nutrition; text it cannot interpret becomes the next search.
 */
fun parseCheckIn(
    input: String,
    tracker: TrackerState,
    preferences: Preferences,
): CheckInResult {
    val raw = input.lowercase()
    var nextTracker = tracker
    var nextPreferences = preferences
    val notes = mutableListOf<CheckInNote>()
    amountPattern.find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { amount ->
        when {
            "left" in raw || "remaining" in raw -> {
                nextTracker = nextTracker.copy(allowance = nextTracker.spent + amount)
                notes += CheckInNote.AmountLeft(amount)
            }

            "daily" in raw || "budget" in raw || "allowance" in raw -> {
                nextTracker = nextTracker.copy(allowance = amount)
                notes += CheckInNote.AllowanceUpdated
            }

            else -> {
                notes += CheckInNote.AmountNeedsContext
            }
        }
    }
    mealsPattern.find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { count ->
        nextTracker = nextTracker.copy(opportunities = count)
        notes += CheckInNote.MealsLeft(count)
    }
    Tastes.descriptive.firstOrNull { it in raw }?.let { taste ->
        nextPreferences = nextPreferences.copy(taste = taste, query = "$taste food")
        notes += CheckInNote.SearchingTaste(taste)
    }
    if (notes.isEmpty()) {
        val query = input.trim().take(150)
        nextPreferences = nextPreferences.copy(query = query)
        notes += CheckInNote.NextSearch(query)
    }
    return CheckInResult(nextTracker, nextPreferences, notes)
}
