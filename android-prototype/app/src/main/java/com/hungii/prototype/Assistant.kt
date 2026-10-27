package com.hungii.prototype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(
    val text: UiText,
    val fromUser: Boolean = false,
    val proposals: List<AssistantProposal> = emptyList(),
)

/**
 * The Simulator's opt-in cloud Assistant. Messages leave the device only after
 * consent, and proposed changes wait for the user's confirmation.
 */
class Assistant(
    private val api: Backend,
    private val scope: CoroutineScope,
    private val context: () -> JSONObject,
    private val onOpen: () -> Unit,
    private val onTrace: (JSONObject) -> Unit,
) {
    val messages = mutableStateListOf<ChatMessage>()
    var loading by mutableStateOf(false)
        private set
    var consent by mutableStateOf(false)
        private set
    var consentPending by mutableStateOf(false)
        private set
    private var pendingText = ""

    /** A proposal the user is reviewing. */
    var proposal by mutableStateOf<AssistantProposal?>(null)

    fun send(text: String) {
        val message = text.trim().take(MAX_MESSAGE)
        if (message.isBlank() || loading) return
        onOpen()
        if (!consent) {
            pendingText = message
            consentPending = true
            return
        }
        messages.add(ChatMessage(UiText.Raw(message), fromUser = true))
        loading = true
        scope.launch {
            try {
                val args =
                    JSONObject()
                        .put("message", message)
                        .put("context", context())
                        .put("history", history())
                        .put("consent", true)
                val result = api.action("agent_chat", args)
                val proposals =
                    result.optJSONArray("actions").objects().map {
                        AssistantProposal(it.getString("action"), it.optString("value"))
                    }
                messages.add(ChatMessage(UiText.Raw(result.getString("reply")), proposals = proposals))
                onTrace(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiFailure) {
                messages.add(ChatMessage(UiText.Raw(e.message)))
            } catch (_: Exception) {
                messages.add(ChatMessage(UiText.of(R.string.assistant_unreachable)))
            } finally {
                loading = false
            }
        }
    }

    /** The last four turns before this message; local error notices are not sent. */
    private fun history(): JSONArray {
        val history = JSONArray()
        messages.dropLast(1).takeLast(4).forEach { m ->
            val text = (m.text as? UiText.Raw)?.value ?: return@forEach
            history.put(JSONObject().put("role", if (m.fromUser) "user" else "assistant").put("text", text.take(MAX_MESSAGE)))
        }
        return history
    }

    fun acceptConsent() {
        consent = true
        consentPending = false
        val text = pendingText
        pendingText = ""
        send(text)
    }

    fun declineConsent() {
        consentPending = false
    }

    private companion object {
        const val MAX_MESSAGE = 1_500
    }
}
