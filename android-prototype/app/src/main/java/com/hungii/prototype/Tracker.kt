package com.hungii.prototype

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** User-entered goals and today's totals. Goals carry over; totals belong to [day]. */
data class TrackerState(
    val day: String,
    val calorieGoal: Int = 2200,
    val proteinGoal: Int = 140,
    val carbGoal: Int = 250,
    val fatGoal: Int = 70,
    val allowance: Int = 600,
    val spent: Int = 0,
    val opportunities: Int = 2,
    val intake: Nutrition = Nutrition.zero,
) {
    val caloriesLeft get() = Span(calorieGoal - intake.calories.high, calorieGoal - intake.calories.low)
    val proteinLeft get() = Span(proteinGoal - intake.protein.high, proteinGoal - intake.protein.low)

    /** A new day keeps goals and allowance but starts spending, intake and meals afresh. */
    fun forDay(today: String) = if (day == today) this else copy(day = today, spent = 0, opportunities = 2, intake = Nutrition.zero)

    // Key order is part of the sync contract: devices compare serialized states.
    fun json(): JSONObject =
        JSONObject()
            .put("day", day)
            .put("calorieGoal", calorieGoal)
            .put("proteinGoal", proteinGoal)
            .put("carbGoal", carbGoal)
            .put("fatGoal", fatGoal)
            .put("allowance", allowance)
            .put("spent", spent)
            .put("opportunities", opportunities)
            .put("intake", intake.json())

    companion object {
        /** Restores a stored or synced tracker for [today]; totals from another day are dropped. */
        fun from(
            o: JSONObject,
            today: String,
        ): TrackerState {
            val goals =
                TrackerState(
                    day = today,
                    calorieGoal = o.getInt("calorieGoal"),
                    proteinGoal = o.getInt("proteinGoal"),
                    carbGoal = o.getInt("carbGoal"),
                    fatGoal = o.getInt("fatGoal"),
                    allowance = o.getInt("allowance"),
                )
            if (o.getString("day") != today) return goals
            return goals.copy(
                spent = o.getInt("spent"),
                opportunities = o.getInt("opportunities"),
                intake = Nutrition.from(o.getJSONObject("intake")),
            )
        }
    }
}

data class Preferences(
    val displayName: String = "",
    val taste: String = Tastes.ANY,
    val query: String = DEFAULT_QUERY,
    val highProtein: Boolean = false,
    val vegOnly: Boolean = false,
    val budgetOnly: Boolean = false,
    val fast: Boolean = false,
) {
    val filterCount get() = listOf(taste != Tastes.ANY, highProtein, vegOnly, budgetOnly, fast).count { it }

    fun json(): JSONObject =
        JSONObject()
            .put("displayName", displayName)
            .put("taste", taste)
            .put("query", query)
            .put("highProtein", highProtein)
            .put("vegOnly", vegOnly)
            .put("budgetOnly", budgetOnly)
            .put("fast", fast)

    companion object {
        const val DEFAULT_QUERY = "healthy bowls"

        fun from(o: JSONObject) =
            Preferences(
                displayName = o.optString("displayName"),
                taste = o.optString("taste", Tastes.ANY).takeIf { it in Tastes.all } ?: Tastes.ANY,
                query = o.optString("query", DEFAULT_QUERY),
                highProtein = o.optBoolean("highProtein"),
                vegOnly = o.optBoolean("vegOnly"),
                budgetOnly = o.optBoolean("budgetOnly"),
                fast = o.optBoolean("fast"),
            )
    }
}

/** The state shared with optional cloud sync: tracker fields plus preferences. */
internal fun profileJson(
    tracker: TrackerState,
    preferences: Preferences,
): JSONObject = tracker.json().put("preferences", preferences.json())

/** Supplies "today" so day boundaries can be tested. */
fun interface Clock {
    fun now(): Instant

    fun today(): String = dayOf(now())

    fun dayOf(epochMillis: Long): String = dayOf(Instant.ofEpochMilli(epochMillis))

    private fun dayOf(instant: Instant): String = instant.atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** Milliseconds until shortly after the next local midnight. */
    fun millisUntilNextDay(): Long {
        val zone = ZoneId.systemDefault()
        val next =
            now()
                .atZone(zone)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
        return next.toEpochMilli() - now().toEpochMilli() + 1_000
    }

    companion object {
        val system = Clock { Instant.now() }
    }
}
