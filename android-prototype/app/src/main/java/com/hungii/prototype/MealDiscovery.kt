package com.hungii.prototype

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
internal fun DiscoverScreen(model: HungiiModel, onDetails: (Meal) -> Unit) {
    var filtersOpen by remember { mutableStateOf(false) }
    val meal = model.pool.firstOrNull()
    val filterCount = listOf(model.taste != "any", model.highProtein, model.vegOnly, model.budgetOnly, model.fast).count { it }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            DisplayText("Find your three", 28, modifier=Modifier.weight(1f))
            IconButton(onClick = { model.screen = Screen.Saved }) {
                Icon(Icons.Outlined.Bookmarks, "Saved meals", tint = White)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("₹${model.moneyLeft} left today", color = White, fontSize = 14.sp)
                Text("${model.finalists.size} of 3 shortlisted", color = Muted, fontSize = 14.sp)
            }
            OutlinedButton(onClick = { filtersOpen = true }, modifier = Modifier.heightIn(min = 48.dp), border = BorderStroke(1.dp, Line)) {
                Icon(Icons.Outlined.Tune, null, tint = White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (filterCount == 0) "Filters" else "Filters · $filterCount", color = White, fontSize = 14.sp)
            }
        }
        LinearProgressIndicator(progress={model.finalists.size/3f},modifier=Modifier.fillMaxWidth().height(4.dp),color=Lime,trackColor=Line)
        Spacer(Modifier.height(12.dp))
        when {
            model.loading -> Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Lime, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(24.dp))
                DisplayText("Finding your meals…", 26)
                Text("Checking a fresh batch for your day.", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
            }
            model.discoveryError != null -> MealEmptyState(
                Icons.Outlined.CloudOff, "Couldn't load your meals", model.discoveryError!!,
                "Try again", { model.search() }, Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                TextButton(onClick = { model.accountOpen = true }) { Text("Check connection", color = Muted) }
            }
            model.opportunities == 0 -> MealEmptyState(
                Icons.Outlined.CheckCircle, "Your day is complete", "No meals left in your plan. Review your day before choosing another.",
                "Review my day", { model.screen = Screen.Day }, Modifier.weight(1f).verticalScroll(rememberScrollState()),
            )
            meal != null -> {
                MealProfile(meal, model, Modifier.weight(1f), onDetails) { keep ->
                    if (keep) model.like(meal) else model.pass(meal)
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = model::undoSwipe, enabled = model.lastPassed != null || model.finalists.isNotEmpty(), modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, "Undo last choice", tint = if (model.lastPassed != null || model.finalists.isNotEmpty()) White else Line)
                    }
                    OutlinedButton(onClick = { model.pass(meal) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), border = BorderStroke(1.dp, Line)) {
                        Text("Pass", color = White, fontSize = 16.sp)
                    }
                    Button(onClick = { model.like(meal) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Charcoal)) {
                        Text("Keep", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Swipe left to pass · right to keep", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                if (model.finalists.isNotEmpty()) TextButton(onClick = model::showFinalists, modifier = Modifier.fillMaxWidth()) {
                    Text("Review shortlist (${model.finalists.size}/3)", color = Lime, fontSize = 14.sp)
                }
            }
            else -> {
                val needsConnection = !model.connected || model.addressId == null
                val filtered = model.meals.any { candidate -> candidate.id !in model.passed && model.finalists.none { it.id == candidate.id } }
                val exhausted = model.meals.isNotEmpty() && !filtered
                val title = when {
                    needsConnection -> if (BuildConfig.LOCAL_DEMO) "Connect your Simulator" else "Connect to find meals"
                    !model.discoveryAttempted -> "Your next meal starts here"
                    filtered -> "No meals match these filters"
                    exhausted -> "You've seen this batch"
                    else -> "No meals found"
                }
                val description = when {
                    needsConnection -> if (BuildConfig.LOCAL_DEMO) "Turn on Tailscale and keep the Simulator running on your computer. Then reconnect to browse fictional meals." else "Connect Swiggy and choose a delivery address to browse available meals."
                    !model.discoveryAttempted -> "Keep three meals you like. We'll make the final choice easy."
                    filtered -> "Try broadening your filters. Your shortlisted meals are still here."
                    exhausted -> if (model.finalists.isNotEmpty()) "Your shortlist is ready to review. Revisit passed meals if you'd like another choice." else "Nothing caught your eye? Revisit passed meals or try another craving."
                    else -> "Try a different craving or an available restaurant below."
                }
                val label = when {
                    needsConnection -> if (BuildConfig.LOCAL_DEMO) "Reconnect Simulator" else "Connect Swiggy"
                    filtered -> "Adjust filters"
                    exhausted && model.finalists.isNotEmpty() -> "Review my shortlist"
                    exhausted -> "Revisit passed meals"
                    else -> "Find meals"
                }
                MealEmptyState(Icons.Outlined.Restaurant, title, description, label, {
                    when {
                        needsConnection -> model.accountOpen = true
                        filtered -> filtersOpen = true
                        exhausted && model.finalists.isNotEmpty() -> model.showFinalists()
                        exhausted -> model.passed.clear()
                        else -> model.search()
                    }
                }, Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (filtered && model.finalists.isNotEmpty()) TextButton(onClick = model::showFinalists) { Text("Review my shortlist", color = Muted) }
                    if (exhausted && model.passed.isNotEmpty() && model.finalists.isNotEmpty()) TextButton(onClick = { model.passed.clear() }) { Text("Revisit passed meals", color = Muted) }
                    if (!needsConnection && model.discoveryAttempted) {
                        TextButton(onClick = { model.screen = Screen.Home }) { Text("Change craving", color = Muted) }
                        model.restaurants.take(8).forEach { restaurant ->
                            TextButton(onClick = { model.restaurantMeals(restaurant) }) { Text(restaurant.name, color = White, fontSize = 14.sp) }
                        }
                    }
                }
            }
        }
    }
    if (filtersOpen) MealFiltersSheet(model) { filtersOpen = false }
}

