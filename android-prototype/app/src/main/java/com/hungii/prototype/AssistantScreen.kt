package com.hungii.prototype

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@Composable
internal fun GlassOrb(
    modifier: Modifier,
    voice: VoiceState,
    thinking: Boolean,
    reduceMotion: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val animatedPhase by transition.animateFloat(
        0f,
        6.283f,
        infiniteRepeatable(tween(if (thinking) 4200 else 12000, easing = LinearEasing)),
        label = "orb phase",
    )
    val phase = if (reduceMotion) 0f else animatedPhase
    val amplitude =
        if (voice.listening) {
            voice.level
        } else if (thinking) {
            .45f
        } else {
            .12f
        }
    val description =
        stringResource(
            if (voice.listening) {
                R.string.orb_listening
            } else if (thinking) {
                R.string.orb_thinking
            } else {
                R.string.orb_idle
            },
        )
    Canvas(modifier.semantics { contentDescription = description }) {
        val c = center
        val r = size.minDimension * .34f
        drawCircle(
            Brush.radialGradient(
                listOf(Rose.copy(alpha = .15f + amplitude * .08f), SoftCrimson.copy(alpha = .06f), Color.Transparent),
                c,
                r * 1.48f,
            ),
            r * 1.48f,
            c,
        )
        drawCircle(Brush.radialGradient(listOf(Raised, AccentWash, Charcoal), Offset(c.x - r * .25f, c.y - r * .3f), r * 1.5f), r, c)
        val glow = Offset(c.x - r * .35f, c.y - r * .45f)
        drawCircle(Brush.radialGradient(listOf(Rose.copy(alpha = .22f), Color.Transparent), glow, r * .7f), r * .7f, glow)
        for (i in 0..2) {
            val wave = sin(phase + i * 1.8f) * r * .18f
            val ribbon =
                Path().apply {
                    moveTo(c.x - r * .86f, c.y + wave)
                    cubicTo(c.x - r * .3f, c.y - r * .85f, c.x + r * .35f, c.y + r * .72f, c.x + r * .86f, c.y - wave)
                    cubicTo(c.x + r * .36f, c.y + r * .95f, c.x - r * .34f, c.y - r * .5f, c.x - r * .86f, c.y + wave)
                }
            drawPath(
                ribbon,
                Brush.linearGradient(
                    listOf(Rose.copy(alpha = .06f), Accent.copy(alpha = .28f), White.copy(alpha = .25f), Accent.copy(alpha = .04f)),
                    Offset(c.x - r, c.y - r),
                    Offset(c.x + r, c.y + r),
                ),
            )
        }
        for (i in 0..4) {
            val a = phase + i * .67f
            val y = c.y + sin(a) * r * .2f
            val path =
                Path().apply {
                    moveTo(c.x - r * .88f, y)
                    cubicTo(c.x - r * .38f, c.y - r * (.9f + amplitude * .3f), c.x + r * .36f, c.y + r * .8f, c.x + r * .88f, y)
                }
            drawPath(
                path,
                Brush.linearGradient(
                    listOf(Rose.copy(alpha = .1f), Rose.copy(alpha = .7f), White.copy(alpha = .65f), Accent.copy(alpha = .25f)),
                    Offset(c.x - r, c.y - r),
                    Offset(c.x + r, c.y + r),
                ),
                style = Stroke(r * (.012f + i * .003f)),
            )
        }
        drawCircle(
            Brush.sweepGradient(
                listOf(
                    Rose.copy(alpha = .25f),
                    SoftCrimson,
                    White.copy(alpha = .85f),
                    Rose.copy(alpha = .16f),
                    Accent.copy(alpha = .4f),
                    Rose.copy(alpha = .25f),
                ),
                c,
            ),
            r,
            c,
            style = Stroke(r * .025f),
        )
        drawArc(
            White.copy(alpha = .6f),
            215f,
            65f,
            false,
            Offset(c.x - r * .92f, c.y - r * .92f),
            Size(r * 1.84f, r * 1.84f),
            style =
                Stroke(r * .018f),
        )
        drawCircle(White.copy(alpha = .55f), r * .035f, Offset(c.x - r * .54f, c.y - r * .66f))
    }
}

@Composable
internal fun AssistantScreen(
    model: HungiiModel,
    voice: VoiceState,
    onVoice: () -> Unit,
    reduceMotion: Boolean,
) {
    if (BuildConfig.LOCAL_DEMO) {
        ChatAssistant(model, voice, onVoice, reduceMotion)
    } else {
        CheckInAssistant(model, reduceMotion)
    }
}

