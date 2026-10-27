package com.hungii.prototype

import org.json.JSONArray
import org.json.JSONObject

data class Span(
    val low: Int,
    val high: Int,
) {
    val mid get() = (low + high) / 2f
    val label get() = if (low == high) "$low" else "$low–$high"

    operator fun plus(other: Span) = Span(low + other.low, high + other.high)

    fun json(): JSONArray = JSONArray().put(low).put(high)

    companion object {
        fun exact(value: Int) = Span(value, value)

        fun from(array: JSONArray) = Span(array.getInt(0), array.getInt(1))
    }
}

data class Nutrition(
    val calories: Span,
    val protein: Span,
    val carbs: Span,
    val fat: Span,
) {
    operator fun plus(other: Nutrition) =
        Nutrition(calories + other.calories, protein + other.protein, carbs + other.carbs, fat + other.fat)

    fun json(): JSONObject =
        JSONObject()
            .put("calories", calories.json())
            .put("protein", protein.json())
            .put("carbs", carbs.json())
            .put("fat", fat.json())

    companion object {
        val zero = Nutrition(Span(0, 0), Span(0, 0), Span(0, 0), Span(0, 0))

        fun from(o: JSONObject) =
            Nutrition(
                Span.from(o.getJSONArray("calories")),
                Span.from(o.getJSONArray("protein")),
                Span.from(o.getJSONArray("carbs")),
                Span.from(o.getJSONArray("fat")),
            )
    }
}

/** Taste values shared with the backend's preference validation in tracker.ts. */
object Tastes {
    const val ANY = "any"
    val all = listOf(ANY, "spicy", "cheesy", "sweet", "bland", "light", "filling")
    val descriptive = all - ANY

    /** The subset offered as filter chips. */
    val filters = listOf(ANY, "spicy", "light", "cheesy", "sweet")
}

data class Meal(
    val id: String,
    val dishId: String,
    val restaurantId: String,
    val name: String,
    val restaurant: String,
    val imageUrl: String?,
    val veg: Boolean?,
    val itemPrice: Double?,
    val etaMinutes: Int?,
    val distanceKm: Double?,
    val description: String = "",
    val offerText: String? = null,
    val nutrition: Nutrition? = null,
    val estimatedPayable: Double? = null,
    val photo: Int = 0,
) {
    val tags get() = Tastes.descriptive.filter { it in "$name $description".lowercase() }.toSet()

    /** Saved shortcuts keep identifiers and names only; prices and photos go stale. */
    fun forSaving() = copy(imageUrl = null, itemPrice = null, etaMinutes = null, distanceKm = null, description = "", offerText = null)

    fun json(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("dishId", dishId)
            .put("restaurantId", restaurantId)
            .put("name", name)
            .put("restaurant", restaurant)
            .put("imageUrl", imageUrl.orJsonNull())
            .put("veg", veg.orJsonNull())
            .put("itemPrice", itemPrice.orJsonNull())
            .put("etaMinutes", etaMinutes.orJsonNull())
            .put("distanceKm", distanceKm.orJsonNull())
            .put("description", description)
            .put("offerText", offerText.orJsonNull())

    companion object {
        fun from(o: JSONObject) =
            Meal(
                id = o.getString("id"),
                dishId = o.getString("dishId"),
                restaurantId = o.getString("restaurantId"),
                name = o.getString("name"),
                restaurant = o.stringOrNull("restaurant") ?: "",
                imageUrl = o.stringOrNull("imageUrl")?.takeIf { it.startsWith("https://") },
                veg = if (o.isNull("veg")) null else o.getBoolean("veg"),
                itemPrice = o.doubleOrNull("itemPrice"),
                etaMinutes = o.intOrNull("etaMinutes"),
                distanceKm = o.doubleOrNull("distanceKm"),
                description = o.stringOrNull("description") ?: "",
                offerText = o.stringOrNull("offerText"),
                nutrition = o.optJSONObject("nutrition")?.let(Nutrition::from),
                estimatedPayable = o.doubleOrNull("estimatedPayable"),
                photo = o.optInt("photo"),
            )
    }
}

data class DeliveryAddress(
    val id: String,
    val label: String,
    val addressLine: String,
) {
    companion object {
        fun from(o: JSONObject) = DeliveryAddress(o.getString("id"), o.getString("label"), o.getString("addressLine"))
    }
}

data class Restaurant(
    val id: String,
    val name: String,
    val etaMinutes: Int?,
    val distanceKm: Double?,
) {
    companion object {
        fun from(o: JSONObject) =
            Restaurant(o.getString("id"), o.getString("name"), o.intOrNull("etaMinutes"), o.doubleOrNull("distanceKm"))
    }
}

/** Whole rupees without decimals, otherwise two decimal places. */
internal fun rupees(value: Double): String {
    val whole = value == value.toInt().toDouble()
    return if (whole) "₹${value.toInt()}" else "₹" + "%.2f".format(java.util.Locale.ROOT, value)
}