@Composable
internal fun MealEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(80.dp).background(Surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Lime, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, color = White, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(description, color = Muted, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
        LimeButton(primaryLabel, Icons.AutoMirrored.Outlined.ArrowForward, onClick = onPrimary)
        secondary()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MealFiltersSheet(model: HungiiModel, onClose: () -> Unit) {
    var taste by remember { mutableStateOf(model.taste) }
    var highProtein by remember { mutableStateOf(model.highProtein) }
    var vegOnly by remember { mutableStateOf(model.vegOnly) }
    var budgetOnly by remember { mutableStateOf(model.budgetOnly) }
    var fast by remember { mutableStateOf(model.fast) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DisplayText("Make it your meal", 28)
            Text("Taste", color = Muted, fontSize = 16.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("any" to "Anything", "spicy" to "Spicy", "light" to "Light", "cheesy" to "Cheesy", "sweet" to "Sweet").forEach { (value, label) -> SmallChip(label, taste == value) { taste = value } }
            }
            FilterPreference("Vegetarian only", "Exclude non-vegetarian and unknown items", vegOnly) { vegOnly = it }
            FilterPreference("Menu price under ₹250", "Delivery and other fees may be extra", budgetOnly) { budgetOnly = it }
            FilterPreference("Prefer protein", "Prioritize meals with known nutrition", highProtein) { highProtein = it }
            FilterPreference("Prefer faster delivery", "Use available delivery estimates", fast) { fast = it }
            if (model.finalists.isNotEmpty()) Text("Finding a new batch starts a new shortlist.", color = Muted, fontSize = 14.sp)
            LimeButton("Find matching meals", Icons.AutoMirrored.Outlined.ArrowForward, enabled = !model.loading) {
                model.taste = taste; model.highProtein = highProtein; model.vegOnly = vegOnly; model.budgetOnly = budgetOnly; model.fast = fast
                onClose(); model.search()
            }
            TextButton(onClick = { taste = "any"; highProtein = false; vegOnly = false; budgetOnly = false; fast = false }, modifier = Modifier.fillMaxWidth()) { Text("Reset filters", color = Muted, fontSize = 14.sp) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FilterPreference(title: String, note: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value=checked,role=Role.Switch,onValueChange=onChecked), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 16.sp)
            Text(note, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedThumbColor = Charcoal, checkedTrackColor = Lime, uncheckedThumbColor=Muted, uncheckedTrackColor=Raised, uncheckedBorderColor=Line))
    }
}

@Composable
internal fun MealProfile(meal: Meal, model: HungiiModel, modifier: Modifier, onDetails: (Meal) -> Unit, onSwipe: (Boolean) -> Unit) {
    var drag by remember(meal.id) { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val restingDrag by animateFloatAsState(drag, spring(dampingRatio = .75f, stiffness = 500f), label = "card drag")
    Column(modifier.fillMaxWidth().graphicsLayer { translationX = restingDrag; rotationZ = restingDrag / 35f }.clip(RoundedCornerShape(24.dp)).background(Surface)
        .pointerInput(meal.id) {
            detectHorizontalDragGestures(onDragEnd = { if (abs(drag) > 100.dp.toPx()) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSwipe(drag > 0) }; drag = 0f }, onDragCancel = { drag = 0f }) { change, amount -> change.consume(); drag += amount }
        }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(180.dp)) {
                MealImage(meal, Modifier.fillMaxSize())
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Badge(meal.badge, Charcoal, Lime)
                    IconButton(onClick = { model.toggleSaved(meal) }, modifier = Modifier.size(48.dp).background(Charcoal, CircleShape).border(1.dp,Line,CircleShape)) {
                        Icon(if (meal.id in model.saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, "Save ${meal.name}", tint = if (meal.id in model.saved) Lime else White)
                    }
                }
                if (abs(drag) > 30) Badge(if (drag > 0) "Keep" else "Pass", if (drag > 0) Charcoal else White, if (drag > 0) Lime else Charcoal, Modifier.align(Alignment.Center))
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(meal.restaurant, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                DisplayText(meal.name, 26)
                Text((if (meal.veg == true) "Vegetarian" else if (meal.veg == false) "Non-vegetarian" else "Diet unknown") + " · " + meal.etaLabel, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    DisplayText(meal.priceLabel, 28)
                    Text("  menu price", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                AllowanceContext(meal,model)
                Text(if (meal.nutrition != null) "Estimated nutrition" else "Nutrition unavailable", color = Muted, fontSize = 12.sp)
                MacroStats(meal.nutrition)
                TextButton(onClick = { onDetails(meal) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Details & trade-offs", color = Lime, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun AllowanceContext(meal: Meal, model: HungiiModel) {
    val impact=allowanceImpact(meal.estimatedPayable,meal.itemPrice,model.moneyLeft) ?: return
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        val portion=impact.percentage?.let { " · $it% of today's remaining allowance" } ?: ""
        Text((if(impact.estimatedBasket) "~${rupees(impact.total)} estimated basket" else "${rupees(impact.total)} before fees")+portion,color=Muted,fontSize=14.sp,lineHeight=20.sp)
        val remaining=if(impact.remaining>=0) "Leaves ${if(impact.estimatedBasket) "about " else ""}${rupees(impact.remaining)} of today's allowance" else "${rupees(-impact.remaining)} over today's remaining allowance"
        Text(remaining+(if(impact.estimatedBasket) ". Final total checked at checkout." else ", before delivery and fees."),color=if(impact.remaining<0)Coral else Muted,fontSize=14.sp,lineHeight=20.sp)
    }
}
