package com.hungii.prototype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** The cloud copy as returned by state_get. */
data class RemoteProfile(
    val state: JSONObject?,
    val revision: String?,
    val consentVersion: String?,
) {
    companion object {
        fun from(response: JSONObject) =
            RemoteProfile(response.optJSONObject("state"), response.stringOrNull("updatedAt"), response.stringOrNull("consentVersion"))
    }
}

/** How a restore treats the cloud copy. */
enum class RestoreAction {
    /** Both copies changed since the last sync: the user must choose. */
    Conflict,

    /** Adopt the cloud copy. */
    ApplyRemote,

    /** The copy expired or was erased elsewhere; that is not permission to recreate it. */
    RemoteMissing,

    /** Keep the device copy; it uploads if sync is on. */
    KeepLocal,
}

/** Pure restore rule, separated for tests. [localChanged] is measured before fetching. */
fun restoreAction(
    localChanged: Boolean,
    remote: RemoteProfile,
    knownRevision: String?,
    force: Boolean,
): RestoreAction =
    when {
        !force && localChanged && remote.revision != knownRevision -> RestoreAction.Conflict
        remote.state != null && (force || !localChanged) -> RestoreAction.ApplyRemote
        remote.state == null && knownRevision != null -> RestoreAction.RemoteMissing
        else -> RestoreAction.KeepLocal
    }

/**
 * Optional profile sync with revision checks. Signing in never uploads by itself;
 * the user allows sync, chooses on conflict, and a missing cloud copy stays missing.
 */
class CloudSync(
    private val api: Backend,
    private val actions: ActionRunner,
    private val scope: CoroutineScope,
    private val profile: () -> JSONObject,
    private val applyProfile: (JSONObject) -> Unit,
    private val noticeVersion: () -> String,
    private val signedIn: () -> Boolean,
) {
    var enabled by mutableStateOf(false)
        private set
    var decisionMade by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var conflict by mutableStateOf(false)
        private set
    var setupPending by mutableStateOf(false)
        private set
    var message by mutableStateOf<UiText?>(null)
        private set
    private var revision: String? = null
    private var lastSynced: String? = null
    private var job: Job? = null
    private var epoch = 0

    private val localChanged get() = enabled && lastSynced != null && profile().toString() != lastSynced

    /** Persistence calls this after each local save. */
    fun onLocalChange() {
        if (ready && enabled && signedIn() && profile().toString() != lastSynced) schedule()
    }

    private fun schedule() {
        job?.cancel()
        val scheduled = epoch
        job =
            scope.launch {
                delay(1_000)
                if (ready && enabled && signedIn()) {
                    actions.launch(Operation.BackgroundSync, onError = { message = it }) {
                        // A pause, erasure or sign-out while this waited must not upload.
                        if (epoch == scheduled && ready && enabled && signedIn()) sync()
                    }
                }
            }
    }

    /** Stops a scheduled upload before an erasure request. */
    fun cancelScheduled() {
        epoch++
        job?.cancel()
    }

    /** Called inside an account refresh. */
    suspend fun restore(force: Boolean = false) {
        if (decisionMade && !enabled && !force) {
            ready = false
            setupPending = false
            return
        }
        val changed = localChanged
        val remote = RemoteProfile.from(api.action("state_get"))
        when (restoreAction(changed, remote, revision, force)) {
            RestoreAction.Conflict -> {
                ready = false
                conflict = true
                message = UiText.of(R.string.sync_changed_elsewhere)
                return
            }

            RestoreAction.ApplyRemote -> {
                val state = remote.state!!
                applyProfile(state)
                lastSynced = profile().toString()
                enabled = enabled && remote.consentVersion == noticeVersion() && state.has("preferences")
                if (enabled) decisionMade = true
                message = UiText.of(R.string.sync_restored)
            }

            RestoreAction.RemoteMissing -> {
                enabled = false
                message = UiText.of(R.string.sync_cloud_missing)
            }

            RestoreAction.KeepLocal -> {
                // The device copy stays; it uploads below when sync is on.
            }
        }
        revision = remote.revision
        ready = true
        conflict = false
        setupPending = !decisionMade
        if (enabled && profile().toString() != lastSynced) schedule()
    }

    /** The user allowed sync from the welcome screen or Accounts. */
    fun allow() = actions.launch(Operation.Sync) { sync() }

    private suspend fun sync() {
        if (!ready) {
            val remote = RemoteProfile.from(api.action("state_get"))
            revision = remote.revision
            val local = profile().toString()
            if (remote.state != null && remote.state.toString() != local && lastSynced != local) {
                conflict = true
                message = UiText.of(R.string.sync_choose_copy)
                return
            }
            ready = true
        }
        if (conflict) return
        push()
    }

    private suspend fun push() {
        val started = epoch
        val current = profile().toString()
        val args =
            JSONObject()
                .put("state", JSONObject(current))
                .put("privacyVersion", noticeVersion())
                .put("expectedUpdatedAt", revision.orJsonNull())
        val response = api.action("state_save", args)
        // Pausing or resetting during the upload wins over its late result.
        if (started != epoch) return
        revision = response.getString("updatedAt")
        lastSynced = current
        ready = true
        conflict = false
        enabled = true
        decisionMade = true
        setupPending = false
        message = UiText.of(R.string.sync_done)
    }

    fun useCloudCopy() =
        actions.launch(Operation.Sync) {
            enabled = true
            decisionMade = true
            restore(force = true)
        }

    fun keepDeviceCopy() =
        actions.launch(Operation.Sync) {
            revision = RemoteProfile.from(api.action("state_get")).revision
            conflict = false
            ready = true
            push()
        }

    fun restoreFromCloud() =
        actions.launch(Operation.Sync) {
            restore(force = true)
            decisionMade = true
            setupPending = false
        }

    /** Stop uploading from this device; nothing is erased. */
    fun pause() {
        epoch++
        job?.cancel()
        enabled = false
        ready = false
        decisionMade = true
        setupPending = false
        message = UiText.of(R.string.sync_paused)
    }

    fun keepOnDevice() {
        decisionMade = true
        setupPending = false
        enabled = false
    }

    /** The cloud copy was erased; this device keeps its own copy and stops syncing. */
    fun erased() {
        reset()
        decisionMade = true
    }

    fun onConflict(text: UiText) {
        conflict = true
        ready = false
        message = text
    }

    /** Consent was given under an older notice, so it must be given again. */
    fun noticeChanged() {
        job?.cancel()
        enabled = false
        ready = false
        decisionMade = false
        message = UiText.of(R.string.sync_notice_updated)
    }

    fun reset() {
        epoch++
        job?.cancel()
        job = null
        enabled = false
        ready = false
        conflict = false
        revision = null
        lastSynced = null
        decisionMade = false
        setupPending = false
        message = null
    }

    fun putInto(o: JSONObject): JSONObject =
        o
            .put("cloudSyncEnabled", enabled)
            .put("cloudDecisionMade", decisionMade)
            .put("cloudRevision", revision.orJsonNull())
            .put("lastSyncedState", lastSynced.orJsonNull())

    fun restoreLocal(o: JSONObject) {
        enabled = o.optBoolean("cloudSyncEnabled")
        decisionMade = o.optBoolean("cloudDecisionMade")
        revision = o.stringOrNull("cloudRevision")
        lastSynced = o.stringOrNull("lastSyncedState")
    }
}
