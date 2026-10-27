package com.hungii.prototype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** The Hungii backend or Simulator gateway. */
fun interface Backend {
    suspend fun action(
        action: String,
        args: JSONObject,
    ): JSONObject
}

suspend fun Backend.action(action: String): JSONObject = action(action, JSONObject())

/** What a request is for, so the UI can show the right progress and background work stays quiet. */
enum class Operation(
    val foreground: Boolean = true,
) {
    Account,
    Connection,
    Discovery,
    Cart,
    Payment,
    Order,
    Privacy,
    Sync,
    BackgroundSync(foreground = false),
}

/**
 * Runs requests one at a time. The backend admits one provider request per user, so
 * overlapping calls would fail; later requests wait instead of being dropped. A repeat
 * of an operation that is already running is ignored, which prevents double submits.
 */
class ActionRunner(
    private val scope: CoroutineScope,
    private val onApiFailure: suspend (ApiFailure) -> Unit,
) {
    private val gate = Mutex()
    private val running = mutableMapOf<Operation, Int>()

    var busy by mutableStateOf(emptySet<Operation>())
        private set

    /** The latest foreground request outcome, shown near the action that caused it. */
    var message by mutableStateOf<UiText?>(null)
        private set

    /** Errors are highlighted; confirmations such as "Disconnected" are not. */
    var messageIsError by mutableStateOf(true)
        private set

    val loading get() = busy.any { it.foreground }

    fun isBusy(operation: Operation) = operation in busy

    fun report(text: UiText?) {
        message = text
        messageIsError = true
    }

    fun inform(text: UiText) {
        message = text
        messageIsError = false
    }

    /**
     * Queues [block]. With [dedupe], a second request for a running [operation] is
     * ignored; links returning from a browser pass false so they are never lost.
     */
    fun launch(
        operation: Operation,
        onError: ((UiText) -> Unit)? = null,
        dedupe: Boolean = true,
        block: suspend () -> Unit,
    ) {
        if (dedupe && operation in busy) return
        running.merge(operation, 1, Int::plus)
        busy = busy + operation
        scope.launch {
            try {
                gate.withLock {
                    if (operation.foreground) message = null
                    try {
                        block()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ApiFailure) {
                        val text = UiText.Raw(e.message)
                        if (operation.foreground) report(text)
                        onError?.invoke(text)
                        onApiFailure(e)
                    } catch (_: Exception) {
                        val text = UiText.of(R.string.error_request_failed)
                        if (operation.foreground) report(text)
                        onError?.invoke(text)
                    }
                }
            } finally {
                // Another queued request for the same operation may still be pending.
                if (running.merge(operation, -1, Int::plus) == 0) {
                    running.remove(operation)
                    busy = busy - operation
                }
            }
        }
    }

    /** For background polling: waits its turn but shows no progress. */
    suspend fun <T> serialized(block: suspend () -> T): T = gate.withLock { block() }
}

enum class Screen(
    /** Focused flows hide the tab bar. */
    val focused: Boolean = false,
) {
    Home,
    Assistant,
    Discover,
    Finalists(focused = true),
    Draw(focused = true),
    Winner(focused = true),
    Review(focused = true),
    Payment(focused = true),
    Order(focused = true),
    Saved,
    Day,
    ;

    /** Where Back leads. Order returns Home so a finished checkout is not re-entered. */
    val back
        get() =
            when (this) {
                Draw, Winner -> Finalists
                Review -> Winner
                Payment -> Review
                Order -> Home
                Finalists, Saved -> Discover
                else -> Home
            }
}

class Navigator(
    initial: Screen = Screen.Home,
    private val onChange: (Screen) -> Unit = {},
) {
    var screen by mutableStateOf(initial)
        private set

    fun go(target: Screen) {
        screen = target
        onChange(target)
    }
}
