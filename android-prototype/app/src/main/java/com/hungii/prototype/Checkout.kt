package com.hungii.prototype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Basket, payment and order recovery. The request ID is saved before checkout is
 * sent, an ambiguous placement stays unresolved, and a confirmed order adds to
 * today's spending exactly once.
 */
class Checkout(
    private val api: Backend,
    private val actions: ActionRunner,
    private val navigator: Navigator,
    private val day: DayPlan,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val localDemo: Boolean,
    private val winner: () -> Meal?,
    private val orderingEnabled: () -> Boolean,
    private val saveNow: suspend () -> Unit,
    private val notify: (UiText) -> Unit,
    private val openDayEditor: () -> Unit,
) {
    var cart by mutableStateOf<Cart?>(null)
        private set

    /** Null until the backend has verified the basket and returned options. */
    var paymentMethods by mutableStateOf<List<PaymentMethod>?>(null)
        private set
    var selectedMethodId by mutableStateOf("")
    var note by mutableStateOf("")
    var requestId by mutableStateOf<String?>(null)
        private set
    var createdAt by mutableLongStateOf(0L)
        private set
    var payment by mutableStateOf<Payment?>(null)
        private set
    var stage by mutableStateOf(PaymentStage.Choose)
        private set
    var order by mutableStateOf<OrderInfo?>(null)
        private set
    var checkoutCart by mutableStateOf<Cart?>(null)
        private set
    var replaceCartPending by mutableStateOf(false)
    var refreshRequired by mutableStateOf(false)
        private set
    var trace by mutableStateOf<List<String>>(emptyList())
        private set

    private val trackedOrders = mutableSetOf<String>()
    private val eatenOrders = mutableSetOf<String>()
    private var pollJob: Job? = null

    val reservedSpend get() = reservedSpend(stage, checkoutCart, createdAt, day.tracker.day, clock)

    /** The basket shown on the payment screen: the attempt's own once checkout began. */
    val paymentCart get() = if (stage != PaymentStage.Choose) checkoutCart ?: cart else cart

    fun review(replace: Boolean = false) {
        navigator.go(Screen.Review)
        val meal = winner() ?: return
        if (!orderingEnabled()) {
            cart = null
            return
        }
        actions.launch(Operation.Cart, onError = { refreshRequired = true }) {
            val args =
                JSONObject()
                    .put("dishId", meal.dishId)
                    .put("restaurantId", meal.restaurantId)
                    .put("query", meal.name)
                    .put("replace", replace)
            try {
                applyCart(api.action("cart_create", args))
            } catch (e: ApiFailure) {
                if (e.code != "HUNGII_REPLACE_CART") throw e
                replaceCartPending = true
            }
        }
    }

    fun refreshCart() = editCart { api.action("cart") }

    fun changeQuantity(
        item: CartItem,
        quantity: Int,
    ) = editCart {
        api.action("cart_quantity", JSONObject().put("dishId", item.menuItemId).put("query", item.name).put("quantity", quantity))
    }

    fun changeVariant(
        item: CartItem,
        group: VariantGroup,
        choice: VariantChoice,
    ) = editCart {
        api.action("cart_variant", JSONObject().put("dishId", item.menuItemId).put("groupId", group.groupId).put("variationId", choice.id))
    }

    fun changeAddons(
        item: CartItem,
        addons: List<SelectedAddon>,
    ) = editCart { api.action("cart_addons", JSONObject().put("dishId", item.menuItemId).put("addons", addons.json())) }

    fun addSide(dishId: String) = editCart { api.action("cart_add_side", JSONObject().put("dishId", dishId)) }

    fun applyCoupon(code: String) = editCart { api.action("cart_coupon", JSONObject().put("couponCode", code)) }

    private fun editCart(request: suspend () -> JSONObject) =
        actions.launch(Operation.Cart, onError = { refreshRequired = true }) {
            applyCart(request())
        }

    private fun applyCart(result: JSONObject) {
        refreshRequired = false
        cart = Cart.from(result.optJSONObject("cart") ?: result)
        applyTrace(result)
    }

    /** Keeps the Simulator's recent MCP tool calls for the activity panel. */
    fun applyTrace(result: JSONObject) {
        result.optJSONArray("mcpTrace")?.let { trace = it.strings() }
    }

    fun choosePayment() {
        if (stage.inFlight) {
            navigator.go(Screen.Payment)
            return
        }
        paymentMethods = null
        payment = null
        requestId = null
        selectedMethodId = ""
        stage = PaymentStage.Choose
        navigator.go(Screen.Payment)
        actions.launch(Operation.Payment, onError = { refreshRequired = true }) {
            val result = api.action("payment_options")
            paymentMethods = PaymentMethod.listFrom(result.getJSONObject("paymentOptions"))
            applyCart(result)
        }
    }

    fun beginPayment() =
        actions.launch(Operation.Payment, onError = { if (stage == PaymentStage.Placing) stage = PaymentStage.Unresolved }) {
            val basket = cart ?: return@launch
            val payable = basket.payable
            if (payable == null) {
                refreshRequired = true
                return@launch
            }
            requestId = requestId ?: UUID.randomUUID().toString()
            createdAt = clock.now().toEpochMilli()
            stage = PaymentStage.Placing
            checkoutCart = basket
            // Persist before the network call so a crash cannot lose the request ID.
            saveNow()
            val args =
                JSONObject()
                    .put("confirm", true)
                    .put("requestId", requestId)
                    .put("revision", basket.revision.orJsonNull())
                    .put("quote", basket.quote ?: "")
                    .put("payable", payable)
                    .put("methodId", selectedMethodId)
                    .put("note", note)
            try {
                restoreCheckout(api.action("checkout", args), navigate = true)
            } catch (e: ApiFailure) {
                if (e.code in RETRYABLE_CHECKOUT_CODES) {
                    requestId = null
                    stage = PaymentStage.Choose
                    refreshRequired = true
                }
                throw e
            }
        }

    /** Applies a checkout or recovery result from the backend. */
    suspend fun restoreCheckout(
        result: JSONObject,
        navigate: Boolean,
    ) {
        val attempt = result.optJSONObject("payment")?.let(Payment::from) ?: return
        payment = attempt
        createdAt = result.longOrNull("createdAt") ?: createdAt
        result.optJSONObject("checkoutCart")?.let { checkoutCart = Cart.from(it) }
        requestId = result.stringOrNull("requestId") ?: requestId
        applyTrace(result)
        val next = PaymentStage.forPaymentStatus(attempt.status)
        val orderId = attempt.orderId
        when {
            next == PaymentStage.Confirmed && orderId != null -> {
                completeOrder(orderId, navigate)
            }

            next == PaymentStage.Pending -> {
                stage = next
                if (navigate) navigator.go(Screen.Payment)
                startPolling()
            }

            else -> {
                // A confirmation without an order ID cannot be tracked or counted.
                stage = if (next == PaymentStage.Confirmed) PaymentStage.Unresolved else next
                if (navigate) navigator.go(Screen.Payment)
            }
        }
    }

    /** After an account refresh: recover a real checkout the backend still holds. */
    suspend fun recoverAfterRefresh() {
        if (localDemo || !(orderingEnabled() || requestId != null)) return
        val resumed = api.action("checkout_resume")
        if (resumed.optJSONObject("payment") != null) restoreCheckout(resumed, navigate = false)
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob =
            scope.launch {
                val window = (payment?.maxPollMs ?: 60_000L).coerceIn(1_000L, 600_000L)
                val until = clock.now().toEpochMilli() + window
                while (stage == PaymentStage.Pending && clock.now().toEpochMilli() < until) {
                    delay(if (localDemo) 1_600L else (payment?.pollingIntervalMs ?: 5_000L).coerceAtLeast(1_000L))
                    try {
                        actions.serialized {
                            if (stage == PaymentStage.Pending) consumePayment(api.action("payment_status", paymentArgs()))
                        }
                    } catch (e: CancellationException) {
                        // A newer poll or a reset replaced this one; it must not change the stage.
                        throw e
                    } catch (_: Exception) {
                        actions.report(UiText.of(R.string.payment_status_unresolved))
                        stage = PaymentStage.Unresolved
                        break
                    }
                }
                if (stage == PaymentStage.Pending) {
                    stage = PaymentStage.Unresolved
                    actions.report(UiText.of(R.string.payment_taking_longer))
                }
            }
    }

    private fun paymentArgs(): JSONObject {
        val args = JSONObject().put("requestId", requestId)
        if (localDemo) {
            payment?.let { p ->
                args.put("paasId", p.paasId ?: "").put("orderId", p.orderId ?: "").put("cartId", p.cartId ?: "")
            }
        }
        return args
    }

    fun simulatePayment(outcome: String) =
        actions.launch(Operation.Payment) {
            consumePayment(api.action("simulate_payment", paymentArgs().put("outcome", outcome)))
        }

    fun resumeCheckout() =
        actions.launch(Operation.Payment) {
            navigator.go(Screen.Payment)
            if (localDemo) {
                consumePayment(api.action("payment_status", paymentArgs()))
                return@launch
            }
            val result = api.action("checkout_resume")
            if (result.optJSONObject("payment") == null) {
                stage = PaymentStage.Unresolved
                actions.report(UiText.of(R.string.checkout_not_recovered))
            } else {
                restoreCheckout(result, navigate = true)
            }
        }

    fun refreshPayment() =
        actions.launch(Operation.Payment) {
            if (payment == null && !localDemo) {
                val result = api.action("checkout_resume")
                if (result.optJSONObject("payment") != null) restoreCheckout(result, navigate = true)
            } else {
                consumePayment(api.action("payment_status", paymentArgs()))
            }
        }

    private suspend fun consumePayment(result: JSONObject) {
        applyTrace(result)
        val update = PaymentStatusUpdate.from(result.getJSONObject("paymentStatus"))
        val attempt = result.optJSONObject("payment")
        if (!localDemo) {
            attempt?.let { payment = Payment.from(it) }
            result.optJSONObject("checkoutCart")?.let { checkoutCart = Cart.from(it) }
        }
        when (val next = stageAfterStatus(update, attempt?.stringOrNull("status"), localDemo)) {
            PaymentStage.Confirmed -> {
                val orderId = update.orderId ?: payment?.orderId
                if (orderId != null) completeOrder(orderId) else stage = PaymentStage.Unresolved
            }

            PaymentStage.Failed -> {
                stage = next
                actions.report(UiText.of(R.string.payment_failed))
            }

            else -> {
                stage = next
            }
        }
    }

    private suspend fun completeOrder(
        orderId: String,
        navigate: Boolean = true,
    ) {
        // Confirmation is known even when tracking is temporarily unavailable.
        stage = PaymentStage.Confirmed
        order = OrderInfo(orderId, null)
        if (trackedOrders.add(orderId)) {
            day.ensureToday()
            if (createdAt > 0 && clock.dayOf(createdAt) == day.tracker.day) {
                val paid = (checkoutCart?.payable ?: 0.0).roundToInt()
                day.updateTracker { it.copy(spent = it.spent + paid) }
            }
            saveNow()
        }
        if (navigate) navigator.go(Screen.Order)
        try {
            loadOrder(orderId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            actions.report(UiText.of(R.string.order_tracking_unavailable))
        }
    }

    private suspend fun loadOrder(orderId: String) {
        val result = api.action("order", JSONObject().put("orderId", orderId))
        order = OrderInfo.from(result).let { if (it.orderId == null) it.copy(orderId = orderId) else it }
        applyTrace(result)
    }

    fun refreshOrder() =
        actions.launch(Operation.Order) {
            val orderId = payment?.orderId ?: order?.orderId ?: return@launch
            loadOrder(orderId)
        }

    fun retryPayment() {
        if (stage != PaymentStage.Failed) return
        pollJob?.cancel()
        payment = null
        stage = PaymentStage.Choose
        choosePayment()
    }

    /** Ordering is not eating: only an explicit log adds the Simulator's estimate. */
    fun logOrderAsEaten() {
        if (!localDemo) {
            // Real menus publish no nutrition, so the user enters what they ate.
            navigator.go(Screen.Day)
            notify(UiText.of(R.string.log_real_order_hint))
            openDayEditor()
            return
        }
        val orderId = payment?.orderId ?: return
        if (!eatenOrders.add(orderId)) {
            notify(UiText.of(R.string.log_already_logged))
            return
        }
        val items = checkoutCart?.items ?: return
        day.updateTracker { tracker ->
            tracker.copy(
                intake = items.mapNotNull { it.nutrition }.fold(tracker.intake, Nutrition::plus),
                opportunities = (tracker.opportunities - 1).coerceAtLeast(0),
            )
        }
        notify(UiText.of(R.string.log_estimate_logged))
        navigator.go(Screen.Day)
    }

    fun clearCart() {
        cart = null
    }

    /** Forgets provider state on sign-out, disconnection or erasure. */
    fun clear() {
        pollJob?.cancel()
        payment = null
        order = null
        checkoutCart = null
        requestId = null
        stage = PaymentStage.Choose
        paymentMethods = null
        refreshRequired = false
        cart = null
    }

    fun putInto(o: JSONObject): JSONObject =
        o
            .put("checkoutCreatedAt", createdAt)
            .put("checkoutRequestId", requestId.orJsonNull())
            .put("payment", payment?.json().orJsonNull())
            .put("checkoutCart", checkoutCart?.json().orJsonNull())
            .put("paymentStage", stage.wire)
            .put("trackedOrders", JSONArray(trackedOrders.toList()))
            .put("eatenOrders", JSONArray(eatenOrders.toList()))

    fun restore(o: JSONObject?) {
        clear()
        trackedOrders.clear()
        eatenOrders.clear()
        createdAt = 0L
        if (o == null) return
        createdAt = o.optLong("checkoutCreatedAt")
        requestId = o.stringOrNull("checkoutRequestId")
        payment = o.optJSONObject("payment")?.let(Payment::from)
        checkoutCart = o.optJSONObject("checkoutCart")?.let(Cart::from)
        stage = PaymentStage.fromWire(o.optString("paymentStage", PaymentStage.Choose.wire))
        trackedOrders.addAll(o.optJSONArray("trackedOrders").strings())
        eatenOrders.addAll(o.optJSONArray("eatenOrders").strings())
    }
}