/** The real build updates the plan from short text, on the device, without inference. */
@Composable
private fun CheckInAssistant(
    model: HungiiModel,
    reduceMotion: Boolean,
) {
    var checkIn by rememberSaveable { mutableStateOf("") }
    val day = model.day
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DisplayText(stringResource(R.string.checkin_title), 28)
        GlassOrb(Modifier.size(180.dp), VoiceState(), thinking = false, reduceMotion = reduceMotion)
        Text(stringResource(R.string.checkin_body), color = Muted, fontSize = 14.sp)
        FilledInput(checkIn, { checkIn = it.take(150) }, stringResource(R.string.checkin_placeholder), Modifier.fillMaxWidth())
        PrimaryButton(stringResource(R.string.checkin_update), Icons.Outlined.Check, enabled = checkIn.isNotBlank()) {
            day.checkIn(checkIn)
            checkIn = ""
        }
        day.checkInReceipt?.let { Text(it.asString(), color = White, fontSize = 14.sp, lineHeight = 21.sp) }
        if (day.canUndoCheckIn) OutlineButton(stringResource(R.string.checkin_undo), day::undoLastCheckIn)
        OutlineButton(stringResource(R.string.home_search), model.discovery::search)
        Text(stringResource(R.string.checkin_footer), color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun ChatAssistant(
    model: HungiiModel,
    voice: VoiceState,
    onVoice: () -> Unit,
    reduceMotion: Boolean,
) {
    val assistant = model.assistant
    val tracker = model.day.tracker
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(assistant.messages.size, assistant.loading) {
        if (assistant.messages.isNotEmpty()) list.animateScrollToItem(assistant.messages.size)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                DisplayText(stringResource(R.string.chat_title), 25)
                Text(stringResource(R.string.chat_subtitle), color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Outlined.AutoAwesome, null, tint = Accent, modifier = Modifier.size(22.dp))
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = list,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassOrb(Modifier.size(if (assistant.messages.isEmpty()) 220.dp else 126.dp), voice, assistant.loading, reduceMotion)
                    val status =
                        when {
                            voice.listening -> R.string.chat_listening
                            assistant.loading -> R.string.chat_connecting
                            else -> R.string.chat_ready
                        }
                    Text(stringResource(status), color = White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text(
                        pluralStringResource(
                            R.plurals.chat_summary,
                            tracker.opportunities,
                            rupees(model.moneyLeft.toDouble()),
                            tracker.caloriesLeft.label,
                            tracker.opportunities,
                        ),
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }
            if (assistant.messages.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(R.string.chat_prompt_meal, R.string.chat_prompt_allowance, R.string.chat_prompt_tracker).forEach { id ->
                            val prompt = stringResource(id)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Surface)
                                    .clickable { assistant.send(prompt) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(prompt, color = White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Icon(Icons.Outlined.ArrowOutward, null, tint = Muted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            items(assistant.messages) { message -> ChatBubble(message) { assistant.proposal = it } }
            if (assistant.loading) item { ThinkingDots() }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledInput(input, { input = it }, stringResource(R.string.chat_placeholder), Modifier.weight(1f))
            RoundAction(
                if (input.isBlank()) Icons.Outlined.Mic else Icons.Outlined.ArrowUpward,
                stringResource(if (input.isBlank()) R.string.chat_speak else R.string.chat_send),
                if (voice.listening) SoftCrimson else Accent,
                Charcoal,
                {
                    if (!assistant.loading) {
                        if (input.isBlank()) {
                            onVoice()
                        } else {
                            assistant.send(input)
                            input = ""
                        }
                    }
                },
                52,
            )
        }
        Text(
            stringResource(if (assistant.consent) R.string.chat_enabled else R.string.chat_optional),
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onReview: (AssistantProposal) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (message.fromUser) Raised else Surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(message.text.asString(), color = White, fontSize = 15.sp, lineHeight = 23.sp)
            message.proposals.forEach { proposal ->
                OutlinedButton(
                    onClick = { onReview(proposal) },
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Accent.copy(alpha = .4f)),
                ) {
                    Text(stringResource(R.string.chat_review_proposal, proposal.action.replace('_', ' ')), color = Accent, fontSize = 12.sp)
                }
            }
        }
    }
}
