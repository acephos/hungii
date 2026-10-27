package com.hungii.prototype

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class VoiceState(
    val listening: Boolean = false,
    val level: Float = 0f,
)

class MainActivity : ComponentActivity() {
    internal val model: HungiiModel by viewModels()
    internal val voice = mutableStateOf(VoiceState())
    private var recognizer: SpeechRecognizer? = null
    private var onWords: (String) -> Unit = {}
    private val microphonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            if (allowed) listen() else Toast.makeText(this, R.string.voice_permission_denied, Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(9, 9, 12)),
        )
        // A recreated activity still holds the original intent; its one-time code was already used.
        if (savedInstanceState == null) consumeLink(intent)
        setContent {
            HungiiTheme {
                onWords = { model.assistant.send(it) }
                HungiiApp(model, voice.value, ::requestVoice)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLink(intent)
    }

    override fun onResume() {
        super.onResume()
        model.onForeground()
    }

    private fun consumeLink(intent: Intent) {
        val link = intent.data ?: return
        intent.data = null
        model.handleCallback(link)
    }

    internal fun requestVoice() {
        if (voice.value.listening) {
            recognizer?.stopListening()
            voice.value = VoiceState()
            return
        }
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listen()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun listen() {
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer =
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this).apply {
                setRecognitionListener(
                    object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            voice.value = VoiceState(true)
                        }

                        override fun onBeginningOfSpeech() {
                            voice.value = VoiceState(true)
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            voice.value = VoiceState(true, ((rmsdB + 2) / 12).coerceIn(0f, 1f))
                        }

                        override fun onEndOfSpeech() {
                            voice.value = VoiceState()
                        }

                        override fun onResults(results: Bundle?) {
                            voice.value = VoiceState()
                            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onWords)
                        }

                        override fun onPartialResults(partialResults: Bundle?) = Unit

                        override fun onError(error: Int) {
                            voice.value = VoiceState()
                            Toast.makeText(this@MainActivity, R.string.voice_failed, Toast.LENGTH_SHORT).show()
                        }

                        override fun onBufferReceived(buffer: ByteArray?) = Unit

                        override fun onEvent(
                            eventType: Int,
                            params: Bundle?,
                        ) = Unit
                    },
                )
                startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    },
                )
            }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        super.onDestroy()
    }
}

@Composable
internal fun HungiiApp(
    model: HungiiModel,
    voice: VoiceState,
    onVoice: () -> Unit,
) {
    var goalsOpen by rememberSaveable { mutableStateOf(false) }
    var goalsShowTotals by rememberSaveable { mutableStateOf(false) }
    var detailMeal by remember { mutableStateOf<Meal?>(null) }
    val haptic = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()
    val screen = model.navigator.screen
    if (model.initializing || model.accountLoading) {
        RestoringScreen()
        return
    }
    if (!BuildConfig.LOCAL_DEMO && !model.signedIn && !model.offlineMode) {
        AccountEntry(model)
        return
    }
    if (!BuildConfig.LOCAL_DEMO && model.signedIn && model.cloud.setupPending) {
        CloudEntry(model)
        return
    }
    BackHandler(screen != Screen.Home) { model.goBack() }
    LaunchedEffect(model.discovery.pickedIndex) {
        if (model.discovery.pickedIndex != null && model.navigator.screen == Screen.Draw) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(if (reduceMotion) 40 else 700)
            if (model.navigator.screen == Screen.Draw) model.go(Screen.Winner)
        }
    }
    LaunchedEffect(model.dayEditorRequested) {
        if (model.dayEditorRequested) {
            goalsShowTotals = true
            goalsOpen = true
            model.consumeDayEditorRequest()
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val notice = model.notice?.asString()
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            model.consumeNotice()
        }
    }
    val openGoals = {
        goalsShowTotals = false
        goalsOpen = true
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Charcoal,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (!screen.focused) HungiiTabs(model) },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding()) {
            if (BuildConfig.LOCAL_DEMO) {
                Text(
                    stringResource(R.string.simulator_banner),
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().background(Surface).padding(8.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Box(Modifier.weight(1f).background(Brush.verticalGradient(listOf(AccentWash.copy(alpha = .24f), Charcoal), endY = 1100f))) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val enter = if (reduceMotion) 0 else 220
                        val exit = if (reduceMotion) 0 else 120
                        fadeIn(tween(enter)) + slideInVertically(tween(enter)) { it / 30 } togetherWith fadeOut(tween(exit))
                    },
                    label = "screen",
                ) { target ->
                    when (target) {
                        Screen.Home -> HomeScreen(model, openGoals)
                        Screen.Assistant -> AssistantScreen(model, voice, onVoice, reduceMotion)
                        Screen.Discover -> DiscoverScreen(model) { detailMeal = it }
                        Screen.Finalists -> FinalistsScreen(model)
                        Screen.Draw -> DrawScreen(model, reduceMotion)
                        Screen.Winner -> WinnerScreen(model) { detailMeal = it }
                        Screen.Review -> CheckoutScreen(model)
                        Screen.Payment -> PaymentScreen(model)
                        Screen.Order -> OrderScreen(model)
                        Screen.Saved -> SavedScreen(model)
                        Screen.Day -> DayScreen(model, openGoals)
                    }
                }
            }
        }
    }
    AppDialogs(model)
    if (goalsOpen) GoalsDialog(model, showTotals = goalsShowTotals) { goalsOpen = false }
    detailMeal?.let { meal -> MealDetailsSheet(meal, model) { detailMeal = null } }
}

