package com.hungii.prototype

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
internal fun DiscoverScreen(
    model: HungiiModel,
    onDetails: (Meal) -> Unit,
) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val discovery = model.discovery
    val meal = discovery.candidates.firstOrNull()
    val filterCount = model.day.preferences.filterCount
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            DisplayText(stringResource(R.string.discover_title), 28, modifier = Modifier.weight(1f))
            IconButton(onClick = { model.go(Screen.Saved) }) {
                Icon(Icons.Outlined.Bookmarks, stringResource(R.string.discover_saved), tint = White)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.discover_money_left, rupees(model.moneyLeft.toDouble())), color = White, fontSize = 14.sp)
                Text(stringResource(R.string.discover_shortlisted, discovery.finalists.size), color = Muted, fontSize = 14.sp)
            }
            OutlinedButton(onClick = { filtersOpen = true }, modifier = Modifier.heightIn(min = 48.dp), border = BorderStroke(1.dp, Line)) {
                Icon(Icons.Outlined.Tune, null, tint = White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val label =
                    if (filterCount == 0) {
                        stringResource(R.string.discover_filters)
                    } else {
                        stringResource(R.string.discover_filters_count, filterCount)
                    }
                Text(label, color = White, fontSize = 14.sp)
            }
        }
        LinearProgressIndicator(
            progress = { discovery.finalists.size / 3f },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = Accent,
            trackColor = Line,
        )
        Spacer(Modifier.height(12.dp))
        val scrolling = Modifier.weight(1f).verticalScroll(rememberScrollState())
        when {
            model.actions.isBusy(Operation.Discovery) -> {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(24.dp))
                    DisplayText(stringResource(R.string.home_searching), 26)
                    Text(
                        stringResource(R.string.discover_loading_body),
                        color = Muted,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            discovery.error != null -> {
                MealEmptyState(
                    Icons.Outlined.CloudOff,
                    stringResource(R.string.discover_error_title),
                    discovery.error!!.asString(),
                    stringResource(R.string.action_try_again),
                    discovery::search,
                    scrolling,
                ) {
                    TextButton(
                        onClick = { model.accountOpen = true },
                    ) { Text(stringResource(R.string.delivery_check_connection), color = Muted) }
                }
            }

            model.day.tracker.opportunities == 0 -> {
                MealEmptyState(
                    Icons.Outlined.CheckCircle,
                    stringResource(R.string.discover_day_done_title),
                    stringResource(R.string.discover_day_done_body),
                    stringResource(R.string.discover_review_day),
                    { model.go(Screen.Day) },
                    scrolling,
                )
            }

            meal != null -> {
                MealProfile(
                    meal,
                    model,
                    Modifier.weight(1f),
                    onDetails,
                ) { keep -> if (keep) discovery.keep(meal) else discovery.pass(meal) }
                SwipeControls(model, meal)
            }

            else -> {
                DiscoverEmptyState(model, scrolling) { filtersOpen = true }
            }
        }
    }
    if (filtersOpen) MealFiltersSheet(model) { filtersOpen = false }
}

@Composable
private fun SwipeControls(
    model: HungiiModel,
    meal: Meal,
) {
    val discovery = model.discovery
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = discovery::undo, enabled = discovery.canUndo, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.discover_undo), tint = if (discovery.canUndo) White else Line)
        }
        OutlinedButton(
            onClick = { discovery.pass(meal) },
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            border = BorderStroke(1.dp, Line),
        ) { Text(stringResource(R.string.discover_pass), color = White, fontSize = 16.sp) }
        Button(
            onClick = { discovery.keep(meal) },
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Charcoal),
        ) { Text(stringResource(R.string.discover_keep), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
    Text(
        stringResource(R.string.discover_swipe_hint),
        color = Muted,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
    if (discovery.finalists.isNotEmpty()) {
        TextButton(onClick = discovery::showFinalists, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.discover_review_shortlist_count, discovery.finalists.size), color = Accent, fontSize = 14.sp)
        }
    }
}

