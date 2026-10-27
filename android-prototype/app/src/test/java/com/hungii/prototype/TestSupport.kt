package com.hungii.prototype

import kotlinx.coroutines.test.TestScope
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** A clock that tests move by hand. */
class FakeClock(
    var instant: Instant = at("2026-10-27T09:00"),
) : Clock {
    override fun now(): Instant = instant

    fun set(local: String) {
        instant = at(local)
    }

    companion object {
        fun at(local: String): Instant = LocalDateTime.parse(local).atZone(ZoneId.systemDefault()).toInstant()
    }
}

/** A clock that follows the coroutine test scheduler, so polling deadlines advance with delay(). */
class SchedulerClock(
    private val scope: TestScope,
    private val start: Instant = FakeClock.at("2026-10-27T09:00"),
) : Clock {
    override fun now(): Instant = start.plusMillis(scope.testScheduler.currentTime)
}

/** Records requests and answers them with scripted responses or failures. */
class FakeBackend : Backend {
    val calls = mutableListOf<Pair<String, JSONObject>>()
    private val handlers = mutableMapOf<String, ArrayDeque<suspend (JSONObject) -> JSONObject>>()
    private val used = mutableSetOf<String>()
    var beforeEach: suspend (String) -> Unit = {}

    fun on(
        action: String,
        handler: suspend (JSONObject) -> JSONObject,
    ) {
        handlers.getOrPut(action) { ArrayDeque() }.addLast(handler)
    }

    fun respond(
        action: String,
        json: String,
    ) = on(action) { JSONObject(json) }

    fun fail(
        action: String,
        code: String,
        message: String = code,
    ) = on(action) { throw ApiFailure(code, message) }

    fun actions() = calls.map { it.first }

    override suspend fun action(
        action: String,
        args: JSONObject,
    ): JSONObject {
        beforeEach(action)
        calls += action to JSONObject(args.toString())
        val queue = handlers[action] ?: error("Unexpected request: $action")
        // Answers are used in order; the latest one repeats, so polling can ask several times.
        if (action in used && queue.size > 1) queue.removeFirst()
        used += action
        return queue.first()(args)
    }
}

fun meal(
    id: String,
    price: Double? = 200.0,
    veg: Boolean? = true,
    eta: Int? = 25,
    nutrition: Nutrition? = null,
    name: String = "Meal $id",
    description: String = "",
    estimatedPayable: Double? = null,
) = Meal(
    id = id,
    dishId = "dish-$id",
    restaurantId = "r1",
    name = name,
    restaurant = "Kitchen",
    imageUrl = null,
    veg = veg,
    itemPrice = price,
    etaMinutes = eta,
    distanceKm = null,
    description = description,
    nutrition = nutrition,
    estimatedPayable = estimatedPayable,
)

fun nutrition(
    calories: Int,
    protein: Int,
    carbs: Int,
    fat: Int,
) = Nutrition(Span.exact(calories), Span.exact(protein), Span.exact(carbs), Span.exact(fat))