@Composable
private fun RestoringScreen() {
    Box(Modifier.fillMaxSize().background(Charcoal), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ThinkingDots()
            Text(stringResource(R.string.restoring_account), color = White)
        }
    }
}

/** Consent and confirmation dialogs that can open from any screen. */
@Composable
private fun AppDialogs(model: HungiiModel) {
    val assistant = model.assistant
    if (assistant.consentPending) {
        AlertDialog(
            onDismissRequest = assistant::declineConsent,
            containerColor = Surface,
            title = { Text(stringResource(R.string.assistant_consent_title)) },
            text = { Text(stringResource(R.string.assistant_consent_body)) },
            confirmButton = { TextButton(onClick = assistant::acceptConsent) { Text(stringResource(R.string.assistant_consent_accept)) } },
            dismissButton = {
                TextButton(
                    onClick = assistant::declineConsent,
                ) { Text(stringResource(R.string.assistant_consent_decline)) }
            },
        )
    }
    assistant.proposal?.let { proposal ->
        AlertDialog(
            onDismissRequest = { assistant.proposal = null },
            containerColor = Surface,
            title = { Text(stringResource(R.string.assistant_apply_title)) },
            text = { Text(proposal.action.replace('_', ' ') + if (proposal.value.isBlank()) "" else " → ${proposal.value}") },
            confirmButton = { TextButton(onClick = model::applyAssistantProposal) { Text(stringResource(R.string.assistant_apply)) } },
            dismissButton = { TextButton(onClick = { assistant.proposal = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    val checkout = model.checkout
    if (checkout.replaceCartPending) {
        val keep = {
            checkout.replaceCartPending = false
            model.goBack()
        }
        AlertDialog(
            onDismissRequest = keep,
            containerColor = Surface,
            title = { Text(stringResource(R.string.cart_replace_title)) },
            text = { Text(stringResource(R.string.cart_replace_body)) },
            confirmButton = {
                TextButton(onClick = {
                    checkout.replaceCartPending = false
                    checkout.review(replace = true)
                }) { Text(stringResource(R.string.cart_replace_confirm)) }
            },
            dismissButton = { TextButton(onClick = keep) { Text(stringResource(R.string.cart_replace_keep)) } },
        )
    }
    if (model.accountOpen) AccountDialog(model) { model.accountOpen = false }
    model.saved.pendingSave?.let { meal ->
        AlertDialog(
            onDismissRequest = { model.saved.pendingSave = null },
            containerColor = Surface,
            title = { Text(stringResource(R.string.save_consent_title), color = White) },
            text = { Text(stringResource(R.string.save_consent_body, meal.name, model.noticeVersion), color = Muted) },
            confirmButton = {
                TextButton(onClick = model.saved::acceptPending) { Text(stringResource(R.string.save_consent_accept), color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { model.saved.pendingSave = null }) { Text(stringResource(R.string.action_cancel), color = Muted) }
            },
        )
    }
    model.privacyAction?.let { action -> PrivacyDialog(model, action) }
}
