package com.hungii.prototype

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun FinalistsScreen(model: HungiiModel) {
    val discovery = model.discovery
    val count = discovery.finalists.size
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FocusHeader(stringResource(R.string.finalists_header), model::goBack)
        DisplayText(stringResource(if (count == 3) R.string.finalists_title_full else R.string.finalists_title), 30)
        Text(stringResource(R.string.finalists_body), color = Muted, lineHeight = 23.sp)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            discovery.finalists.toList().forEachIndexed { i, meal ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 144.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Surface),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MealImage(meal, Modifier.size(88.dp).padding(start = 12.dp).clip(RoundedCornerShape(16.dp)))
                    Column(Modifier.weight(1f).padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Eyebrow(stringResource(R.string.finalists_item, i + 1), Accent)
                        Text(meal.name, color = White, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.finalists_price_eta, meal.priceLabel(), meal.etaLabel()),
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                    IconButton(onClick = { discovery.remove(meal) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            stringResource(R.string.finalists_remove, meal.name),
                            tint = Muted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        if (count == 3) {
            PrimaryButton(stringResource(R.string.finalists_shuffle), Icons.Outlined.Shuffle, onClick = discovery::startDraw)
        } else {
            PrimaryButton(
                pluralStringResource(R.plurals.finalists_keep_more, 3 - count, 3 - count),
                Icons.Outlined.Add,
            ) { model.go(Screen.Discover) }
        }
        Text(
            stringResource(R.string.finalists_footer),
            color = Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )
    }
}

@Composable
internal fun DrawScreen(
    model: HungiiModel,
    reduceMotion: Boolean,
) {
    val discovery = model.discovery
    var backsUp by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(discovery.shuffling) {
        backsUp = true
        if (!discovery.shuffling) return@LaunchedEffect
        progress.snapTo(0f)
        if (!reduceMotion) {
            delay(450)
            progress.animateTo(1f, tween(1800, easing = LinearEasing))
        } else {
            progress.snapTo(1f)
        }
        discovery.finishShuffle()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        FocusHeader(stringResource(R.string.draw_header), model::goBack)
        Spacer(Modifier.height(28.dp))
        Eyebrow(stringResource(R.string.draw_eyebrow), Accent)
        val title =
            when {
                discovery.shuffling -> R.string.draw_title_shuffling
                discovery.pickedIndex != null -> R.string.draw_title_picked
                else -> R.string.draw_title_pick
            }
        DisplayText(stringResource(title), 36, maxLines = 2, minLines = 2)
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(if (discovery.shuffling) R.string.draw_body_shuffling else R.string.draw_body_pick),
            color = Muted,
            lineHeight = 23.sp,
            minLines = 2,
            maxLines = 2,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val cardWidth = (maxWidth - 40.dp) / 3
            val travel = cardWidth + 12.dp
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                discovery.drawOrder.forEachIndexed { i, meal ->
                    DrawCard(model, meal, i, if (reduceMotion) 1f else progress.value, travel.value, cardWidth.value, backsUp, reduceMotion)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (discovery.shuffling) Icons.Outlined.Shuffle else Icons.Outlined.TouchApp,
                null,
                tint = Accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(stringResource(if (discovery.shuffling) R.string.draw_mixing else R.string.draw_hint), color = White, fontSize = 13.sp)
        }
        TextButton(onClick = {
            discovery.stopShuffle()
            model.go(Screen.Finalists)
        }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.draw_view_finalists), color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DrawCard(
    model: HungiiModel,
    meal: Meal,
    index: Int,
    progress: Float,
    travel: Float,
    width: Float,
    backsUp: Boolean,
    reduceMotion: Boolean,
) {
    val discovery = model.discovery
    val pose = shufflePose(progress, index, travel)
    val picked = discovery.pickedIndex == index
    val duration = if (reduceMotion) 0 else 320
    val lift by animateFloatAsState(if (picked) -12f else 0f, tween(duration), label = "picked lift")
    val opacity by animateFloatAsState(if (discovery.pickedIndex != null && !picked) .25f else 1f, tween(duration), label = "picked fade")
    val turned by animateFloatAsState(if (picked || !backsUp) 0f else 180f, tween(if (reduceMotion) 0 else 450), label = "flip")
    val description = if (picked) stringResource(R.string.draw_revealed, meal.name) else stringResource(R.string.draw_card, index + 1)
    Box(
        Modifier
            .width(width.dp)
            .height((width * 1.62f).dp)
            .graphicsLayer {
                translationX = pose.x.dp.toPx()
                translationY = (pose.y + lift).dp.toPx()
                rotationZ = pose.tilt
                alpha = opacity
                if (Build.VERSION.SDK_INT >= 31) {
                    renderEffect = if (pose.blur > .05f) BlurEffect(pose.blur * density, pose.blur * density, TileMode.Decal) else null
                }
                rotationY = turned
                cameraDistance = 16 * density
            }.clip(RoundedCornerShape(18.dp))
            .background(if (turned < 90) Surface else Accent)
            .border(1.dp, if (picked) Accent else Accent.copy(alpha = .5f), RoundedCornerShape(18.dp))
            .clickable(enabled = discovery.canPick && !discovery.shuffling && discovery.pickedIndex == null) { discovery.pick(index) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (turned < 90) {
            Column(Modifier.fillMaxSize()) {
                MealImage(meal, Modifier.weight(1f).fillMaxWidth())
                Text(
                    meal.name,
                    color = White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(10.dp),
                    maxLines = 3,
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .padding(10.dp)
                    .border(1.dp, Charcoal.copy(alpha = .25f), RoundedCornerShape(10.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("h.", fontSize = 54.sp, fontWeight = FontWeight.Black, letterSpacing = (-3).sp, color = Charcoal)
                Spacer(Modifier.height(15.dp))
                Text(
                    stringResource(R.string.draw_card_back),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    color = Charcoal,
                    letterSpacing = .5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun WinnerScreen(
    model: HungiiModel,
    onDetails: (Meal) -> Unit,
) {
    val meal = model.discovery.winner ?: return
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FocusHeader(stringResource(R.string.winner_header), model::goBack)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                DisplayText(stringResource(R.string.winner_title), 32)
            }
            Box(Modifier.fillMaxWidth().height(245.dp).clip(RoundedCornerShape(26.dp))) {
                MealImage(meal, Modifier.fillMaxSize())
                Badge(meal.badge(), Charcoal, Accent, Modifier.align(Alignment.TopStart).padding(15.dp))
            }
            DisplayText(meal.name, 28)
            Text(stringResource(R.string.winner_price, meal.priceLabel()), color = White, fontSize = 18.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(meal.restaurant, color = Muted, fontSize = 12.sp)
                Text(meal.etaLabel(), color = Muted, fontSize = 12.sp)
            }
            MacroStats(meal.nutrition)
            AllowanceContext(meal, model)
            Text(
                stringResource(if (meal.nutrition != null) R.string.winner_nutrition_estimated else R.string.winner_nutrition_unknown),
                color = Muted,
                fontSize = 12.sp,
            )
            TradeLine(Icons.Outlined.Add, Accent, meal.benefit())
            TradeLine(Icons.Outlined.Remove, SoftCrimson, meal.compromise(), maxLines = 3)
            TextButton(onClick = { onDetails(meal) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.winner_details), color = Muted, fontSize = 12.sp)
            }
            Text(
                stringResource(R.string.winner_footer),
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            )
        }
        Box(Modifier.fillMaxWidth().background(Charcoal).padding(horizontal = 22.dp, vertical = 12.dp)) {
            PrimaryButton(
                stringResource(if (BuildConfig.LOCAL_DEMO) R.string.winner_add_demo else R.string.winner_review),
                Icons.AutoMirrored.Outlined.ArrowForward,
            ) { model.checkout.review() }
        }
    }
}
