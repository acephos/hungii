package com.hungii.prototype

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri

/** Opens [url] in a Custom Tab; reports a missing browser instead of crashing. */
internal fun openInBrowser(
    context: Context,
    url: String,
    actions: ActionRunner,
    @androidx.annotation.StringRes missingBrowser: Int = R.string.browser_missing,
) {
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
    } catch (_: Exception) {
        actions.report(UiText.of(missingBrowser))
    }
}

private fun startSignIn(
    context: Context,
    model: HungiiModel,
) {
    try {
        openInBrowser(context, model.signInUrl(), model.actions, R.string.browser_missing_sign_in)
    } catch (e: ApiFailure) {
        model.actions.report(UiText.Raw(e.message))
    }
}

/** First screen of the real build: plan locally, or sign in to sync. */
@Composable
internal fun AccountEntry(model: HungiiModel) {
    val context = LocalContext.current
    val tracker = model.day.tracker
    Column(
        Modifier
            .fillMaxSize()
            .background(Charcoal)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        HungiiWordmark(height = 72.dp)
        DisplayText(stringResource(R.string.entry_title), 36)
        Text(stringResource(R.string.entry_body), color = Muted, fontSize = 16.sp, lineHeight = 24.sp)
        Column(
            Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.entry_plan_title), color = Muted, fontSize = 14.sp)
            DisplayText(rupees(tracker.allowance.toDouble()), 40)
            Text(
                pluralStringResource(R.plurals.entry_plan_summary, tracker.opportunities, tracker.opportunities, tracker.calorieGoal),
                color = White,
                fontSize = 16.sp,
            )
            Text(stringResource(R.string.entry_plan_hint), color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        }
        PrimaryButton(stringResource(R.string.entry_start), Icons.AutoMirrored.Outlined.ArrowForward) { model.useOffline() }
        OutlinedButton(
            onClick = { startSignIn(context, model) },
            enabled = BuildConfig.WORKOS_AUTH_READY && !model.loading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            border = BorderStroke(1.dp, Line),
        ) {
            Text(stringResource(R.string.entry_sign_in), color = if (BuildConfig.WORKOS_AUTH_READY) White else Muted, fontSize = 16.sp)
        }
        Text(stringResource(R.string.entry_footer), color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        ActionMessage(model.actions, fontSize = 14)
    }
}

/** Shown after sign-in until the user decides about cloud sync. */
@Composable
internal fun CloudEntry(model: HungiiModel) {
    val cloud = model.cloud
    Column(
        Modifier
            .fillMaxSize()
            .background(Charcoal)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        HungiiWordmark(height = 72.dp)
        Eyebrow(stringResource(R.string.cloud_entry_eyebrow), Accent)
        DisplayText(stringResource(R.string.cloud_entry_title), 34)
        Text(stringResource(R.string.cloud_entry_body), color = Muted, fontSize = 16.sp, lineHeight = 25.sp)
        Text(stringResource(R.string.cloud_entry_details, model.noticeVersion), color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
        PrimaryButton(stringResource(R.string.cloud_entry_allow), Icons.Outlined.CloudUpload, enabled = !model.loading) { cloud.allow() }
        if (cloud.conflict) {
            cloud.message?.let { Text(it.asString(), color = SoftCrimson, fontSize = 13.sp) }
            TextButton(onClick = cloud::useCloudCopy) { Text(stringResource(R.string.cloud_use_cloud_copy), color = Accent) }
            TextButton(onClick = cloud::keepDeviceCopy) { Text(stringResource(R.string.cloud_use_device_copy), color = SoftCrimson) }
        }
        TextButton(onClick = cloud::keepOnDevice) { Text(stringResource(R.string.cloud_entry_skip), color = Muted) }
        ActionMessage(model.actions)
    }
}

private enum class AccountSection(
    val label: Int,
) {
    Delivery(R.string.account_section_delivery),
    Profile(R.string.account_section_profile),
    Privacy(R.string.account_section_privacy),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountDialog(
    model: HungiiModel,
    onClose: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(AccountSection.Delivery) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DisplayText(stringResource(R.string.account_title), 28)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountSection.entries.forEach { SmallChip(stringResource(it.label), section == it) { section = it } }
            }
            if (model.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent)
            ActionMessage(model.actions)
            when (section) {
                AccountSection.Delivery -> DeliverySection(model, onClose)
                AccountSection.Profile -> ProfileSection(model)
                AccountSection.Privacy -> PrivacySection(model)
            }
            OutlineButton(stringResource(R.string.action_done), onClose)
        }
    }
}

