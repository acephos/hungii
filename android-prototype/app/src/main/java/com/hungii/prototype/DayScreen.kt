package com.hungii.prototype

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun BrandHeader(onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HungiiWordmark(height = 48.dp)
        IconButton(
            onClick = onEdit,
        ) { Icon(Icons.Outlined.Tune, stringResource(R.string.day_edit), tint = White, modifier = Modifier.size(23.dp)) }
    }
}

@Composable
internal fun DayScreen(
    model: HungiiModel,
    onEdit: () -> Unit,
) {
    val tracker = model.day.tracker
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BrandHeader(onEdit)
        DisplayText(stringResource(R.string.day_title), 30)
        CalorieRing(tracker)
        listOf(
            Triple(R.string.day_protein, tracker.intake.protein, tracker.proteinGoal),
            Triple(R.string.day_carbs, tracker.intake.carbs, tracker.carbGoal),
            Triple(R.string.day_fat, tracker.intake.fat, tracker.fatGoal),
        ).forEachIndexed { i, (label, eaten, goal) ->
            val color = listOf(Accent, Rose, SoftCrimson)[i]
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Surface)
                    .padding(17.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(label), color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.day_macro_eaten, eaten.label, goal),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        color = White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                LinearProgressIndicator(
                    progress = { (eaten.mid / goal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = color,
                    trackColor = Line,
                )
            }
        }
        MetricTile(
            stringResource(R.string.day_allowance_left),
            rupees(model.moneyLeft.toDouble()),
            stringResource(R.string.day_allowance_note, rupees(tracker.spent.toDouble()), rupees(tracker.allowance.toDouble())),
            Accent,
            Modifier.fillMaxWidth(),
        )
        OutlineButton(stringResource(R.string.day_edit_button), onEdit)
        Text(
            stringResource(R.string.day_footer),
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(bottom = 20.dp),
        )
    }
}

@Composable
private fun CalorieRing(tracker: TrackerState) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Raised, Surface)))
            .border(1.dp, Line.copy(alpha = .6f), RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val consumed by animateFloatAsState(
            (tracker.intake.calories.mid / tracker.calorieGoal).coerceIn(0f, 1f),
            tween(650),
            label = "calorie progress",
        )
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val diameter = minOf(maxWidth, 192.dp)
            Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val width = 8.dp.toPx()
                    val inset = width / 2
                    drawCircle(Line, radius = (size.minDimension - width) / 2, style = Stroke(width))
                    if (consumed > 0f) {
                        drawArc(
                            Brush.sweepGradient(listOf(Crimson, Accent, Rose, Crimson)),
                            -90f,
                            consumed * 360,
                            false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - width, size.height - width),
                            style = Stroke(width, cap = StrokeCap.Round),
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FittedMetric(tracker.caloriesLeft.label, 38, Accent)
                    Text(
                        stringResource(R.string.day_kcal_left),
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.day_kcal_consumed, tracker.intake.calories.label),
                color = White,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                pluralStringResource(R.plurals.day_target_summary, tracker.opportunities, tracker.calorieGoal, tracker.opportunities),
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A text field for one whole-number tracker value. */
private class NumberField(
    val label: Int,
    initial: Int,
) {
    val initialText = initial.toString()
    var text by mutableStateOf(initialText)
    val value get() = text.toIntOrNull()
    val changed get() = text != initialText
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalsDialog(
    model: HungiiModel,
    showTotals: Boolean,
    onClose: () -> Unit,
) {
    val tracker = model.day.tracker
    var name by remember { mutableStateOf(model.day.preferences.displayName) }
    val calories = remember { NumberField(R.string.goals_calories, tracker.calorieGoal) }
    val protein = remember { NumberField(R.string.goals_protein, tracker.proteinGoal) }
    val carbs = remember { NumberField(R.string.goals_carbs, tracker.carbGoal) }
    val fat = remember { NumberField(R.string.goals_fat, tracker.fatGoal) }
    val allowance = remember { NumberField(R.string.goals_allowance, tracker.allowance) }
    val meals = remember { NumberField(R.string.goals_meals, tracker.opportunities) }
    val eatenCalories = remember { NumberField(R.string.goals_eaten_calories, tracker.intake.calories.low) }
    val eatenProtein = remember { NumberField(R.string.goals_eaten_protein, tracker.intake.protein.low) }
    val eatenCarbs = remember { NumberField(R.string.goals_eaten_carbs, tracker.intake.carbs.low) }
    val eatenFat = remember { NumberField(R.string.goals_eaten_fat, tracker.intake.fat.low) }
    val spent = remember { NumberField(R.string.goals_spent, tracker.spent) }
    var totalsOpen by remember { mutableStateOf(showTotals) }
    var nameOpen by remember { mutableStateOf(name.isNotBlank()) }
    val goals = listOf(calories, protein, carbs, fat, allowance, meals)
    val totals = listOf(eatenCalories, eatenProtein, eatenCarbs, eatenFat, spent)
    val valid =
        listOf(calories, protein, carbs, fat, allowance).all { (it.value ?: 0) > 0 } &&
            (meals.value ?: -1) in 0..8 &&
            totals.all { (it.value ?: -1) >= 0 }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).imePadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DisplayText(stringResource(R.string.goals_title), 28)
                Text(stringResource(R.string.goals_subtitle), color = Muted, fontSize = 16.sp)
                if (nameOpen) {
                    FilledInput(
                        name,
                        { name = it.take(60) },
                        stringResource(R.string.goals_name),
                        Modifier.fillMaxWidth(),
                        showLabel = true,
                    )
                } else {
                    TextButton(
                        onClick = { nameOpen = true },
                    ) { Text(stringResource(R.string.goals_add_name), color = Muted, fontSize = 14.sp) }
                }
                goals.forEach { NumberInput(it) }
                TextButton(onClick = { totalsOpen = !totalsOpen }) {
                    Text(
                        stringResource(if (totalsOpen) R.string.goals_hide_totals else R.string.goals_edit_totals),
                        color = White,
                        fontSize = 16.sp,
                    )
                }
                if (totalsOpen) {
                    Text(stringResource(R.string.goals_totals_hint), color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                    totals.forEach { NumberInput(it) }
                }
                if (!valid) Text(stringResource(R.string.goals_invalid), color = SoftCrimson, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(8.dp))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).navigationBarsPadding()) {
                PrimaryButton(stringResource(R.string.goals_save), Icons.Outlined.Check, enabled = valid) {
                    // Unchanged totals keep their logged ranges; edited ones become exact.
                    fun span(
                        field: NumberField,
                        current: Span,
                    ) = if (field.changed) Span.exact(field.value!!) else current
                    val intake = tracker.intake
                    val updated =
                        tracker.copy(
                            calorieGoal = calories.value!!,
                            proteinGoal = protein.value!!,
                            carbGoal = carbs.value!!,
                            fatGoal = fat.value!!,
                            allowance = allowance.value!!,
                            opportunities = meals.value!!,
                            spent = spent.value!!,
                            intake =
                                Nutrition(
                                    span(eatenCalories, intake.calories),
                                    span(eatenProtein, intake.protein),
                                    span(eatenCarbs, intake.carbs),
                                    span(eatenFat, intake.fat),
                                ),
                        )
                    model.day.saveDay(updated, name)
                    onClose()
                }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_cancel), color = Muted, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun NumberInput(field: NumberField) {
    FilledInput(
        field.text,
        { value -> field.text = value.filter { it.isDigit() } },
        stringResource(field.label),
        Modifier.fillMaxWidth(),
        showLabel = true,
        keyboardType = KeyboardType.Number,
    )
}
