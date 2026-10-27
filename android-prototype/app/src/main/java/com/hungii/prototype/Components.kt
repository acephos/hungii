package com.hungii.prototype

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlin.math.sin

@Composable
internal fun DisplayText(
    text: String,
    size: Int,
    modifier: Modifier = Modifier,
    color: Color = White,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
) = Text(
    text,
    modifier = modifier,
    color = color,
    fontFamily = Display,
    fontSize = size.sp,
    lineHeight = (size * 1.2f).sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-.4).sp,
    maxLines = maxLines,
    minLines = minLines,
    overflow = TextOverflow.Ellipsis,
)

@Composable
internal fun Eyebrow(
    text: String,
    color: Color,
) = Text(text, color = color, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)

@Composable
internal fun Badge(
    text: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(text, color = color, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SmallChip(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (selected) AccentWash else Surface, tween(220), label = "chip surface")
    val ink by animateColorAsState(if (selected) Rose else Muted, tween(220), label = "chip ink")
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(24.dp),
        label = { Text(text, fontSize = 14.sp, lineHeight = 20.sp) },
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = tint,
                labelColor = ink,
                selectedContainerColor = tint,
                selectedLabelColor = ink,
            ),
    )
}

@Composable
internal fun PrimaryButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val top by animateColorAsState(
        if (!enabled) {
            Raised
        } else if (pressed) {
            SoftCrimson
        } else {
            Accent
        },
        tween(180),
        label = "button highlight",
    )
    val bottom by animateColorAsState(
        if (!enabled) {
            Surface
        } else if (pressed) {
            Accent
        } else {
            AccentShade
        },
        tween(180),
        label = "button shade",
    )
    val shape = RoundedCornerShape(28.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactions,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(Brush.linearGradient(listOf(top, bottom))),
        shape = shape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Charcoal,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Muted,
            ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Icon(icon, null, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun OutlineButton(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Line),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text, color = White, fontSize = 16.sp, lineHeight = 22.sp)
    }
}

@Composable
internal fun RoundAction(
    icon: ImageVector,
    label: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
    size: Int = 53,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(size.coerceAtLeast(48).dp).background(background, CircleShape)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(if (size >= 60) 27.dp else 24.dp))
    }
}

@Composable
internal fun TradeLine(
    icon: ImageVector,
    color: Color,
    text: String,
    maxLines: Int = 2,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Text(text, color = Muted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun BillLine(
    label: String,
    value: String,
    color: Color = White,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Shows the latest request outcome; errors are highlighted, confirmations are not. */
@Composable
internal fun ActionMessage(
    actions: ActionRunner,
    fontSize: Int = 13,
) {
    val message = actions.message ?: return
    Text(
        message.asString(),
        color = if (actions.messageIsError) SoftCrimson else Muted,
        fontSize = fontSize.sp,
        lineHeight = (fontSize + 7).sp,
    )
}

@Composable
internal fun FittedMetric(
    value: String,
    size: Int,
    color: Color,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        val style = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Bold, fontFamily = Display, letterSpacing = (-.4).sp)
        val measured = measurer.measure(value, style = style, softWrap = false).size.width
        val available = with(density) { maxWidth.toPx() }
        val fitted = size * (available / measured.coerceAtLeast(1)).coerceAtMost(1f)
        Text(
            value,
            modifier = Modifier.fillMaxWidth(),
            color = color,
            fontFamily = Display,
            fontWeight = FontWeight.Bold,
            fontSize = fitted.sp,
            lineHeight = (fitted * 1.2f).sp,
            letterSpacing = (-.4).sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun FocusHeader(
    label: String,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp).border(1.dp, Line, CircleShape)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), tint = White, modifier = Modifier.size(24.dp))
        }
        Text(label, color = Muted, fontSize = 14.sp)
    }
}

