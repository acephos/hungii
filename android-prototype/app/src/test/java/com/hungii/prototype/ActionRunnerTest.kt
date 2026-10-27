package com.hungii.prototype

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionRunnerTest {
    @Test fun repeatedTapsOfARunningOperationAreIgnored() =
        runTest {
            val runner = ActionRunner(this) {}
            val gate = CompletableDeferred<Unit>()
            var runs = 0
            runner.launch(Operation.Payment) {
                runs++
                gate.await()
            }
            runner.launch(Operation.Payment) { runs++ }
            advanceUntilIdle()
            assertTrue(runner.isBusy(Operation.Payment))
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, runs)
            assertFalse(runner.loading)
        }

    @Test fun differentOperationsWaitTheirTurnInsteadOfBeingDropped() =
        runTest {
            val runner = ActionRunner(this) {}
            val gate = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            runner.launch(Operation.BackgroundSync) {
                order += "sync started"
                gate.await()
                order += "sync done"
            }
            runner.launch(Operation.Discovery) { order += "search" }
            advanceUntilIdle()
            // Background work never shows as foreground loading.
            assertTrue(runner.isBusy(Operation.Discovery))
            assertEquals(listOf("sync started"), order)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("sync started", "sync done", "search"), order)
        }

    @Test fun backgroundWorkAloneIsNotLoading() =
        runTest {
            val runner = ActionRunner(this) {}
            val gate = CompletableDeferred<Unit>()
            runner.launch(Operation.BackgroundSync) { gate.await() }
            advanceUntilIdle()
            assertFalse(runner.loading)
            gate.complete(Unit)
        }

    @Test fun browserReturnsAreQueuedEvenWhileTheSameOperationRuns() =
        runTest {
            val runner = ActionRunner(this) {}
            val gate = CompletableDeferred<Unit>()
            var callbacks = 0
            runner.launch(Operation.Account) { gate.await() }
            runner.launch(Operation.Account, dedupe = false) { callbacks++ }
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, callbacks)
            assertFalse(runner.isBusy(Operation.Account))
        }

    @Test fun failuresAreReportedWhereTheyBelong() =
        runTest {
            val failures = mutableListOf<String>()
            val runner = ActionRunner(this) { failures += it.code }
            var backgroundError: UiText? = null
            runner.launch(Operation.Cart) { throw ApiFailure("HUNGII_CART_CHANGED", "Review the updated cart.") }
            advanceUntilIdle()
            assertEquals(UiText.Raw("Review the updated cart."), runner.message)
            assertTrue(runner.messageIsError)
            runner.launch(Operation.BackgroundSync, onError = { backgroundError = it }) { throw ApiFailure("HUNGII_OFFLINE", "Offline") }
            advanceUntilIdle()
            assertEquals(UiText.Raw("Review the updated cart."), runner.message)
            assertEquals(UiText.Raw("Offline"), backgroundError)
            assertEquals(listOf("HUNGII_CART_CHANGED", "HUNGII_OFFLINE"), failures)
            runner.launch(Operation.Order) { error("parser broke") }
            advanceUntilIdle()
            assertEquals(UiText.of(R.string.error_request_failed), runner.message)
            runner.launch(Operation.Order) { runner.inform(UiText.Raw("Done")) }
            advanceUntilIdle()
            assertFalse(runner.messageIsError)
        }

    @Test fun cancellationIsNotReportedAsAFailure() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            val runner = ActionRunner(scope) { error("not a failure") }
            runner.launch(Operation.Payment) { CompletableDeferred<Unit>().await() }
            advanceUntilIdle()
            scope.cancel()
            advanceUntilIdle()
            assertNull(runner.message)
            assertFalse(runner.isBusy(Operation.Payment))
        }
}
