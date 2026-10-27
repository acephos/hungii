package com.hungii.prototype

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Tab(
    val screen: Screen,
    val label: Int,
    val icon: ImageVector,
)

private val tabs =
    listOf(
        Tab(Screen.Home, R.string.tab_home, Icons.Outlined.Home),
        Tab(Screen.Discover, R.string.tab_meals, Icons.Outlined.Restaurant),
        Tab(Screen.Assistant, R.string.tab_assistant, Icons.Outlined.AutoAwesome),
        Tab(Screen.Day, R.string.tab_day, Icons.Outlined.BarChart),
    )

@Composable
internal fun HungiiTabs(model: HungiiModel) {
    val current = model.navigator.screen
    NavigationBar(containerColor = Surface, contentColor = Muted, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            val selected = current == tab.screen || (tab.screen == Screen.Discover && current == Screen.Saved)
            val indicator by animateColorAsState(if (selected) AccentWash else Color.Transparent, tween(260), label = "tab surface")
            val foreground by animateColorAsState(if (selected) Rose else Muted, tween(260), label = "tab ink")
            NavigationBarItem(
                selected = selected,
                onClick = { model.go(tab.screen) },
                icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                label = { TabLabel(stringResource(tab.label)) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = foreground,
                        selectedTextColor = foreground,
                        indicatorColor = indicator,
                        unselectedIconColor = Muted,
                        unselectedTextColor = Muted,
                    ),
            )
        }
    }
}

/** Shrinks long translated labels to fit, instead of truncating them. */
@Composable
private fun TabLabel(label: String) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width =
            measurer
                .measure(
                    label,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    softWrap = false,
                ).size.width
        val fit = (with(density) { maxWidth.toPx() } / width.coerceAtLeast(1)).coerceAtMost(1f)
        Text(label, fontSize = (12 * fit).sp, lineHeight = (16 * fit).sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}

@Composable
internal fun HomeScreen(
    model: HungiiModel,
    onEdit: () -> Unit,
) {
    val tracker = model.day.tracker
    val prefs = model.day.preferences
    val searching = model.actions.isBusy(Operation.Discovery)
    val canSearch = !model.loading && tracker.opportunities > 0
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HungiiWordmark(height = 56.dp)
                Text(
                    if (prefs.displayName.isBlank()) {
                        stringResource(R.string.home_greeting)
                    } else {
                        stringResource(R.string.home_greeting_named, prefs.displayName)
                    },
                    color = Muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
            RoundAction(Icons.Outlined.Person, stringResource(R.string.home_accounts), Raised, White, { model.accountOpen = true }, 48)
        }
        Panel {
            Text(stringResource(R.string.home_allowance), color = Muted, fontSize = 14.sp)
            val reserved = model.checkout.reservedSpend
            if (reserved > 0) Text(stringResource(R.string.home_reserved, rupees(reserved.toDouble())), color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    DisplayText(rupees(model.moneyLeft.toDouble()), 44)
                    Text(
                        pluralStringResource(R.plurals.home_left_today, tracker.opportunities, tracker.opportunities),
                        color = Muted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.home_edit_day), color = Accent, fontSize = 14.sp) }
            }
        }
        ConnectionStrip(model)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DisplayText(stringResource(R.string.home_question), 28)
            FilledInput(
                prefs.query,
                { query -> model.day.updatePreferences { it.copy(query = query) } },
                stringResource(R.string.home_craving_placeholder),
                Modifier.fillMaxWidth(),
                label = stringResource(R.string.home_craving_label),
                onSubmit = { if (canSearch) model.discovery.search() },
            )
            PrimaryButton(
                stringResource(if (searching) R.string.home_searching else R.string.home_search),
                Icons.AutoMirrored.Outlined.ArrowForward,
                enabled = canSearch,
            ) { model.discovery.search() }
            if (tracker.opportunities == 0) Text(stringResource(R.string.home_no_meals_left), color = Muted, fontSize = 14.sp)
            ActionMessage(model.actions, fontSize = 14)
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Text(stringResource(R.string.home_calories_left), color = Muted, fontSize = 14.sp)
                DisplayText(tracker.caloriesLeft.label, 28)
                Text(stringResource(R.string.home_calorie_goal, tracker.calorieGoal), color = Muted, fontSize = 12.sp)
            }
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Text(stringResource(R.string.home_protein_left), color = Muted, fontSize = 14.sp)
                DisplayText(stringResource(R.string.unit_grams, tracker.proteinLeft.mid.toInt()), 28)
                Text(stringResource(R.string.home_protein_goal, tracker.proteinGoal), color = Muted, fontSize = 12.sp)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Surface)
                .clickable { model.go(Screen.Assistant) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassOrb(Modifier.size(48.dp), VoiceState(), thinking = false, reduceMotion = true)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_help_title), color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.home_help_body), color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ConnectionStrip(model: HungiiModel) {
    val connection = model.connection
    val demo = BuildConfig.LOCAL_DEMO
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable { model.accountOpen = true }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.LocationOn, null, tint = Accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (demo) R.string.strip_title_demo else R.string.strip_title),
                color = White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val status =
                when {
                    connection.ready -> R.string.strip_address_selected
                    !demo && !connection.available -> R.string.strip_access_pending
                    else -> R.string.strip_connect
                }
            Text(stringResource(status), color = Muted, fontSize = 12.sp)
        }
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            stringResource(R.string.strip_settings),
            tint = Accent,
            modifier = Modifier.size(18.dp),
        )
    }
    if (model.checkout.requestId != null) {
        val label = if (model.checkout.stage == PaymentStage.Confirmed) R.string.strip_view_order else R.string.strip_resume_checkout
        TextButton(onClick = model.checkout::resumeCheckout, enabled = !model.loading) {
            Text(stringResource(label), color = Accent)
        }
    }
}