/** Distinguishes connection, first use, filters, an exhausted batch and no results. */
@Composable
private fun DiscoverEmptyState(
    model: HungiiModel,
    modifier: Modifier,
    openFilters: () -> Unit,
) {
    val discovery = model.discovery
    val demo = BuildConfig.LOCAL_DEMO
    val needsConnection = !model.connection.ready
    val filtered = discovery.hasUnseenMeals
    val exhausted = discovery.meals.isNotEmpty() && !filtered
    val hasShortlist = discovery.finalists.isNotEmpty()
    val title =
        when {
            needsConnection -> if (demo) R.string.empty_connect_demo_title else R.string.empty_connect_title
            !discovery.attempted -> R.string.empty_first_title
            filtered -> R.string.empty_filtered_title
            exhausted -> R.string.empty_exhausted_title
            else -> R.string.empty_none_title
        }
    val description =
        when {
            needsConnection -> if (demo) R.string.empty_connect_demo_body else R.string.empty_connect_body
            !discovery.attempted -> R.string.empty_first_body
            filtered -> R.string.empty_filtered_body
            exhausted -> if (hasShortlist) R.string.empty_exhausted_shortlist_body else R.string.empty_exhausted_body
            else -> R.string.empty_none_body
        }
    val label =
        when {
            needsConnection -> if (demo) R.string.delivery_reconnect_demo else R.string.empty_connect_action
            filtered -> R.string.empty_adjust_filters
            exhausted && hasShortlist -> R.string.empty_review_shortlist
            exhausted -> R.string.empty_revisit_passed
            else -> R.string.empty_find_meals
        }
    MealEmptyState(
        Icons.Outlined.Restaurant,
        stringResource(title),
        stringResource(description),
        stringResource(label),
        {
            when {
                needsConnection -> model.accountOpen = true
                filtered -> openFilters()
                exhausted && hasShortlist -> discovery.showFinalists()
                exhausted -> discovery.revisitPassed()
                else -> discovery.search()
            }
        },
        modifier,
    ) {
        if (filtered && hasShortlist) {
            TextButton(onClick = discovery::showFinalists) { Text(stringResource(R.string.empty_review_shortlist), color = Muted) }
        }
        if (exhausted && discovery.passed.isNotEmpty() && hasShortlist) {
            TextButton(onClick = discovery::revisitPassed) { Text(stringResource(R.string.empty_revisit_passed), color = Muted) }
        }
        if (!needsConnection && discovery.attempted) {
            TextButton(onClick = { model.go(Screen.Home) }) { Text(stringResource(R.string.empty_change_craving), color = Muted) }
            discovery.restaurants.take(8).forEach { restaurant ->
                TextButton(onClick = { discovery.restaurantMeals(restaurant) }) { Text(restaurant.name, color = White, fontSize = 14.sp) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MealFiltersSheet(
    model: HungiiModel,
    onClose: () -> Unit,
) {
    val current = model.day.preferences
    var taste by remember { mutableStateOf(current.taste) }
    var highProtein by remember { mutableStateOf(current.highProtein) }
    var vegOnly by remember { mutableStateOf(current.vegOnly) }
    var budgetOnly by remember { mutableStateOf(current.budgetOnly) }
    var fast by remember { mutableStateOf(current.fast) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DisplayText(stringResource(R.string.filters_title), 28)
            Text(stringResource(R.string.filters_taste), color = Muted, fontSize = 16.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Tastes.filters.forEach { value -> SmallChip(tasteLabel(value), taste == value) { taste = value } }
            }
            FilterPreference(stringResource(R.string.filters_veg), stringResource(R.string.filters_veg_note), vegOnly) { vegOnly = it }
            FilterPreference(
                stringResource(R.string.filters_budget, rupees(BUDGET_MENU_PRICE.toDouble())),
                stringResource(R.string.filters_budget_note),
                budgetOnly,
            ) { budgetOnly = it }
            FilterPreference(stringResource(R.string.filters_protein), stringResource(R.string.filters_protein_note), highProtein) {
                highProtein = it
            }
            FilterPreference(stringResource(R.string.filters_fast), stringResource(R.string.filters_fast_note), fast) { fast = it }
            if (model.discovery.finalists.isNotEmpty()) {
                Text(
                    stringResource(R.string.filters_new_shortlist),
                    color = Muted,
                    fontSize = 14.sp,
                )
            }
            PrimaryButton(stringResource(R.string.filters_apply), Icons.AutoMirrored.Outlined.ArrowForward, enabled = !model.loading) {
                model.day.updatePreferences {
                    it.copy(taste = taste, highProtein = highProtein, vegOnly = vegOnly, budgetOnly = budgetOnly, fast = fast)
                }
                onClose()
                model.discovery.search()
            }
            TextButton(onClick = {
                taste = Tastes.ANY
                highProtein = false
                vegOnly = false
                budgetOnly = false
                fast = false
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.filters_reset), color = Muted, fontSize = 14.sp) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun tasteLabel(taste: String): String =
    stringResource(
        when (taste) {
            "spicy" -> R.string.taste_spicy
            "light" -> R.string.taste_light
            "cheesy" -> R.string.taste_cheesy
            "sweet" -> R.string.taste_sweet
            "bland" -> R.string.taste_bland
            "filling" -> R.string.taste_filling
            else -> R.string.taste_any
        },
    )

@Composable
private fun FilterPreference(
    title: String,
    note: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 16.sp)
            Text(note, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Switch(
            checked,
            onCheckedChange = null,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Charcoal,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Muted,
                    uncheckedTrackColor = Raised,
                    uncheckedBorderColor = Line,
                ),
        )
    }
}

@Composable
private fun MealProfile(
    meal: Meal,
    model: HungiiModel,
    modifier: Modifier,
    onDetails: (Meal) -> Unit,
    onSwipe: (Boolean) -> Unit,
) {
    var drag by remember(meal.id) { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val restingDrag by animateFloatAsState(drag, spring(dampingRatio = .75f, stiffness = 500f), label = "card drag")
    val saved = model.saved.isSaved(meal)
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = restingDrag
                rotationZ = restingDrag / 35f
            }.clip(RoundedCornerShape(24.dp))
            .background(Surface)
            .pointerInput(meal.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(drag) > 100.dp.toPx()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSwipe(drag > 0)
                        }
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { change, amount ->
                    change.consume()
                    drag += amount
                }
            },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(180.dp)) {
                MealImage(meal, Modifier.fillMaxSize())
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Badge(meal.badge(), Charcoal, Accent)
                    IconButton(
                        onClick = { model.saved.toggle(meal) },
                        modifier = Modifier.size(48.dp).background(Charcoal, CircleShape).border(1.dp, Line, CircleShape),
                    ) {
                        Icon(
                            if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            stringResource(R.string.meal_save, meal.name),
                            tint = if (saved) Accent else White,
                        )
                    }
                }
                if (abs(drag) > 30) {
                    Badge(
                        stringResource(if (drag > 0) R.string.discover_keep else R.string.discover_pass),
                        if (drag > 0) Charcoal else White,
                        if (drag > 0) Accent else Charcoal,
                        Modifier.align(Alignment.Center),
                    )
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(meal.restaurant, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                DisplayText(meal.name, 26)
                Text(
                    stringResource(R.string.meal_diet_eta, meal.dietLabel(), meal.etaLabel()),
                    color = Muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    DisplayText(meal.priceLabel(), 28)
                    Text(
                        stringResource(R.string.meal_menu_price_suffix),
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                AllowanceContext(meal, model)
                Text(
                    stringResource(if (meal.nutrition != null) R.string.meal_nutrition_estimated else R.string.meal_nutrition_unavailable),
                    color = Muted,
                    fontSize = 12.sp,
                )
                MacroStats(meal.nutrition)
                TextButton(onClick = { onDetails(meal) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.meal_details), color = Accent, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** How a meal's known cost compares with today's remaining allowance. */
@Composable
internal fun AllowanceContext(
    meal: Meal,
    model: HungiiModel,
) {
    val impact = allowanceImpact(meal.estimatedPayable, meal.itemPrice, model.moneyLeft) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val cost =
            stringResource(
                if (impact.estimatedBasket) R.string.allowance_estimated_basket else R.string.allowance_before_fees,
                rupees(impact.total),
            )
        val share = impact.percentage?.let { stringResource(R.string.allowance_share, it) } ?: ""
        Text(cost + share, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        val remaining =
            when {
                impact.remaining < 0 -> stringResource(R.string.allowance_over, rupees(-impact.remaining))
                impact.estimatedBasket -> stringResource(R.string.allowance_leaves_about, rupees(impact.remaining))
                else -> stringResource(R.string.allowance_leaves, rupees(impact.remaining))
            }
        val note =
            stringResource(if (impact.estimatedBasket) R.string.allowance_checked_at_checkout else R.string.allowance_before_delivery)
        Text(remaining + note, color = if (impact.remaining < 0) SoftCrimson else Muted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MealDetailsSheet(
    meal: Meal,
    model: HungiiModel,
    onClose: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface,
    ) {
        Column(
            Modifier
                .heightIn(max = 650.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Eyebrow(stringResource(R.string.details_eyebrow), Accent)
            DisplayText(meal.name, 28)
            MacroStats(meal.nutrition)
            AllowanceContext(meal, model)
            Text(meal.benefit(), color = White, fontSize = 15.sp)
            Text(meal.compromise(), color = SoftCrimson, fontSize = 15.sp)
            Text(
                stringResource(R.string.details_menu_price, meal.priceLabel()) + " " +
                    stringResource(
                        if (meal.nutrition != null) {
                            R.string.details_nutrition_estimated
                        } else {
                            R.string.details_nutrition_unpublished
                        },
                    ),
                color = Muted,
                lineHeight = 23.sp,
            )
            PrimaryButton(stringResource(R.string.details_done), Icons.Outlined.Check, onClick = onClose)
        }
    }
}

@Composable
internal fun SavedScreen(model: HungiiModel) {
    val saved = model.saved
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FocusHeader(stringResource(R.string.saved_header)) { model.go(Screen.Discover) }
        if (saved.meals.isEmpty()) {
            MealEmptyState(
                Icons.Outlined.Bookmarks,
                stringResource(R.string.saved_empty_title),
                stringResource(R.string.saved_empty_body),
                stringResource(R.string.saved_empty_action),
                { model.go(Screen.Discover) },
            )
        } else {
            DisplayText(stringResource(R.string.saved_title), 28)
            Text(stringResource(R.string.saved_body), color = Muted, fontSize = 16.sp, lineHeight = 24.sp)
            saved.meals.toList().forEach { meal ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 144.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Surface)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MealImage(meal, Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(meal.name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
                        Text(meal.restaurant, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    model.day.updatePreferences { it.copy(query = meal.name) }
                                    model.discovery.search()
                                },
                                modifier =
                                    Modifier.weight(
                                        1f,
                                    ),
                            ) { Text(stringResource(R.string.saved_find_again), color = Accent, fontSize = 14.sp) }
                            IconButton(onClick = { saved.toggle(meal) }) {
                                Icon(Icons.Outlined.Bookmark, stringResource(R.string.saved_unsave, meal.name), tint = Muted)
                            }
                        }
                    }
                }
            }
        }
        if (saved.consent) {
            TextButton(onClick = model::forgetSaved) { Text(stringResource(R.string.saved_forget), color = SoftCrimson, fontSize = 14.sp) }
        }
        Spacer(Modifier.height(16.dp))
    }
}
