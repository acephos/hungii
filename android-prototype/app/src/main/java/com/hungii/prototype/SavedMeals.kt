package com.hungii.prototype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Consented meal shortcuts kept on the device for up to 30 days. */
class SavedMeals(
    private val clock: Clock,
) {
    val meals = mutableStateListOf<Meal>()
    var consent by mutableStateOf(false)
        private set
    private var consentAt = 0L

    /** A meal waiting for the user to allow saving. */
    var pendingSave by mutableStateOf<Meal?>(null)

    fun isSaved(meal: Meal) = meals.any { it.id == meal.id }

    fun toggle(meal: Meal) {
        when {
            isSaved(meal) -> meals.removeAll { it.id == meal.id }
            consent -> meals.add(meal)
            else -> pendingSave = meal
        }
    }

    fun acceptPending() {
        consent = true
        consentAt = clock.now().toEpochMilli()
        pendingSave?.let { meals.add(it) }
        pendingSave = null
    }

    fun clear() {
        meals.clear()
        consent = false
        consentAt = 0
        pendingSave = null
    }

    fun putInto(o: JSONObject): JSONObject =
        o
            .put("savedConsent", consent)
            .put("savedConsentAt", consentAt)
            .put("savedMeals", JSONArray(meals.map { it.forSaving().json() }))

    fun restore(o: JSONObject) {
        clear()
        consentAt = o.optLong("savedConsentAt")
        consent = o.optBoolean("savedConsent") && clock.now().toEpochMilli() - consentAt < RETENTION_MS
        if (consent) meals.addAll(o.optJSONArray("savedMeals").objects().map(Meal::from))
    }

    private companion object {
        const val RETENTION_MS = 30L * 86_400 * 1_000
    }
}
