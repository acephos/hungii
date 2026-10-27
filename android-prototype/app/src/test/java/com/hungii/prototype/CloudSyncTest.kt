package com.hungii.prototype

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncTest {
    private val remoteState = JSONObject().put("calorieGoal", 1900).put("preferences", JSONObject())

    @Test fun restoreDecisionTable() {
        val remote = RemoteProfile(remoteState, "r2", "v1")
        assertEquals(RestoreAction.Conflict, restoreAction(localChanged = true, remote, knownRevision = "r1", force = false))
        assertEquals(RestoreAction.KeepLocal, restoreAction(localChanged = true, remote, knownRevision = "r2", force = false))
        assertEquals(RestoreAction.ApplyRemote, restoreAction(localChanged = false, remote, knownRevision = "r1", force = false))
        assertEquals(RestoreAction.ApplyRemote, restoreAction(localChanged = true, remote, knownRevision = "r1", force = true))
        assertEquals(RestoreAction.RemoteMissing, restoreAction(localChanged = false, RemoteProfile(null, null, null), "r1", force = false))
        assertEquals(RestoreAction.KeepLocal, restoreAction(localChanged = false, RemoteProfile(null, null, null), null, force = false))
    }

    private class Harness(
        scope: TestScope,
    ) {
        val backend = FakeBackend()
        val actions = ActionRunner(scope) {}
        var profile = JSONObject().put("calorieGoal", 2200)
        val applied = mutableListOf<JSONObject>()
        val sync =
            CloudSync(
                api = backend,
                actions = actions,
                scope = scope,
                profile = { JSONObject(profile.toString()) },
                applyProfile = {
                    applied += it
                    profile = JSONObject(it.toString())
                },
                noticeVersion = { "v1" },
                signedIn = { true },
            )

        fun saves() = backend.actions().count { it == "state_save" }

        /** Sign-in restore, then the user allows sync. */
        fun TestScope.enable() {
            backend.respond("state_get", """{"state":null,"updatedAt":null}""")
            backend.respond("state_save", """{"saved":true,"updatedAt":"2026-10-27T09:00:00Z"}""")
            actions.launch(Operation.Account) { sync.restore() }
            advanceUntilIdle()
            sync.allow()
            advanceUntilIdle()
        }
    }

    @Test fun signingInRestoresWithoutUploadingAndAsksFirst() =
        runTest {
            val h = Harness(this)
            h.backend.respond("state_get", """{"state":$remoteState,"updatedAt":"r1","consentVersion":"v1"}""")
            h.actions.launch(Operation.Account) { h.sync.restore() }
            advanceUntilIdle()
            assertEquals(1, h.applied.size)
            assertTrue(h.sync.setupPending)
            assertFalse(h.sync.enabled)
            assertEquals(0, h.saves())
        }

    @Test fun localEditsUploadAfterSyncIsAllowed() =
        runTest {
            val h = Harness(this)
            with(h) { enable() }
            assertTrue(h.sync.enabled)
            assertEquals(1, h.saves())
            h.profile.put("calorieGoal", 2000)
            h.sync.onLocalChange()
            advanceUntilIdle()
            assertEquals(2, h.saves())
            val sent =
                h.backend.calls
                    .last { it.first == "state_save" }
                    .second
            assertEquals("2026-10-27T09:00:00Z", sent.getString("expectedUpdatedAt"))
        }

    @Test fun changesOnBothDevicesNeedAChoice() =
        runTest {
            val h = Harness(this)
            with(h) { enable() }
            h.profile.put("calorieGoal", 2100)
            h.backend.respond("state_get", """{"state":$remoteState,"updatedAt":"elsewhere","consentVersion":"v1"}""")
            h.actions.launch(Operation.Account) { h.sync.restore() }
            advanceUntilIdle()
            assertTrue(h.sync.conflict)
            assertFalse(h.sync.ready)
            assertTrue(h.applied.isEmpty())
            assertEquals(UiText.of(R.string.sync_changed_elsewhere), h.sync.message)
        }

    @Test fun anErasedCloudCopyIsNotRecreatedAutomatically() =
        runTest {
            val h = Harness(this)
            with(h) { enable() }
            h.backend.respond("state_get", """{"state":null,"updatedAt":null}""")
            h.actions.launch(Operation.Account) { h.sync.restore() }
            advanceUntilIdle()
            assertFalse(h.sync.enabled)
            h.profile.put("calorieGoal", 1500)
            h.sync.onLocalChange()
            advanceUntilIdle()
            assertEquals(1, h.saves())
        }

    @Test fun aQueuedUploadIsDroppedWhenSyncStopsMeanwhile() =
        runTest {
            val h = Harness(this)
            with(h) { enable() }
            val busy = CompletableDeferred<Unit>()
            h.actions.launch(Operation.Privacy) { busy.await() }
            h.profile.put("calorieGoal", 1700)
            h.sync.onLocalChange()
            advanceUntilIdle()
            // The upload waits behind the erasure request, which then stops sync.
            h.sync.erased()
            busy.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, h.saves())
            assertFalse(h.sync.enabled)
            assertTrue(h.sync.decisionMade)
        }

    @Test fun pausingCancelsAScheduledUpload() =
        runTest {
            val h = Harness(this)
            with(h) { enable() }
            h.profile.put("calorieGoal", 1600)
            h.sync.onLocalChange()
            h.sync.pause()
            advanceUntilIdle()
            assertEquals(1, h.saves())
            assertEquals(UiText.of(R.string.sync_paused), h.sync.message)
        }

    @Test fun aNewNoticeRequiresConsentAgain() =
        runTest {
            val h = Harness(this)
            with(h) { enable() }
            h.sync.noticeChanged()
            assertFalse(h.sync.enabled)
            assertFalse(h.sync.decisionMade)
            val persisted = h.sync.putInto(JSONObject())
            assertFalse(persisted.getBoolean("cloudSyncEnabled"))
        }
}