@Composable
private fun DeliverySection(
    model: HungiiModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val connection = model.connection
    val demo = BuildConfig.LOCAL_DEMO
    var consent by remember { mutableStateOf(false) }
    var showAddresses by remember { mutableStateOf(connection.connected && connection.addressId == null) }
    Text(
        stringResource(if (demo) R.string.delivery_title_demo else R.string.delivery_title),
        color = White,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        stringResource(if (demo) R.string.delivery_body_demo else R.string.delivery_body),
        color = Muted,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )
    when {
        !model.signedIn -> {
            Text(stringResource(R.string.delivery_sign_in_first), color = Muted, fontSize = 13.sp)
            PrimaryButton(
                stringResource(R.string.delivery_sign_in),
                Icons.AutoMirrored.Outlined.Login,
                enabled = model.configured && BuildConfig.WORKOS_AUTH_READY && !model.loading,
            ) { startSignIn(context, model) }
        }

        !connection.connected -> {
            if (!connection.available && !demo) {
                Text(stringResource(R.string.delivery_access_pending), color = Accent, fontSize = 14.sp, lineHeight = 21.sp)
            } else {
                if (!demo) {
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(
                            consent,
                            onCheckedChange = { consent = it },
                            colors = CheckboxDefaults.colors(checkedColor = Accent, checkmarkColor = Charcoal),
                        )
                        Text(
                            stringResource(R.string.delivery_consent),
                            color = Muted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                PrimaryButton(
                    stringResource(if (demo) R.string.delivery_reconnect_demo else R.string.delivery_connect),
                    Icons.Outlined.Link,
                    enabled = (demo || consent) && !model.loading,
                ) { model.connect { url -> openInBrowser(context, url, model.actions) } }
            }
            TextButton(onClick = { model.refresh() }, enabled = !model.loading) {
                Text(stringResource(R.string.delivery_check_connection), color = Accent)
            }
        }

        else -> {
            val status =
                when {
                    demo -> R.string.delivery_connected_demo
                    connection.environment == "staging" -> R.string.delivery_connected_staging
                    else -> R.string.delivery_connected
                }
            Text(stringResource(status), color = Accent, fontSize = 14.sp)
            if (!connection.orderingEnabled && !demo) {
                Text(stringResource(R.string.delivery_checkout_pending), color = Muted, fontSize = 13.sp)
            }
            val addressAction = if (connection.addressId == null) R.string.delivery_choose_address else R.string.delivery_change_address
            OutlineButton(stringResource(addressAction)) {
                if (!model.loading) {
                    showAddresses = true
                    model.addressList()
                }
            }
            if (showAddresses) {
                connection.addresses.forEach { address ->
                    OutlinedButton(
                        onClick = { model.selectAddress(address) },
                        enabled = !model.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(address.label, color = White, fontWeight = FontWeight.SemiBold)
                            Text(address.addressLine, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                }
                Row {
                    if (connection.addressPage > 1) {
                        TextButton(onClick = { model.addressList(connection.addressPage - 1) }, enabled = !model.loading) {
                            Text(stringResource(R.string.delivery_previous_addresses))
                        }
                    }
                    if (connection.moreAddresses) {
                        TextButton(onClick = { model.addressList(connection.addressPage + 1) }, enabled = !model.loading) {
                            Text(stringResource(R.string.delivery_more_addresses))
                        }
                    }
                }
            }
            if (model.checkout.requestId != null) {
                OutlineButton(stringResource(R.string.delivery_resume_checkout)) {
                    onClose()
                    model.checkout.resumeCheckout()
                }
            }
            TextButton(onClick = { model.privacyAction = PrivacyAction.Disconnect }, enabled = !model.loading) {
                Text(stringResource(R.string.delivery_disconnect), color = SoftCrimson)
            }
        }
    }
}

@Composable
private fun ProfileSection(model: HungiiModel) {
    val context = LocalContext.current
    val cloud = model.cloud
    Text(stringResource(R.string.profile_title), color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.profile_body), color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
    when {
        BuildConfig.LOCAL_DEMO -> {
            Text(stringResource(R.string.profile_unavailable_demo), color = Muted, fontSize = 13.sp)
        }

        !model.signedIn -> {
            PrimaryButton(
                stringResource(R.string.profile_sign_in),
                Icons.AutoMirrored.Outlined.Login,
                enabled = model.configured && BuildConfig.WORKOS_AUTH_READY && !model.loading,
            ) { startSignIn(context, model) }
        }

        else -> {
            Text(
                stringResource(if (cloud.enabled) R.string.profile_sync_on else R.string.profile_sync_off),
                color = Accent,
                fontSize = 14.sp,
            )
            cloud.message?.let { Text(it.asString(), color = Muted, fontSize = 13.sp) }
            when {
                cloud.conflict -> {
                    OutlineButton(stringResource(R.string.cloud_use_cloud_copy_short), cloud::useCloudCopy)
                    OutlineButton(stringResource(R.string.cloud_use_device_copy_short), cloud::keepDeviceCopy)
                }

                !cloud.enabled -> {
                    PrimaryButton(stringResource(R.string.profile_enable_sync), Icons.Outlined.CloudUpload, enabled = !model.loading) {
                        model.privacyAction = PrivacyAction.SyncProfile
                    }
                }

                else -> {
                    OutlineButton(stringResource(R.string.profile_pause_sync), cloud::pause)
                }
            }
            TextButton(onClick = cloud::restoreFromCloud, enabled = !model.loading) {
                Text(stringResource(R.string.profile_restore), color = Accent)
            }
            TextButton(onClick = { model.signOut { url -> openInBrowser(context, url, model.actions) } }, enabled = !model.loading) {
                Text(stringResource(R.string.profile_sign_out), color = Muted)
            }
        }
    }
}

@Composable
private fun PrivacySection(model: HungiiModel) {
    Text(stringResource(R.string.privacy_title), color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.privacy_storage), color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
    Text(
        stringResource(if (BuildConfig.LOCAL_DEMO) R.string.privacy_details_demo else R.string.privacy_details),
        color = Muted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
    Text(stringResource(R.string.privacy_voice, model.noticeVersion), color = Muted, fontSize = 12.sp)
    if (model.signedIn && !BuildConfig.LOCAL_DEMO) {
        TextButton(onClick = { model.privacyAction = PrivacyAction.DeleteCloudTracker }, enabled = !model.loading) {
            Text(stringResource(R.string.privacy_erase_cloud), color = SoftCrimson)
        }
        TextButton(onClick = { model.privacyAction = PrivacyAction.DeleteAccount }, enabled = !model.loading) {
            Text(stringResource(R.string.privacy_delete_account), color = SoftCrimson)
        }
    }
    TextButton(onClick = { model.privacyAction = PrivacyAction.EraseDevice }, enabled = !model.loading) {
        Text(stringResource(R.string.privacy_erase_device), color = SoftCrimson)
    }
}

@Composable
internal fun PrivacyDialog(
    model: HungiiModel,
    action: PrivacyAction,
) {
    val (title, body) =
        when (action) {
            PrivacyAction.SyncProfile -> R.string.confirm_sync_title to R.string.confirm_sync_body
            PrivacyAction.Disconnect -> R.string.confirm_disconnect_title to R.string.confirm_disconnect_body
            PrivacyAction.DeleteAccount -> R.string.confirm_delete_account_title to R.string.confirm_delete_account_body
            PrivacyAction.DeleteCloudTracker -> R.string.confirm_erase_cloud_title to R.string.confirm_erase_cloud_body
            PrivacyAction.EraseDevice -> R.string.confirm_erase_device_title to R.string.confirm_erase_device_body
        }
    val consent = action == PrivacyAction.SyncProfile
    AlertDialog(
        onDismissRequest = { model.privacyAction = null },
        containerColor = Surface,
        title = { Text(stringResource(title), color = White) },
        text = { Text(if (consent) stringResource(body, model.noticeVersion) else stringResource(body), color = Muted) },
        confirmButton = {
            TextButton(onClick = model::confirmPrivacyAction) {
                Text(
                    stringResource(if (consent) R.string.confirm_sync_accept else R.string.action_confirm),
                    color = if (consent) Accent else SoftCrimson,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { model.privacyAction = null }) { Text(stringResource(R.string.action_cancel), color = Muted) }
        },
    )
}
