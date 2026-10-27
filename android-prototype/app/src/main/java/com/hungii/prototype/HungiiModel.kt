package com.hungii.prototype

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Destructive or consent-changing actions that need a confirmation dialog. */
enum class PrivacyAction {
    SyncProfile,
    Disconnect,
    DeleteAccount,
    DeleteCloudTracker,
    EraseDevice,
}

/**
 * Composition root: wires the state holders together and owns account, privacy and
 * persistence flows that span several of them.
 */
@OptIn(FlowPreview::class)
class HungiiModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val localDemo = BuildConfig.LOCAL_DEMO
    private val clock = Clock.system
    private val db = PrivateTrackerStore(application)
    private val api = HungiiApi(SecureSession(application))

    val navigator = Navigator(restorableScreen(savedState[SCREEN_KEY])) { savedState[SCREEN_KEY] = it.name }
    val actions = ActionRunner(viewModelScope, ::handleApiFailure)
    val day = DayPlan(clock)
    val saved = SavedMeals(clock)
    val connection = SwiggyConnection(localDemo)

    var initializing by mutableStateOf(true)
        private set
    var accountLoading by mutableStateOf(api.signedIn && !localDemo)
        private set
    var signedIn by mutableStateOf(api.signedIn)
        private set
    var offlineMode by mutableStateOf(localDemo)
        private set

    /** The privacy notice consents refer to; the backend's version wins once known. */
    var noticeVersion by mutableStateOf(BuildConfig.PRIVACY_VERSION)
        private set
    var accountOpen by mutableStateOf(false)
    var privacyAction by mutableStateOf<PrivacyAction?>(null)

    /** A short confirmation or hint shown as a snackbar on any screen. */
    var notice by mutableStateOf<UiText?>(null)
        private set

    /** Asks the UI to open the day editor at today's totals. */
    var dayEditorRequested by mutableStateOf(false)
        private set

    val configured get() = api.configured
    val loading get() = actions.loading

    val checkout: Checkout =
        Checkout(
            api = api,
            actions = actions,
            navigator = navigator,
            day = day,
            clock = clock,
            scope = viewModelScope,
            localDemo = localDemo,
            winner = { discovery.winner },
            orderingEnabled = { connection.orderingEnabled },
            saveNow = ::saveNow,
            notify = ::notify,
            openDayEditor = { dayEditorRequested = true },
        )

    val moneyLeft get() = day.tracker.allowance - day.tracker.spent - checkout.reservedSpend

    val discovery: Discovery =
        Discovery(
            api = api,
            actions = actions,
            navigator = navigator,
            day = day,
            moneyLeft = { moneyLeft },
            canSearch = { signedIn && connection.ready },
            onNeedsConnection = { accountOpen = true },
            onNewShortlist = checkout::clearCart,
            notify = ::notify,
        )

    val cloud: CloudSync =
        CloudSync(
            api = api,
            actions = actions,
            scope = viewModelScope,
            profile = { profileJson(day.tracker, day.preferences) },
            applyProfile = ::applyProfile,
            noticeVersion = { noticeVersion },
            signedIn = { signedIn },
        )

    val assistant: Assistant =
        Assistant(
            api = api,
            scope = viewModelScope,
            context = ::assistantContext,
            onOpen = { navigator.go(Screen.Assistant) },
            onTrace = checkout::applyTrace,
        )

    private var owner = api.userId ?: DEVICE_OWNER
    private var ready by mutableStateOf(false)
    private var persistenceJob: Job? = null
    private val initialization: Job

    init {
        initialization =
            viewModelScope.launch {
                db.migrate()
                loadLocal()
                ready = true
                initializing = false
                if (signedIn) refresh(showRestoring = true)
            }
        startPersistence()
        // A running app crosses midnight too; resuming also checks (see onForeground).
        viewModelScope.launch {
            while (isActive) {
                delay(clock.millisUntilNextDay())
                day.ensureToday()
            }
        }
    }

    // Persistence

    private fun snapshot(): JSONObject {
        val o = profileJson(day.tracker, day.preferences).put("offlineMode", offlineMode)
        cloud.putInto(o)
        checkout.putInto(o)
        saved.putInto(o)
        return o.put("privacyVersion", noticeVersion)
    }

    private fun startPersistence() {
        persistenceJob =
            viewModelScope.launch {
                snapshotFlow { if (ready) TrackerRecord(owner, snapshot().toString()) else null }
                    .filterNotNull()
                    .debounce(600)
                    .collect { record ->
                        db.save(record)
                        cloud.onLocalChange()
                    }
            }
    }

    private suspend fun stopPersistence() {
        persistenceJob?.cancelAndJoin()
        ready = false
    }

    private fun resumePersistence() {
        ready = true
        startPersistence()
    }

    private suspend fun saveNow() = db.save(TrackerRecord(owner, snapshot().toString()))

    private suspend fun loadLocal() {
        checkout.restore(null)
        val stored =
            try {
                db.get(owner)?.payload
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                notify(UiText.of(R.string.load_decrypt_failed))
                null
            } ?: return
        try {
            val o = JSONObject(stored)
            day.restore(TrackerState.from(o, clock.today()), o.optJSONObject("preferences")?.let(Preferences::from) ?: day.preferences)
            checkout.restore(o)
            if (!signedIn) offlineMode = o.optBoolean("offlineMode")
            cloud.restoreLocal(o)
            saved.restore(o)
            val consentedUnder = o.stringOrNull("privacyVersion")
            if (consentedUnder != null && consentedUnder != noticeVersion) {
                if (isNewerNotice(consentedUnder, noticeVersion)) {
                    // The backend announced a newer notice before this build knew it.
                    noticeVersion = consentedUnder
                } else {
                    onNoticeChanged()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            notify(UiText.of(R.string.load_failed))
        }
    }

    private fun applyProfile(state: JSONObject) {
        day.restore(TrackerState.from(state, clock.today()), state.optJSONObject("preferences")?.let(Preferences::from) ?: day.preferences)
    }

    /** Switches the encrypted local profile, e.g. on sign-in or sign-out. */
    private suspend fun switchProfile(newOwner: String) {
        stopPersistence()
        owner = newOwner
        day.reset()
        saved.clear()
        loadLocal()
        resumePersistence()
    }

    private fun onNoticeChanged() {
        cloud.noticeChanged()
        saved.clear()
    }

    private fun clearProviderData() {
        checkout.clear()
        connection.clear()
        discovery.clear()
        saved.clear()
    }

    // Account and connection

    /** Session-wide consequences of an error, whichever request hit it. */
    private suspend fun handleApiFailure(failure: ApiFailure) {
        when (failure.code) {
            "HUNGII_SYNC_CONFLICT" -> {
                cloud.onConflict(UiText.Raw(failure.message))
            }

            "HUNGII_LOGIN_REQUIRED" -> {
                cloud.reset()
                api.clearSession()
                signedIn = false
                clearProviderData()
                switchProfile(DEVICE_OWNER)
            }

            "HUNGII_RECONNECT" -> {
                connection.connected = false
                connection.addressId = null
                discovery.clear()
                checkout.clearCart()
            }
        }
    }

    fun signInUrl() = api.signInUrl()

    /** Browser returns: WorkOS sign-in or the Swiggy connection page. */
    fun handleCallback(uri: Uri) =
        actions.launch(Operation.Account, dedupe = false) {
            try {
                if (uri.host == "auth-return") {
                    accountLoading = true
                    initialization.join()
                    api.callback(uri)
                    signedIn = true
                    offlineMode = false
                    cloud.reset()
                    switchProfile(api.userId ?: DEVICE_OWNER)
                }
                refreshAccount()
            } finally {
                accountLoading = false
            }
        }

    /** [showRestoring] covers the screen while an account is restored at start-up. */
    fun refresh(showRestoring: Boolean = false) =
        actions.launch(Operation.Account) {
            if (showRestoring) accountLoading = true
            try {
                refreshAccount()
            } finally {
                accountLoading = false
            }
        }

    private suspend fun refreshAccount() {
        applyStatus(api.action("status"))
        if (!localDemo) cloud.restore()
        ensureAddress()
        checkout.recoverAfterRefresh()
    }

    private fun applyStatus(status: JSONObject) {
        val notice = status.stringOrNull("privacyVersion") ?: noticeVersion
        if (notice != noticeVersion) {
            noticeVersion = notice
            onNoticeChanged()
        }
        connection.applyStatus(status)
        if (!connection.connected) {
            discovery.clear()
            checkout.clearCart()
        }
    }

    private suspend fun ensureAddress() {
        if (!connection.connected || connection.addressId != null) return
        loadAddresses()
        val first = connection.addresses.firstOrNull()
        if (localDemo && first != null) {
            connection.addressId = first.id
            api.action("select_address", JSONObject().put("addressId", first.id))
        }
    }

    private suspend fun loadAddresses(page: Int = 1) =
        connection.applyAddresses(api.action("addresses", JSONObject().put("page", page)), page)

    fun connect(onUrl: (String) -> Unit) =
        actions.launch(Operation.Connection) {
            val result = api.action("connect", JSONObject().put("consent", true).put("privacyVersion", noticeVersion))
            if (localDemo) {
                applyStatus(api.action("status"))
                ensureAddress()
            } else {
                onUrl(result.getString("authorizationUrl"))
            }
        }

    fun addressList(page: Int = 1) =
        actions.launch(Operation.Connection) {
            loadAddresses(page)
            accountOpen = true
        }

    fun selectAddress(address: DeliveryAddress) =
        actions.launch(Operation.Connection) {
            api.action("select_address", JSONObject().put("addressId", address.id).put("page", connection.addressPage))
            connection.addressId = address.id
            discovery.clear()
            checkout.clearCart()
            accountOpen = false
        }

    fun signOut(onUrl: (String) -> Unit) =
        actions.launch(Operation.Account) {
            try {
                api.signOut()?.let(onUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                actions.report(UiText.of(R.string.signout_remote_unconfirmed))
            }
            signedIn = false
            cloud.reset()
            clearProviderData()
            switchProfile(if (localDemo) DEMO_OWNER else DEVICE_OWNER)
        }

    fun useOffline() {
        offlineMode = true
        cloud.keepOnDevice()
    }

    // Privacy

    fun confirmPrivacyAction() {
        val action = privacyAction ?: return
        privacyAction = null
        when (action) {
            PrivacyAction.SyncProfile -> cloud.allow()
            PrivacyAction.Disconnect -> disconnect()
            PrivacyAction.DeleteCloudTracker -> deleteCloudTracker()
            PrivacyAction.DeleteAccount -> deleteAccount()
            PrivacyAction.EraseDevice -> eraseDevice()
        }
    }

    private fun disconnect() =
        actions.launch(Operation.Privacy) {
            val result = api.action("disconnect", JSONObject().put("confirm", true))
            stopPersistence()
            clearProviderData()
            db.erase(owner)
            saveNow()
            resumePersistence()
            actions.inform(
                UiText.of(
                    if (result.optBoolean(
                            "revocationConfirmed",
                        )
                    ) {
                        R.string.privacy_disconnected
                    } else {
                        R.string.privacy_disconnected_unconfirmed
                    },
                ),
            )
        }

    private fun deleteCloudTracker() =
        actions.launch(Operation.Privacy) {
            cloud.cancelScheduled()
            api.action("delete_cloud_tracker", JSONObject().put("confirm", true))
            cloud.erased()
            actions.inform(UiText.of(R.string.privacy_cloud_erased))
        }

    private fun deleteAccount() =
        actions.launch(Operation.Privacy) {
            cloud.cancelScheduled()
            val result = api.action("delete_account", JSONObject().put("confirm", true))
            stopPersistence()
            cloud.reset()
            db.erase(owner)
            clearProviderData()
            day.reset()
            if (!result.optBoolean("deleted")) {
                resumePersistence()
                actions.report(UiText.of(R.string.privacy_deletion_pending))
                return@launch
            }
            api.clearSession()
            signedIn = false
            accountOpen = false
            // Continue with this device's separate local-only profile, untouched.
            owner = DEVICE_OWNER
            loadLocal()
            resumePersistence()
            actions.inform(UiText.of(R.string.privacy_erased))
        }

    private fun eraseDevice() =
        actions.launch(Operation.Privacy) {
            stopPersistence()
            db.erase(owner)
            clearProviderData()
            day.reset()
            // The cloud copy is managed separately; never overwrite it with blank defaults.
            if (cloud.enabled) cloud.pause()
            resumePersistence()
            actions.inform(UiText.of(R.string.privacy_erased))
        }

    fun forgetSaved() =
        actions.launch(Operation.Privacy) {
            stopPersistence()
            saved.clear()
            // Erasing also rotates the device key, so older ciphertext cannot be read.
            db.erase(owner)
            saveNow()
            resumePersistence()
        }

    // Navigation and notices

    fun go(screen: Screen) = navigator.go(screen)

    fun goBack() {
        discovery.stopShuffle()
        navigator.go(navigator.screen.back)
    }

    /** The app returned to the foreground, possibly on a later day. */
    fun onForeground() {
        day.ensureToday()
    }

    fun notify(text: UiText) {
        notice = text
    }

    fun consumeNotice() {
        notice = null
    }

    fun consumeDayEditorRequest() {
        dayEditorRequested = false
    }

    // Assistant

    fun applyAssistantProposal() {
        val proposal = assistant.proposal ?: return
        assistant.proposal = null
        day.ensureToday()
        when (val effect = assistantEffect(proposal, day.tracker, day.preferences)) {
            is AssistantEffect.UpdateDay -> {
                day.restore(effect.tracker, effect.preferences)
                notify(UiText.of(R.string.assistant_change_confirmed))
            }

            is AssistantEffect.Search -> {
                day.updatePreferences { it.copy(query = effect.query) }
                discovery.search()
            }

            is AssistantEffect.Navigate -> {
                navigator.go(effect.screen)
            }

            AssistantEffect.ReviewCart -> {
                if (discovery.winner != null) checkout.review()
            }

            AssistantEffect.SaveWinner -> {
                discovery.winner?.let {
                    saved.toggle(it)
                    if (saved.isSaved(it)) notify(UiText.of(R.string.assistant_change_confirmed))
                }
            }

            AssistantEffect.Rejected -> {
                notify(UiText.of(R.string.assistant_change_rejected))
            }
        }
    }

    private fun assistantContext(): JSONObject {
        val t = day.tracker
        val goals =
            JSONObject()
                .put("calories", t.calorieGoal)
                .put("protein", t.proteinGoal)
                .put("carbs", t.carbGoal)
                .put("fat", t.fatGoal)
        return JSONObject()
            .put("caloriesLeft", t.caloriesLeft.label)
            .put("proteinLeft", (t.proteinGoal - t.intake.protein.mid).toDouble())
            .put("carbsLeft", (t.carbGoal - t.intake.carbs.mid).toDouble())
            .put("fatLeft", (t.fatGoal - t.intake.fat.mid).toDouble())
            .put("moneyLeft", moneyLeft)
            .put("allowance", t.allowance)
            .put("spent", t.spent)
            .put("goals", goals)
            .put("opportunities", t.opportunities)
            .put("query", day.preferences.query)
            .put("taste", day.preferences.taste)
            .put("screen", navigator.screen.name)
            .put("winner", discovery.winner?.name ?: "")
    }

    override fun onCleared() {
        db.close()
    }

    private companion object {
        const val SCREEN_KEY = "screen"
        const val DEVICE_OWNER = "device"
        const val DEMO_OWNER = "local-demo"

        /**
         * After process death, return to the closest screen whose data survives:
         * search results are not persisted, and checkout resumes from Home.
         */
        fun restorableScreen(name: String?): Screen =
            when (val screen = Screen.entries.firstOrNull { it.name == name } ?: Screen.Home) {
                Screen.Finalists, Screen.Draw, Screen.Winner, Screen.Review -> Screen.Discover
                Screen.Payment, Screen.Order -> Screen.Home
                else -> screen
            }
    }
}

/** Notice versions look like 2026-10-04.1: a date, then a same-day revision. */
internal fun isNewerNotice(
    candidate: String,
    current: String,
): Boolean {
    fun parts(version: String) = version.split('.', limit = 2).let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    val (candidateDate, candidateRevision) = parts(candidate)
    val (currentDate, currentRevision) = parts(current)
    return candidateDate > currentDate || (candidateDate == currentDate && candidateRevision > currentRevision)
}
