package com.hungii.prototype

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri

private fun openSwiggy(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("in.swiggy.android")
    try {
        context.startActivity(launch ?: Intent(Intent.ACTION_VIEW, "https://www.swiggy.com/".toUri()))
    } catch (_: Exception) {
        Toast.makeText(context, R.string.swiggy_missing, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun money(value: Double?): String = value?.let(::rupees) ?: stringResource(R.string.value_unavailable)

/** Recent synthetic MCP tool calls, for Simulator demonstrations. */
@Composable
private fun McpActivity(checkout: Checkout) {
    if (!BuildConfig.LOCAL_DEMO) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Terminal, null, tint = Muted, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.mcp_activity),
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, stringResource(R.string.mcp_toggle), tint = Muted)
        }
        if (expanded) {
            checkout.trace
                .takeLast(
                    12,
                ).forEach { Text(it, color = Rose, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
internal fun CheckoutScreen(model: HungiiModel) {
    val context = LocalContext.current
    val checkout = model.checkout
    val cart = checkout.cart
    var coupon by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FocusHeader(stringResource(R.string.basket_header), model::goBack)
            DisplayText(stringResource(R.string.basket_title), 32)
            if (model.loading) ThinkingDots()
            ActionMessage(model.actions)
            if (!model.connection.orderingEnabled) {
                Panel {
                    Text(stringResource(R.string.basket_pending_title), color = Accent, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.basket_pending_body), color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                    model.discovery.winner?.let { Text(it.name, color = White, fontSize = 18.sp) }
                    OutlineButton(stringResource(R.string.action_back_to_day)) { model.go(Screen.Day) }
                }
            }
            if (cart != null) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.LocationOn, null, tint = Accent)
                        Column {
                            Text(stringResource(R.string.basket_deliver_to), color = Muted, fontSize = 12.sp)
                            Text(cart.address ?: "", color = White, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
                cart.items.forEach { CartItemPanel(model, it, cart.restaurant ?: "") }
                cart.nudge?.let { NudgePanel(model, it) }
                if (cart.items.isNotEmpty()) BillPanel(cart) else Text(stringResource(R.string.basket_empty), color = Muted)
                if (!BuildConfig.LOCAL_DEMO) {
                    FilledInput(coupon, { coupon = it.take(80) }, stringResource(R.string.basket_coupon), Modifier.fillMaxWidth())
                    OutlineButton(stringResource(R.string.basket_apply_coupon)) {
                        if (!model.loading && coupon.isNotBlank()) {
                            checkout.applyCoupon(coupon)
                        }
                    }
                }
            }
            McpActivity(checkout)
            if (BuildConfig.LOCAL_DEMO) {
                OutlineButton(stringResource(R.string.basket_open_swiggy)) { openSwiggy(context) }
                Text(stringResource(R.string.basket_demo_note), color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(
            Modifier.fillMaxWidth().background(Charcoal).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val total = cart?.payable
            val count = cart?.itemCount ?: 0
            if (total != null && !model.loading && !checkout.refreshRequired) {
                val left = model.moneyLeft
                val position =
                    if (total <= left) {
                        stringResource(R.string.basket_allowance_left, rupees(left - total))
                    } else {
                        stringResource(R.string.basket_allowance_over, rupees(total - left))
                    }
                Text(pluralStringResource(R.plurals.basket_summary, count, count, position), color = Muted, fontSize = 14.sp)
            }
            if (checkout.refreshRequired) Text(stringResource(R.string.basket_refresh_hint), color = Muted, fontSize = 14.sp)
            val label =
                when {
                    model.actions.isBusy(Operation.Cart) -> stringResource(R.string.basket_updating)
                    checkout.refreshRequired -> stringResource(R.string.basket_refresh)
                    total != null -> stringResource(R.string.basket_choose_payment_total, rupees(total))
                    else -> stringResource(R.string.basket_choose_payment)
                }
            PrimaryButton(
                label,
                Icons.AutoMirrored.Outlined.ArrowForward,
                enabled = model.connection.orderingEnabled && !model.loading && (checkout.refreshRequired || (count > 0 && total != null)),
            ) { if (checkout.refreshRequired) checkout.refreshCart() else checkout.choosePayment() }
        }
    }
}

@Composable
private fun NudgePanel(
    model: HungiiModel,
    nudge: CartNudge,
) {
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocalOffer, null, tint = Accent)
            Text(stringResource(R.string.nudge_title), color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            stringResource(R.string.nudge_body, nudge.name, rupees(nudge.price), nudge.coupon, rupees(nudge.saving)),
            color = White,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        nudge.calories?.let { Text(stringResource(R.string.nudge_tradeoff, it.label), color = SoftCrimson, fontSize = 12.sp) }
        OutlineButton(
            stringResource(R.string.nudge_add, rupees(nudge.newPayable)),
        ) { if (!model.loading) model.checkout.addSide(nudge.dishId) }
    }
}

@Composable
private fun BillPanel(cart: Cart) {
    Panel {
        Text(stringResource(R.string.bill_title), color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        BillLine(stringResource(R.string.bill_food), money(cart.itemTotal))
        BillLine(stringResource(R.string.bill_delivery), money(cart.deliveryCharge))
        if (BuildConfig.LOCAL_DEMO) {
            BillLine(stringResource(R.string.bill_packaging), money(cart.fees.packaging))
            BillLine(stringResource(R.string.bill_platform), money(cart.fees.platform))
        }
        BillLine(stringResource(if (BuildConfig.LOCAL_DEMO) R.string.bill_tax else R.string.bill_taxes), money(cart.fees.tax))
        if (cart.couponDiscount > 0) BillLine(cart.appliedCoupon ?: "", "−" + rupees(cart.couponDiscount), Accent)
        HorizontalDivider(color = Line)
        BillLine(stringResource(R.string.bill_to_pay), money(cart.payable), Accent)
        Text(stringResource(if (BuildConfig.LOCAL_DEMO) R.string.bill_note_demo else R.string.bill_note), color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun CartItemPanel(
    model: HungiiModel,
    item: CartItem,
    restaurant: String,
) {
    val checkout = model.checkout
    val busy = model.loading
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val image =
                model.discovery.meals.firstOrNull { it.dishId == item.menuItemId }
                    ?: model.discovery.winner?.copy(name = item.name, photo = item.photo)
            if (image != null) MealImage(image, Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, color = White, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(restaurant, color = Muted, fontSize = 14.sp)
                item.total?.let {
                    Text(
                        pluralStringResource(R.plurals.cart_item_total, item.quantity, rupees(it), item.quantity),
                        color = White,
                        fontSize = 16.sp,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.cart_quantity), color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { checkout.changeQuantity(item, item.quantity - 1) }, enabled = !busy) {
                Icon(Icons.Outlined.Remove, stringResource(R.string.cart_remove_one), tint = Muted)
            }
            Text("${item.quantity}", color = White, fontSize = 17.sp)
            IconButton(onClick = { checkout.changeQuantity(item, item.quantity + 1) }, enabled = !busy && item.quantity < 10) {
                Icon(Icons.Outlined.Add, stringResource(R.string.cart_add_one), tint = Accent)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3).forEach { count ->
                SmallChip(stringResource(R.string.cart_quantity_chip, count), item.quantity == count, enabled = !busy) {
                    checkout.changeQuantity(item, count)
                }
            }
        }
        item.variantGroups.forEach { group ->
            Text(group.name ?: stringResource(R.string.cart_portion), color = Muted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.variations.forEach { choice ->
                    SmallChip(choice.name, choice.id in item.selectedVariations) { if (!busy) checkout.changeVariant(item, group, choice) }
                }
            }
        }
        item.addonGroups.forEach { group ->
            group.choices.forEach { choice ->
                val checked = item.selectedAddons.any { it.addonId == choice.id }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, onCheckedChange = { on ->
                        if (!busy) checkout.changeAddons(item, item.addonsWith(group, choice, on))
                    })
                    Text(stringResource(R.string.cart_addon, choice.name, money(choice.price)), color = White, fontSize = 13.sp)
                }
            }
        }
        item.nutrition?.let {
            MacroStats(it)
            Text(stringResource(R.string.cart_nutrition_note), color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun PaymentScreen(model: HungiiModel) {
    val context = LocalContext.current
    val checkout = model.checkout
    var confirm by rememberSaveable { mutableStateOf(false) }
    val cart = checkout.paymentCart
    val stage = checkout.stage
    val demo = BuildConfig.LOCAL_DEMO
    val staging = model.connection.environment == "staging"
    val payable = cart?.payable?.let(::rupees) ?: stringResource(R.string.value_unavailable)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FocusHeader(stringResource(if (demo) R.string.payment_header_demo else R.string.payment_header), model::goBack)
        Text(cart?.restaurant ?: stringResource(R.string.payment_your_basket), color = Muted, fontSize = 14.sp)
        DisplayText(payable, 48)
        val environment =
            when {
                demo -> R.string.payment_environment_demo
                staging -> R.string.payment_environment_staging
                else -> R.string.payment_environment
            }
        Text(stringResource(environment), color = Accent, fontSize = 12.sp)
        if (model.loading) ThinkingDots()
        ActionMessage(model.actions)
        when {
            stage == PaymentStage.Choose -> {
                Panel {
                    Text(stringResource(R.string.payment_pay_using), color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    val methods = checkout.paymentMethods.orEmpty()
                    methods.forEach { method ->
                        val enabled = method.enabled && !model.loading
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Raised)
                                .clickable(enabled = enabled) { checkout.selectedMethodId = method.id }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Payments, null, tint = White, modifier = Modifier.size(24.dp))
                            Text(
                                method.displayName,
                                color = White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            )
                            RadioButton(
                                checkout.selectedMethodId == method.id,
                                onClick = { checkout.selectedMethodId = method.id },
                                enabled = enabled,
                            )
                        }
                    }
                    if (methods.isEmpty()) Text(stringResource(R.string.payment_methods_pending), color = Muted, fontSize = 14.sp)
                }
                FilledInput(checkout.note, { checkout.note = it.take(200) }, stringResource(R.string.payment_note), Modifier.fillMaxWidth())
                if (checkout.refreshRequired) OutlineButton(stringResource(R.string.payment_refresh_options), checkout::choosePayment)
                PrimaryButton(
                    stringResource(R.string.payment_review, payable),
                    Icons.Outlined.Lock,
                    enabled =
                        !model.loading && !checkout.refreshRequired && checkout.paymentMethods != null &&
                            checkout.selectedMethodId.isNotBlank() && cart?.payable != null,
                ) { confirm = true }
            }

            stage.inFlight -> {
                Panel {
                    Icon(Icons.Outlined.HourglassTop, null, tint = Accent, modifier = Modifier.size(32.dp))
                    val heading = if (stage == PaymentStage.Unresolved) R.string.payment_checking else R.string.payment_pending
                    DisplayText(stringResource(heading), 26)
                    Text(
                        stringResource(if (demo) R.string.payment_pending_body_demo else R.string.payment_pending_body),
                        color = Muted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                    if (demo) {
                        PrimaryButton(stringResource(R.string.payment_simulate_success), Icons.Outlined.Check, enabled = !model.loading) {
                            checkout.simulatePayment("SUCCESS")
                        }
                        OutlineButton(
                            stringResource(R.string.payment_simulate_failure),
                        ) { if (!model.loading) checkout.simulatePayment("FAILED") }
                        TextButton(onClick = { if (!model.loading) checkout.simulatePayment("CANCELLED") }) {
                            Text(stringResource(R.string.payment_simulate_cancel), color = SoftCrimson)
                        }
                    } else {
                        checkout.payment?.safeBridgeUrl?.let { url ->
                            PrimaryButton(
                                stringResource(R.string.payment_continue_upi),
                                Icons.Outlined.Payments,
                                enabled = !model.loading,
                            ) {
                                openInBrowser(context, url, model.actions, R.string.payment_browser_missing)
                            }
                        }
                        OutlineButton(stringResource(R.string.payment_check_swiggy)) { openSwiggy(context) }
                    }
                    PrimaryButton(stringResource(R.string.payment_check_status), Icons.Outlined.Refresh, enabled = !model.loading) {
                        checkout.refreshPayment()
                    }
                }
            }

            stage == PaymentStage.Failed -> {
                Panel {
                    DisplayText(stringResource(R.string.payment_failed_title), 27)
                    Text(stringResource(R.string.payment_failed_body), color = Muted, fontSize = 14.sp)
                    PrimaryButton(
                        stringResource(R.string.payment_retry),
                        Icons.Outlined.Refresh,
                        enabled = !model.loading,
                        onClick = checkout::retryPayment,
                    )
                }
            }

            stage == PaymentStage.Confirmed -> {
                PrimaryButton(stringResource(R.string.payment_view_order), Icons.Outlined.Check) {
                    model.go(Screen.Order)
                    checkout.refreshOrder()
                }
            }
        }
        McpActivity(checkout)
        Spacer(Modifier.height(20.dp))
    }
    if (confirm) ConfirmOrderDialog(model, cart, demo || staging) { confirm = false }
}

@Composable
private fun ConfirmOrderDialog(
    model: HungiiModel,
    cart: Cart?,
    testOrder: Boolean,
    onDismiss: () -> Unit,
) {
    val checkout = model.checkout
    val demo = BuildConfig.LOCAL_DEMO
    val method = checkout.paymentMethods?.firstOrNull { it.id == checkout.selectedMethodId }
    val title =
        when {
            demo -> R.string.confirm_order_title_demo
            testOrder -> R.string.confirm_order_title_staging
            else -> R.string.confirm_order_title
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(stringResource(title)) },
        text = {
            Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(cart?.restaurant ?: "")
                Text(stringResource(R.string.confirm_order_deliver_to, cart?.address ?: ""))
                cart?.items?.forEach { Text(stringResource(R.string.confirm_order_item, it.quantity, it.name)) }
                Text(
                    stringResource(
                        R.string.confirm_order_total,
                        cart?.payable?.let(::rupees) ?: "",
                        method?.displayName ?: checkout.selectedMethodId,
                    ),
                    color = Accent,
                )
                Text(
                    stringResource(if (testOrder) R.string.confirm_order_test_note else R.string.confirm_order_real_note),
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                checkout.beginPayment()
            }) { Text(stringResource(if (testOrder) R.string.confirm_order_test else R.string.confirm_order_place)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm_order_keep_reviewing)) } },
    )
}

@Composable
internal fun OrderScreen(model: HungiiModel) {
    val context = LocalContext.current
    val checkout = model.checkout
    val demo = BuildConfig.LOCAL_DEMO
    val staging = model.connection.environment == "staging"
    val tracking = checkout.order?.tracking
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.size(90.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Check, stringResource(R.string.order_confirmed), tint = Charcoal, modifier = Modifier.size(42.dp))
        }
        val title =
            when {
                demo -> R.string.order_title_demo
                staging -> R.string.order_title_staging
                else -> R.string.order_title
            }
        DisplayText(stringResource(title), 30)
        Panel {
            Text(
                stringResource(
                    if (demo || staging) {
                        R.string.order_test_environment
                    } else {
                        R.string.payment_environment
                    },
                ),
                color = Accent,
                fontSize = 14.sp,
            )
            Text(
                stringResource(R.string.order_number, checkout.payment?.orderId ?: checkout.order?.orderId ?: ""),
                color = Muted,
                fontSize = 12.sp,
            )
            Text(checkout.checkoutCart?.restaurant ?: "", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            BillLine(stringResource(R.string.order_total), money(checkout.checkoutCart?.payable))
            HorizontalDivider(color = Line)
            if (demo) {
                Text(stringResource(R.string.order_demo_eta), color = Muted, fontSize = 12.sp)
            } else {
                Text(tracking?.headline ?: stringResource(R.string.order_tracking_pending), color = White, fontSize = 16.sp)
                tracking?.etaText?.let { Text(it, color = Muted, fontSize = 14.sp) }
            }
        }
        ActionMessage(model.actions)
        OutlineButton(stringResource(R.string.order_refresh), checkout::refreshOrder)
        if (!demo) OutlineButton(stringResource(R.string.order_support)) { openSwiggy(context) }
        Text(stringResource(R.string.order_log_hint), color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        PrimaryButton(
            stringResource(if (demo) R.string.order_log_demo else R.string.order_log),
            Icons.Outlined.Restaurant,
            onClick = checkout::logOrderAsEaten,
        )
        OutlineButton(stringResource(R.string.action_back_to_day)) { model.go(Screen.Day) }
        McpActivity(checkout)
    }
}