@Composable
internal fun MetricTile(
    label: String,
    value: String,
    note: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Surface).padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(label, Muted)
        FittedMetric(value, 36, color)
        Text(note, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
internal fun MacroStats(nutrition: Nutrition?) {
    val unknown = stringResource(R.string.value_unknown)
    val values =
        listOf(
            (nutrition?.calories?.label ?: unknown) to stringResource(R.string.macro_calories),
            (nutrition?.protein?.label ?: unknown) to stringResource(R.string.macro_protein),
            (nutrition?.carbs?.label ?: unknown) to stringResource(R.string.macro_carbs),
            (nutrition?.fat?.label ?: unknown) to stringResource(R.string.macro_fat),
        )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        values.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (value, label) ->
                    Column(Modifier.weight(1f)) {
                        DisplayText(value, 20, color = White)
                        Text(label, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MealImage(
    meal: Meal,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(Raised), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Restaurant, null, tint = Muted, modifier = Modifier.size(36.dp))
        val photo = if (BuildConfig.LOCAL_DEMO && meal.photo > 0) "file:///android_asset/meal-${meal.photo}.jpg" else meal.imageUrl
        if (photo != null) {
            // In-memory caching only: provider photos are never written to disk.
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(photo)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build(),
                contentDescription = meal.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
internal fun FilledInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onSubmit: (() -> Unit)? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val edge by animateColorAsState(if (focused) Accent else Line, tween(220), label = "field edge")
    val tint by animateColorAsState(if (focused) AccentWash else Raised, tween(220), label = "field surface")
    val caption by animateColorAsState(if (focused) Rose else Muted, tween(220), label = "field caption")
    TextField(
        value,
        onValueChange,
        modifier = modifier.heightIn(min = 60.dp).border(1.dp, edge, RoundedCornerShape(20.dp)),
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        label =
            if (showLabel || label != null) {
                { Text(label ?: placeholder, fontSize = 12.sp, color = caption, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else {
                null
            },
        placeholder = { Text(placeholder, fontSize = 16.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = White),
        interactionSource = interactions,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = keyboardType,
                imeAction =
                    if (onSubmit != null) {
                        ImeAction.Search
                    } else {
                        ImeAction.Done
                    },
            ),
        keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = tint,
                unfocusedContainerColor = tint,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = Accent,
            ),
    )
}

@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Raised.copy(alpha = .65f), Surface)))
            .border(1.dp, Line.copy(alpha = .55f), RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun ThinkingDots() {
    val reduced = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "loading")
    val phase by transition.animateFloat(0f, 6.283f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        alpha = if (reduced) .65f else .35f + .65f * ((sin(phase - i) + 1) / 2)
                        translationY = if (reduced) 0f else -3f * sin(phase - i)
                    }.background(Accent, CircleShape),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.thinking), color = Muted, fontSize = 13.sp)
    }
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
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(80.dp).background(Surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.size(24.dp))
        Text(title, color = White, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            description,
            color = Muted,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        PrimaryButton(primaryLabel, Icons.AutoMirrored.Outlined.ArrowForward, onClick = onPrimary)
        secondary()
    }
}

// Meal labels. Provider names stay as returned; unknown values are labelled, never guessed.

@Composable
internal fun Meal.priceLabel(): String = itemPrice?.let(::rupees) ?: stringResource(R.string.meal_price_unavailable)

@Composable
internal fun Meal.etaLabel(): String =
    etaMinutes?.let { stringResource(R.string.meal_eta, it) } ?: stringResource(R.string.meal_eta_unavailable)

@Composable
internal fun Meal.badge(): String = stringResource(if (BuildConfig.LOCAL_DEMO) R.string.meal_badge_demo else R.string.meal_badge_swiggy)

@Composable
internal fun Meal.benefit(): String = description.ifBlank { stringResource(R.string.meal_benefit_fallback, restaurant, etaLabel()) }

@Composable
internal fun Meal.compromise(): String =
    stringResource(
        if (nutrition != null) {
            R.string.meal_compromise_estimated
        } else {
            R.string.meal_compromise_unknown
        },
    )

@Composable
internal fun Meal.dietLabel(): String =
    stringResource(
        when (veg) {
            true -> R.string.meal_diet_veg
            false -> R.string.meal_diet_nonveg
            null -> R.string.meal_diet_unknown
        },
    )
