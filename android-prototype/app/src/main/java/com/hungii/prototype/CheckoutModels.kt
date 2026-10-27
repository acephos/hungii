package com.hungii.prototype

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Where a checkout stands. [wire] values are persisted and must not change. */
enum class PaymentStage(
    val wire: String,
) {
    Choose("choose"),
    Placing("placing"),
    Pending("pending"),
    Unresolved("unresolved"),
    Confirmed("confirmed"),
    Failed("failed"),
    ;

    /** An order may exist that is not yet confirmed or failed; never start another. */
    val inFlight get() = this == Placing || this == Pending || this == Unresolved

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: Choose

        /** Maps the provider-neutral attempt status returned by checkout and resume. */
        fun forPaymentStatus(status: String?) =
            when (status) {
                "CONFIRMED" -> Confirmed
                "PENDING_PAYMENT" -> Pending
                "FAILED" -> Failed
                else -> Unresolved
            }
    }
}

/** Checkout errors that prove no order was placed, so a fresh review may start. */
val RETRYABLE_CHECKOUT_CODES =
    setOf(
        "HUNGII_CART_CHANGED",
        "HUNGII_QUOTE_EXPIRED",
        "HUNGII_PAYMENT_CHANGED",
        "HUNGII_CART_INVALID",
        "HUNGII_CONFIRM_REQUIRED",
        "HUNGII_ORDERING_PENDING",
    )

data class CartFees(
    val packaging: Double?,
    val platform: Double?,
    val tax: Double?,
)

data class VariantChoice(
    val id: String,
    val name: String,
)

data class VariantGroup(
    val groupId: String,
    val name: String?,
    val variations: List<VariantChoice>,
)

data class AddonChoice(
    val id: String,
    val name: String,
    val price: Double?,
)

data class AddonGroup(
    val groupId: String,
    val choices: List<AddonChoice>,
)

data class SelectedAddon(
    val groupId: String,
    val addonId: String,
) {
    fun json(): JSONObject = JSONObject().put("group_id", groupId).put("addon_id", addonId)
}

data class CartItem(
    val menuItemId: String,
    val name: String,
    val quantity: Int,
    val total: Double?,
    val photo: Int,
    val selectedVariations: List<String>,
    val selectedAddons: List<SelectedAddon>,
    val variantGroups: List<VariantGroup>,
    val addonGroups: List<AddonGroup>,
    val nutrition: Nutrition?,
    private val raw: JSONObject,
) {
    fun json(): JSONObject = JSONObject(raw.toString())

    /** The add-on list after switching one choice, keeping the others. */
    fun addonsWith(
        group: AddonGroup,
        choice: AddonChoice,
        on: Boolean,
    ): List<SelectedAddon> =
        selectedAddons.filter { it.addonId != choice.id } + if (on) listOf(SelectedAddon(group.groupId, choice.id)) else emptyList()

    companion object {
        fun from(o: JSONObject): CartItem {
            val custom = o.optJSONObject("customizations")
            return CartItem(
                menuItemId = o.getString("menuItemId"),
                name = o.optString("name"),
                quantity = o.optInt("quantity"),
                total = o.moneyOrNull("total"),
                photo = o.optInt("photo"),
                selectedVariations = o.optJSONArray("variants").objects().mapNotNull { it.stringOrNull("variation_id") },
                selectedAddons =
                    o.optJSONArray("addons").objects().mapNotNull { a ->
                        val group = a.stringOrNull("group_id")
                        val addon = a.stringOrNull("addon_id")
                        if (group != null && addon != null) SelectedAddon(group, addon) else null
                    },
                variantGroups =
                    custom?.optJSONArray("variants").objects().map { g ->
                        VariantGroup(
                            g.getString("groupId"),
                            g.stringOrNull("name"),
                            g.optJSONArray("variations").objects().map { VariantChoice(it.getString("id"), it.getString("name")) },
                        )
                    },
                addonGroups =
                    custom?.optJSONArray("addons").objects().map { g ->
                        AddonGroup(
                            g.getString("groupId"),
                            g
                                .optJSONArray(
                                    "choices",
                                ).objects()
                                .map { AddonChoice(it.getString("id"), it.getString("name"), it.moneyOrNull("price")) },
                        )
                    },
                nutrition = o.optJSONObject("nutrition")?.let(Nutrition::from),
                raw = o,
            )
        }
    }
}

/** A cheaper-overall side suggested by the Simulator, with its calorie trade-off. */
data class CartNudge(
    val dishId: String,
    val name: String,
    val price: Double,
    val coupon: String,
    val saving: Double,
    val newPayable: Double,
    val calories: Span?,
)

