package com.hungii.prototype

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutModelsTest {
    // The shape returned by the Simulator gateway.
    private val demoCart =
        """
        {"cart":{"cartId":"c1","restaurantId":"mock-bowls","restaurant":"Bowl Lab","address":"12 Test Street",
        "items":[{"menuItemId":"tofu","name":"Tofu bowl","quantity":2,"total":420,"photo":3,
          "variants":[{"group_id":"portion","variation_id":"regular"}],
          "addons":[{"group_id":"extras","addon_id":"extra-tofu"}],
          "customizations":{"variants":[{"groupId":"portion","name":"Portion","variations":[{"id":"regular","name":"Regular"},{"id":"large","name":"Large"}]}],
            "addons":[{"groupId":"extras","choices":[{"id":"extra-tofu","name":"Extra tofu","price":39},{"id":"extra-protein","name":"Extra protein","price":59}]}]},
          "nutrition":{"calories":[500,600],"protein":[30,36],"carbs":[50,60],"fat":[14,18]}}],
        "itemTotal":420,"deliveryCharge":29,"fees":{"packaging":8,"platform":5,"tax":21},"couponDiscount":50,"appliedCoupon":"SAVE50",
        "payable":433,"revision":7,"taxes":21,
        "nudge":{"dishId":"side","name":"Iced tea","price":60,"coupon":"BIG100","saving":40,"newPayable":393,"nutrition":{"calories":[90,110]}}},
        "mcpTrace":["get_food_cart"]}
        """.trimIndent()

    @Test fun simulatorBasketParsesEveryShownField() {
        val cart = Cart.from(JSONObject(demoCart).getJSONObject("cart"))
        assertEquals("Bowl Lab", cart.restaurant)
        assertEquals(2, cart.itemCount)
        assertEquals(433.0, cart.payable!!, 0.0)
        assertEquals(CartFees(8.0, 5.0, 21.0), cart.fees)
        assertEquals(50.0, cart.couponDiscount, 0.0)
        assertEquals(7, cart.revision)
        val item = cart.items.single()
        assertEquals(listOf("regular"), item.selectedVariations)
        assertEquals(listOf(SelectedAddon("extras", "extra-tofu")), item.selectedAddons)
        assertEquals(
            2,
            item.variantGroups
                .single()
                .variations.size,
        )
        assertEquals(Span(500, 600), item.nutrition!!.calories)
        assertEquals(Span(90, 110), cart.nudge!!.calories)
    }

    @Test fun toggledAddonsKeepOtherChoices() {
        val item = Cart.from(JSONObject(demoCart).getJSONObject("cart")).items.single()
        val group = item.addonGroups.single()
        val added = item.addonsWith(group, group.choices[1], on = true)
        assertEquals(listOf("extra-tofu", "extra-protein"), added.map { it.addonId })
        assertTrue(item.addonsWith(group, group.choices[0], on = false).isEmpty())
    }

    @Test fun realBasketKeepsUnknownAmountsUnknown() {
        val real =
            JSONObject(
                """{"cartId":"x","restaurantId":"r","restaurant":"Real","items":[{"menuItemId":"m","name":"Thali","quantity":1,"total":null}],
                "itemTotal":null,"deliveryCharge":-5,"fees":{"tax":null},"couponDiscount":0,"appliedCoupon":null,"payable":null,
                "revision":"rev-1","quote":"sealed"}""",
            )
        val cart = Cart.from(real)
        assertNull(cart.itemTotal)
        assertNull(cart.deliveryCharge)
        assertNull(cart.payable)
        assertNull(cart.fees.tax)
        assertNull(cart.items.single().total)
        assertEquals("rev-1", cart.revision)
        assertEquals("sealed", cart.quote)
        assertNull(cart.appliedCoupon)
    }

    @Test fun cartJsonRoundTripsForCheckoutRecovery() {
        val cart = Cart.from(JSONObject(demoCart).getJSONObject("cart"))
        val restored = Cart.from(cart.json())
        assertEquals(cart.payable, restored.payable)
        assertEquals(cart.restaurant, restored.restaurant)
        assertEquals(cart.address, restored.address)
        assertEquals(cart.items.single().nutrition, restored.items.single().nutrition)
        assertEquals(cart.items.single().quantity, restored.items.single().quantity)
    }

    @Test fun paymentRoundTripsAndOnlyOpensHttpsLinks() {
        val payment =
            Payment.from(
                JSONObject(
                    """{"orderId":"9","paasId":"p","cartId":null,"bridgeUrl":"http://pay.example","pollingIntervalInMs":1500,"status":"PENDING_PAYMENT"}""",
                ),
            )
        assertNull(payment.cartId)
        assertNull(payment.safeBridgeUrl)
        assertEquals(payment, Payment.from(payment.json()))
        assertEquals("https://x.swiggy.com/p", payment.copy(bridgeUrl = "https://x.swiggy.com/p").safeBridgeUrl)
    }

    @Test fun stagesKeepTheirPersistedNames() {
        assertEquals(
            listOf("choose", "placing", "pending", "unresolved", "confirmed", "failed"),
            PaymentStage.entries.map { it.wire },
        )
        assertEquals(PaymentStage.Pending, PaymentStage.fromWire("pending"))
        assertEquals(PaymentStage.Choose, PaymentStage.fromWire("garbled"))
        assertEquals(
            setOf(PaymentStage.Placing, PaymentStage.Pending, PaymentStage.Unresolved),
            PaymentStage.entries
                .filter {
                    it.inFlight
                }.toSet(),
        )
    }

    @Test fun attemptStatusesMapToStages() {
        assertEquals(PaymentStage.Confirmed, PaymentStage.forPaymentStatus("CONFIRMED"))
        assertEquals(PaymentStage.Pending, PaymentStage.forPaymentStatus("PENDING_PAYMENT"))
        assertEquals(PaymentStage.Failed, PaymentStage.forPaymentStatus("FAILED"))
        assertEquals(PaymentStage.Unresolved, PaymentStage.forPaymentStatus(null))
    }

    @Test fun pollResultsNeverInventAConfirmation() {
        val pending = PaymentStatusUpdate(confirmed = false, terminalFailure = false, orderId = "1")
        assertEquals(PaymentStage.Pending, stageAfterStatus(pending, "PENDING_PAYMENT", localDemo = false))
        assertEquals(PaymentStage.Unresolved, stageAfterStatus(pending, "UNRESOLVED", localDemo = false))
        assertEquals(PaymentStage.Pending, stageAfterStatus(pending, "UNRESOLVED", localDemo = true))
        assertEquals(PaymentStage.Failed, stageAfterStatus(pending.copy(terminalFailure = true), null, localDemo = false))
        assertEquals(PaymentStage.Confirmed, stageAfterStatus(pending.copy(confirmed = true), null, localDemo = false))
    }

    @Test fun onlyTodaysInFlightCheckoutReservesAllowance() {
        val clock = FakeClock()
        val cart = Cart.from(JSONObject(demoCart).getJSONObject("cart"))
        val started = clock.now().toEpochMilli()
        assertEquals(433, reservedSpend(PaymentStage.Unresolved, cart, started, "2026-10-27", clock))
        assertEquals(0, reservedSpend(PaymentStage.Unresolved, cart, started, "2026-10-28", clock))
        assertEquals(0, reservedSpend(PaymentStage.Confirmed, cart, started, "2026-10-27", clock))
        assertEquals(0, reservedSpend(PaymentStage.Pending, cart, 0, "2026-10-27", clock))
    }

    @Test fun paymentMethodsDefaultToEnabled() {
        val methods =
            PaymentMethod.listFrom(
                JSONObject("""{"allMethods":[{"id":"upi","displayName":"UPI"},{"id":"cod","enabled":false},{"displayName":"no id"}]}"""),
            )
        assertEquals(listOf(PaymentMethod("upi", "UPI", true), PaymentMethod("cod", "cod", false)), methods)
        assertFalse(PaymentMethod.listFrom(null).isNotEmpty())
    }

    @Test fun mealParsingDoesNotTurnJsonNullIntoText() {
        val meal =
            Meal.from(
                JSONObject(
                    """{"id":"r:1","dishId":"1","restaurantId":"r","name":"Dal","restaurant":"Hotel","imageUrl":"http://x/img",
                    "veg":null,"itemPrice":null,"etaMinutes":null,"distanceKm":null,"description":null,"offerText":"null"}""",
                ),
            )
        assertEquals("", meal.description)
        assertNull(meal.offerText)
        assertNull(meal.imageUrl)
        assertNull(meal.veg)
        assertEquals("₹180", rupees(180.0))
        assertEquals("₹180.50", rupees(180.5))
    }

    @Test fun noticeVersionsCompareByDateThenRevision() {
        assertTrue(isNewerNotice("2026-10-04.1", "2026-10-02.3"))
        assertTrue(isNewerNotice("2026-10-04.10", "2026-10-04.9"))
        assertFalse(isNewerNotice("2026-10-02.3", "2026-10-04.1"))
        assertFalse(isNewerNotice("2026-10-04.1", "2026-10-04.1"))
    }
}
