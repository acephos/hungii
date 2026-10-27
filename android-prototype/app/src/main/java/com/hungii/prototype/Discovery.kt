package com.hungii.prototype

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.security.SecureRandom

/** The Swiggy connection, delivery address and environment flags. */
class SwiggyConnection(
    private val localDemo: Boolean,
) {
    var connected by mutableStateOf(false)
    var addressId by mutableStateOf<String?>(null)
    var environment by mutableStateOf("")
    var orderingEnabled by mutableStateOf(localDemo)
    var available by mutableStateOf(localDemo)
    val addresses = mutableStateListOf<DeliveryAddress>()
    var addressPage by mutableIntStateOf(1)
    var moreAddresses by mutableStateOf(false)

    /** Meal search needs a connection and a chosen address. */
    val ready get() = connected && addressId != null

    fun applyStatus(status: JSONObject) {
        connected = status.getBoolean("connected")
        addressId = status.stringOrNull("addressId")
        environment = status.optString("environment")
        orderingEnabled = localDemo || status.optBoolean("orderingEnabled")
        available = localDemo || status.optBoolean("connectionAvailable")
    }

    fun applyAddresses(
        result: JSONObject,
        page: Int,
    ) {
        addresses.clear()
        addresses.addAll(result.getJSONArray("addresses").objects().map(DeliveryAddress::from))
        addressPage = page
        moreAddresses = result.getJSONObject("pagination").getBoolean("hasMore")
    }

    fun clear() {
        connected = false
        addressId = null
        addresses.clear()
        orderingEnabled = localDemo
    }
}

/** Search results, the three-meal shortlist and the lucky draw. */
class Discovery(
    private val api: Backend,
    private val actions: ActionRunner,
    private val navigator: Navigator,
    private val day: DayPlan,
    private val moneyLeft: () -> Int,
    private val canSearch: () -> Boolean,
    private val onNeedsConnection: () -> Unit,
    private val onNewShortlist: () -> Unit,
    private val notify: (UiText) -> Unit,
) {
    val meals = mutableStateListOf<Meal>()
    val restaurants = mutableStateListOf<Restaurant>()
    val finalists = mutableStateListOf<Meal>()
    val passed = mutableStateListOf<String>()
    var lastPassed by mutableStateOf<Meal?>(null)
        private set
    var winner by mutableStateOf<Meal?>(null)
        private set
    var drawOrder by mutableStateOf<List<Meal>>(emptyList())
        private set
    var shuffling by mutableStateOf(false)
        private set
    var canPick by mutableStateOf(false)
        private set
    var pickedIndex by mutableStateOf<Int?>(null)
        private set
    var attempted by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set

    /** The next meals to swipe, recomputed only when their inputs change. */
    val candidates: List<Meal> by derivedStateOf {
        rankMeals(meals, passed, finalists, RankingContext(day.tracker, day.preferences, moneyLeft()))
    }

    val canUndo get() = lastPassed != null || finalists.isNotEmpty()

    fun search() {
        navigator.go(Screen.Discover)
        if (!canSearch()) {
            onNeedsConnection()
            return
        }
        actions.launch(Operation.Discovery, onError = { error = it }) {
            error = null
            attempted = true
            finalists.clear()
            passed.clear()
            lastPassed = null
            winner = null
            onNewShortlist()
            val prefs = day.preferences
            val args = JSONObject().put("query", prefs.query)
            if (prefs.highProtein) {
                args.put("collection", "EATRIGHT")
            } else if (prefs.fast) {
                args.put("collection", "BOLT")
            }
            val result = api.action("discover", args)
            meals.clear()
            restaurants.clear()
            if (result.optBoolean("expandedQuery")) notify(UiText.of(R.string.discovery_expanded))
            meals.addAll(result.getJSONArray("meals").objects().map(Meal::from))
            restaurants.addAll(result.getJSONArray("restaurants").objects().map(Restaurant::from))
        }
    }

    fun restaurantMeals(restaurant: Restaurant) =
        actions.launch(Operation.Discovery, onError = { error = it }) {
            error = null
            attempted = true
            val args =
                JSONObject()
                    .put("query", day.preferences.query)
                    .put("restaurantId", restaurant.id)
                    .put("vegOnly", day.preferences.vegOnly)
            val result = api.action("menu", args)
            meals.clear()
            passed.clear()
            lastPassed = null
            meals.addAll(
                result.getJSONArray("meals").objects().map {
                    Meal.from(it).copy(etaMinutes = restaurant.etaMinutes, distanceKm = restaurant.distanceKm)
                },
            )
        }

    fun keep(meal: Meal) {
        if (finalists.size >= 3 || finalists.any { it.id == meal.id }) return
        finalists.add(meal)
        lastPassed = null
        if (finalists.size == 3) navigator.go(Screen.Finalists)
    }

    fun pass(meal: Meal) {
        passed.add(meal.id)
        lastPassed = meal
    }

    fun undo() {
        val previous = lastPassed
        if (previous != null) {
            passed.remove(previous.id)
            lastPassed = null
        } else if (finalists.isNotEmpty()) {
            finalists.removeAt(finalists.lastIndex)
            navigator.go(Screen.Discover)
        }
    }

    fun revisitPassed() = passed.clear()

    fun remove(meal: Meal) {
        finalists.remove(meal)
        winner = null
        navigator.go(Screen.Discover)
    }

    fun showFinalists() {
        if (finalists.isNotEmpty()) navigator.go(Screen.Finalists)
    }

    fun startDraw() {
        if (finalists.size != 3 || shuffling) return
        drawOrder = finalists.shuffled(SecureRandom())
        winner = null
        pickedIndex = null
        canPick = false
        shuffling = true
        navigator.go(Screen.Draw)
    }

    /** Called when the shuffle animation settles. */
    fun finishShuffle() {
        shuffling = false
        canPick = true
    }

    fun stopShuffle() {
        shuffling = false
    }

    fun pick(index: Int) {
        if (!canPick || shuffling || pickedIndex != null || index !in drawOrder.indices) return
        pickedIndex = index
        canPick = false
        winner = drawOrder[index]
    }

    fun clear() {
        meals.clear()
        restaurants.clear()
        finalists.clear()
        passed.clear()
        lastPassed = null
        winner = null
        drawOrder = emptyList()
        attempted = false
        error = null
    }

    /** Whether results exist but filters or swipes have hidden every one of them. */
    val hasUnseenMeals get() = meals.any { m -> m.id !in passed && finalists.none { it.id == m.id } }
}