/** A basket as returned by the backend or Simulator. Unknown amounts stay null. */
data class Cart(
    val cartId: String?,
    val restaurantId: String?,
    val restaurant: String?,
    val address: String?,
    val items: List<CartItem>,
    val itemTotal: Double?,
    val deliveryCharge: Double?,
    val fees: CartFees,
    val couponDiscount: Double,
    val appliedCoupon: String?,
    val payable: Double?,
    val nudge: CartNudge?,
    /** Opaque basket version echoed to checkout; its JSON type differs by backend. */
    val revision: Any?,
    val quote: String?,
    private val raw: JSONObject,
) {
    val itemCount get() = items.sumOf { it.quantity }

    /** Persisted with the checkout so recovery can show what was ordered. */
    fun json(): JSONObject = JSONObject(raw.toString())

    companion object {
        fun from(o: JSONObject): Cart {
            val fees = o.optJSONObject("fees")
            return Cart(
                cartId = o.stringOrNull("cartId"),
                restaurantId = o.stringOrNull("restaurantId"),
                restaurant = o.stringOrNull("restaurant"),
                address = o.stringOrNull("address"),
                items = o.optJSONArray("items").objects().map(CartItem::from),
                itemTotal = o.moneyOrNull("itemTotal"),
                deliveryCharge = o.moneyOrNull("deliveryCharge"),
                fees =
                    CartFees(
                        packaging = fees?.moneyOrNull("packaging"),
                        platform = fees?.moneyOrNull("platform"),
                        tax = fees?.moneyOrNull("tax") ?: o.moneyOrNull("taxes"),
                    ),
                couponDiscount = o.moneyOrNull("couponDiscount")?.takeIf { it > 0 } ?: 0.0,
                appliedCoupon = o.stringOrNull("appliedCoupon"),
                payable = o.moneyOrNull("payable"),
                nudge =
                    o.optJSONObject("nudge")?.let { n ->
                        CartNudge(
                            dishId = n.getString("dishId"),
                            name = n.getString("name"),
                            price = n.getDouble("price"),
                            coupon = n.getString("coupon"),
                            saving = n.getDouble("saving"),
                            newPayable = n.getDouble("newPayable"),
                            calories = n.optJSONObject("nutrition")?.optJSONArray("calories")?.let(Span::from),
                        )
                    },
                revision = if (o.isNull("revision")) null else o.get("revision"),
                quote = o.stringOrNull("quote"),
                raw = o,
            )
        }
    }
}

data class PaymentMethod(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
) {
    companion object {
        fun listFrom(options: JSONObject?): List<PaymentMethod> =
            options?.optJSONArray("allMethods").objects().mapNotNull { m ->
                val id = m.stringOrNull("id") ?: return@mapNotNull null
                PaymentMethod(id, m.stringOrNull("displayName") ?: id, m.optBoolean("enabled", true))
            }
    }
}

/** Provider identifiers and polling hints for one checkout attempt. */
data class Payment(
    val orderId: String?,
    val paasId: String?,
    val cartId: String?,
    val bridgeUrl: String?,
    val pollingIntervalMs: Long?,
    val maxPollMs: Long?,
    val status: String?,
) {
    /** Only HTTPS payment links are opened. */
    val safeBridgeUrl get() = bridgeUrl?.takeIf { it.startsWith("https://") }

    fun json(): JSONObject =
        JSONObject()
            .put("orderId", orderId.orJsonNull())
            .put("paasId", paasId.orJsonNull())
            .put("cartId", cartId.orJsonNull())
            .put("bridgeUrl", bridgeUrl.orJsonNull())
            .put("pollingIntervalInMs", pollingIntervalMs.orJsonNull())
            .put("maxTimeToPollForInMs", maxPollMs.orJsonNull())
            .put("status", status.orJsonNull())

    companion object {
        fun from(o: JSONObject) =
            Payment(
                orderId = o.stringOrNull("orderId"),
                paasId = o.stringOrNull("paasId"),
                cartId = o.stringOrNull("cartId"),
                bridgeUrl = o.stringOrNull("bridgeUrl"),
                pollingIntervalMs = o.longOrNull("pollingIntervalInMs"),
                maxPollMs = o.longOrNull("maxTimeToPollForInMs"),
                status = o.stringOrNull("status"),
            )
    }
}

/** A payment-status poll result. */
data class PaymentStatusUpdate(
    val confirmed: Boolean,
    val terminalFailure: Boolean,
    val orderId: String?,
) {
    companion object {
        fun from(o: JSONObject) =
            PaymentStatusUpdate(o.optBoolean("confirmed"), o.optBoolean("isTerminalFailure"), o.stringOrNull("orderId"))
    }
}

/** Stage after a poll. Real attempts the backend cannot match stay unresolved. */
fun stageAfterStatus(
    update: PaymentStatusUpdate,
    attemptStatus: String?,
    localDemo: Boolean,
): PaymentStage =
    when {
        update.confirmed -> PaymentStage.Confirmed
        update.terminalFailure -> PaymentStage.Failed
        !localDemo && attemptStatus == "UNRESOLVED" -> PaymentStage.Unresolved
        else -> PaymentStage.Pending
    }

data class OrderTracking(
    val statusMessage: String?,
    val orderStatus: String?,
    val etaText: String?,
) {
    val headline get() = statusMessage ?: orderStatus
}

data class OrderInfo(
    val orderId: String?,
    val tracking: OrderTracking?,
) {
    companion object {
        fun from(o: JSONObject) =
            OrderInfo(
                o.stringOrNull("orderId"),
                o.optJSONObject("tracking")?.let {
                    OrderTracking(it.stringOrNull("statusMessage"), it.stringOrNull("orderStatus"), it.stringOrNull("etaText"))
                },
            )
    }
}

/**
 * Money held back from today's allowance while an order might still be charged.
 * Only checkouts started today count, so a stuck attempt cannot shrink later days.
 */
fun reservedSpend(
    stage: PaymentStage,
    checkoutCart: Cart?,
    checkoutCreatedAt: Long,
    today: String,
    clock: Clock,
): Int =
    if (stage.inFlight && checkoutCreatedAt > 0 && clock.dayOf(checkoutCreatedAt) == today) {
        (checkoutCart?.payable ?: 0.0).roundToInt()
    } else {
        0
    }

internal fun List<SelectedAddon>.json(): JSONArray = JSONArray().also { array -> forEach { array.put(it.json()) } }
