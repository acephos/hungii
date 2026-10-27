package com.hungii.prototype

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutFlowTest {
    private val basket =
        """{"cartId":"c1","restaurantId":"r1","restaurant":"Real Kitchen","address":"Flat 4",
        "items":[{"menuItemId":"m1","name":"Thali","quantity":1,"total":433}],"payable":433,"revision":"rev","quote":"sealed"}"""

    private class Harness(
        scope: TestScope,
        val clock: Clock,
        localDemo: Boolean,
    ) {
        val backend = FakeBackend()
        val failures = mutableListOf<String>()
        val actions = ActionRunner(scope) { failures += it.code }
        val navigator = Navigator()
        val day = DayPlan(clock)
        val saves = mutableListOf<PaymentStage>()
        val notices = mutableListOf<UiText>()
        var dayEditorOpened = false
        lateinit var checkout: Checkout

        init {
            checkout =
                Checkout(
                    api = backend,
                    actions = actions,
                    navigator = navigator,
                    day = day,
                    clock = clock,
                    scope = scope,
                    localDemo = localDemo,
                    winner = { meal("1") },
                    orderingEnabled = { true },
                    saveNow = { saves += checkout.stage },
                    notify = { notices += it },
                    openDayEditor = { dayEditorOpened = true },
                )
        }
    }

    private fun attempt(
        status: String,
        orderId: String? = "order-9",
        createdAt: Long,
    ) = """{"requestId":"11111111-1111-1111-1111-111111111111","createdAt":$createdAt,"checkoutCart":$basket,
        "payment":{"orderId":${orderId?.let { "\"$it\"" } ?: "null"},"paasId":"paas","cartId":"c1","bridgeUrl":"https://pay.swiggy.com/x",
        "pollingIntervalInMs":5000,"maxTimeToPollForInMs":20000,"status":"$status"},
        "paymentStatus":{"orderId":${orderId?.let { "\"$it\"" } ?: "null"},"confirmed":${status == "CONFIRMED"},
        "isTerminalFailure":${status == "FAILED"}}}"""

    private fun TestScope.readyToPay(h: Harness) {
        h.backend.respond("payment_options", """{"paymentOptions":{"allMethods":[{"id":"upi","displayName":"UPI"}]},"cart":$basket}""")
        h.checkout.choosePayment()
        advanceUntilIdle()
        h.checkout.selectedMethodId = "upi"
    }

    @Test fun requestIdIsSavedBeforeTheOrderIsSent() =
        runTest {
            val h = Harness(this, SchedulerClock(this), localDemo = false)
            readyToPay(h)
            h.backend.beforeEach = { action ->
                if (action == "checkout") {
                    assertEquals(listOf(PaymentStage.Placing), h.saves)
                    assertNotNull(h.checkout.requestId)
                }
            }
            h.backend.respond("checkout", attempt("CONFIRMED", createdAt = 0))
            h.backend.respond("order", """{"orderId":"order-9","tracking":{"statusMessage":"Preparing"}}""")
            h.checkout.beginPayment()
            advanceUntilIdle()
            val sent =
                h.backend.calls
                    .single { it.first == "checkout" }
                    .second
            assertEquals(433.0, sent.getDouble("payable"), 0.0)
            assertEquals("rev", sent.getString("revision"))
            assertEquals(PaymentStage.Confirmed, h.checkout.stage)
            assertEquals(Screen.Order, h.navigator.screen)
            assertEquals(
                "Preparing",
                h.checkout.order
                    ?.tracking
                    ?.headline,
            )
        }

    @Test fun provenRejectionsAllowAFreshReview() =
        runTest {
            val h = Harness(this, SchedulerClock(this), localDemo = false)
            readyToPay(h)
            h.backend.fail("checkout", "HUNGII_CART_CHANGED", "Your basket changed.")
            h.checkout.beginPayment()
            advanceUntilIdle()
            assertEquals(PaymentStage.Choose, h.checkout.stage)
            assertNull(h.checkout.requestId)
            assertTrue(h.checkout.refreshRequired)
            assertEquals(UiText.Raw("Your basket changed."), h.actions.message)
        }

    @Test fun anAmbiguousFailureStaysUnresolvedAndKeepsItsRequestId() =
        runTest {
            val h = Harness(this, SchedulerClock(this), localDemo = false)
            readyToPay(h)
            h.backend.fail("checkout", "HUNGII_OFFLINE")
            h.checkout.beginPayment()
            advanceUntilIdle()
            assertEquals(PaymentStage.Unresolved, h.checkout.stage)
            assertNotNull(h.checkout.requestId)
            // Choosing payment again returns to the unresolved attempt instead of starting another.
            h.checkout.choosePayment()
            assertEquals(PaymentStage.Unresolved, h.checkout.stage)
            assertEquals(1, h.backend.actions().count { it == "payment_options" })
        }

    @Test fun aConfirmedOrderCountsTowardTodaysSpendingOnce() =
        runTest {
            val clock = SchedulerClock(this)
            val h = Harness(this, clock, localDemo = false)
            readyToPay(h)
            val started = clock.now().toEpochMilli()
            h.backend.respond("checkout", attempt("CONFIRMED", createdAt = started))
            h.backend.respond("order", """{"orderId":"order-9"}""")
            h.backend.respond("checkout_resume", attempt("CONFIRMED", createdAt = started))
            h.checkout.beginPayment()
            advanceUntilIdle()
            assertEquals(433, h.day.tracker.spent)
            h.checkout.resumeCheckout()
            advanceUntilIdle()
            assertEquals(433, h.day.tracker.spent)
            assertEquals(0, h.checkout.reservedSpend)
        }

    @Test fun yesterdaysCheckoutConfirmedTodayDoesNotChargeToday() =
        runTest {
            val clock = FakeClock(FakeClock.at("2026-10-26T23:58"))
            val h = Harness(this, clock, localDemo = false)
            readyToPay(h)
            h.backend.beforeEach = { if (it == "checkout") clock.set("2026-10-27T00:03") }
            h.backend.respond("checkout", attempt("CONFIRMED", createdAt = FakeClock.at("2026-10-26T23:58").toEpochMilli()))
            h.backend.respond("order", """{"orderId":"order-9"}""")
            h.checkout.beginPayment()
            advanceUntilIdle()
            assertEquals("2026-10-27", h.day.tracker.day)
            assertEquals(0, h.day.tracker.spent)
        }

    @Test fun aConfirmationWithoutAnOrderIdIsNotTreatedAsTrackable() =
        runTest {
            val h = Harness(this, SchedulerClock(this), localDemo = false)
            readyToPay(h)
            h.backend.respond("checkout", attempt("CONFIRMED", orderId = null, createdAt = 0))
            h.checkout.beginPayment()
            advanceUntilIdle()
            assertEquals(PaymentStage.Unresolved, h.checkout.stage)
            assertEquals(0, h.day.tracker.spent)
        }

    @Test fun pendingPaymentIsPolledUntilConfirmed() =
        runTest {
            val clock = SchedulerClock(this)
            val h = Harness(this, clock, localDemo = false)
            readyToPay(h)
            val started = clock.now().toEpochMilli()
            h.backend.respond("checkout", attempt("PENDING_PAYMENT", createdAt = started))
            h.backend.respond("payment_status", attempt("PENDING_PAYMENT", createdAt = started))
            h.backend.respond("payment_status", attempt("CONFIRMED", createdAt = started))
            h.backend.respond("order", """{"orderId":"order-9"}""")
            h.checkout.beginPayment()
            advanceTimeBy(1)
            assertEquals(PaymentStage.Pending, h.checkout.stage)
            assertEquals(433, h.checkout.reservedSpend)
            advanceUntilIdle()
            assertEquals(PaymentStage.Confirmed, h.checkout.stage)
            assertEquals(2, h.backend.actions().count { it == "payment_status" })
            assertEquals(433, h.day.tracker.spent)
        }

    @Test fun pollingThatTimesOutBecomesUnresolved() =
        runTest {
            val clock = SchedulerClock(this)
            val h = Harness(this, clock, localDemo = false)
            readyToPay(h)
            val started = clock.now().toEpochMilli()
            h.backend.respond("checkout", attempt("PENDING_PAYMENT", createdAt = started))
            h.backend.respond("payment_status", attempt("PENDING_PAYMENT", createdAt = started))
            h.checkout.beginPayment()
            advanceUntilIdle()
            assertEquals(PaymentStage.Unresolved, h.checkout.stage)
            assertEquals(UiText.of(R.string.payment_taking_longer), h.actions.message)
        }

    @Test fun cancelledPollingDoesNotMarkTheCheckoutUnresolved() =
        runTest {
            val clock = SchedulerClock(this)
            val h = Harness(this, clock, localDemo = false)
            readyToPay(h)
            val started = clock.now().toEpochMilli()
            val inFlight = CompletableDeferred<JSONObject>()
            h.backend.respond("checkout", attempt("PENDING_PAYMENT", createdAt = started))
            h.backend.on("payment_status") { inFlight.await() }
            h.checkout.beginPayment()
            advanceTimeBy(5_500)
            assertEquals(1, h.backend.actions().count { it == "payment_status" })
            // Signing out while a status request is in flight.
            h.checkout.clear()
            advanceUntilIdle()
            assertEquals(PaymentStage.Choose, h.checkout.stage)
            assertNull(h.actions.message)
        }

    @Test fun loggingARealOrderOpensTheEditorInsteadOfGuessing() =
        runTest {
            val h = Harness(this, SchedulerClock(this), localDemo = false)
            h.checkout.logOrderAsEaten()
            assertTrue(h.dayEditorOpened)
            assertEquals(Screen.Day, h.navigator.screen)
            assertEquals(listOf(UiText.of(R.string.log_real_order_hint)), h.notices)
            assertEquals(Nutrition.zero, h.day.tracker.intake)
        }

    @Test fun loggingASimulatorOrderAddsItsEstimateOnce() =
        runTest {
            val h = Harness(this, SchedulerClock(this), localDemo = true)
            val nutritious =
                basket.replace(
                    "\"total\":433}",
                    "\"total\":433,\"nutrition\":{\"calories\":[500,600],\"protein\":[30,36],\"carbs\":[50,60],\"fat\":[14,18]}}",
                )
            h.checkout.restore(
                JSONObject()
                    .put("paymentStage", "confirmed")
                    .put("payment", JSONObject().put("orderId", "7"))
                    .put("checkoutCart", JSONObject(nutritious)),
            )
            h.checkout.logOrderAsEaten()
            h.checkout.logOrderAsEaten()
            assertEquals(Span(500, 600), h.day.tracker.intake.calories)
            assertEquals(1, h.day.tracker.opportunities)
            assertEquals(listOf(UiText.of(R.string.log_estimate_logged), UiText.of(R.string.log_already_logged)), h.notices)
        }

    @Test fun checkoutStateSurvivesARestart() =
        runTest {
            val clock = SchedulerClock(this)
            val h = Harness(this, clock, localDemo = false)
            readyToPay(h)
            h.backend.fail("checkout", "HUNGII_OFFLINE")
            h.checkout.beginPayment()
            advanceUntilIdle()
            val saved = h.checkout.putInto(JSONObject())
            val restarted = Harness(this, clock, localDemo = false)
            restarted.checkout.restore(saved)
            assertEquals(PaymentStage.Unresolved, restarted.checkout.stage)
            assertEquals(h.checkout.requestId, restarted.checkout.requestId)
            assertEquals(433.0, restarted.checkout.checkoutCart?.payable!!, 0.0)
            assertEquals(433, restarted.checkout.reservedSpend)
        }
}
