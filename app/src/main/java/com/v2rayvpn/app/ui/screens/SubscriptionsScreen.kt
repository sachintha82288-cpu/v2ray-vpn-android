package com.v2rayvpn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2rayvpn.app.core.util.Formatters
import com.v2rayvpn.app.domain.model.Subscription
import com.v2rayvpn.app.ui.AppViewModel
import com.v2rayvpn.app.ui.components.ConfirmDialog
import com.v2rayvpn.app.ui.components.EmptyState
import com.v2rayvpn.app.ui.components.GlassCard
import com.v2rayvpn.app.ui.components.InfoBanner
import com.v2rayvpn.app.ui.components.OutlineButton
import com.v2rayvpn.app.ui.components.ScreenBackground
import com.v2rayvpn.app.ui.components.ScreenHeader
import com.v2rayvpn.app.ui.theme.VpnColors

@Composable
fun SubscriptionsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val busy by viewModel.busy.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Subscription?>(null) }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            ScreenHeader(
                title = "Subscriptions",
                subtitle = "Automatic refresh every ${settings.subscriptionAutoRefreshHours}h when set",
                onBack = onBack,
                action = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add subscription", tint = VpnColors.Purple)
                    }
                }
            )
            Spacer(Modifier.height(12.dp))

            InfoBanner(
                text = "A subscription URL usually contains a private token. It is stored encrypted and never written to logs. Refreshing downloads the list from that server, so only add URLs you trust.",
                tone = VpnColors.Blue
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(
                    text = if (busy) "Refreshing…" else "Refresh all",
                    modifier = Modifier.weight(1f),
                    enabled = !busy && subscriptions.isNotEmpty()
                ) { viewModel.refreshAllSubscriptions() }
                OutlineButton(
                    text = "Auto-refresh: ${settings.subscriptionAutoRefreshHours}h",
                    modifier = Modifier.weight(1f)
                ) {
                    val next = when (settings.subscriptionAutoRefreshHours) {
                        0 -> 6
                        6 -> 12
                        12 -> 24
                        else -> 0
                    }
                    viewModel.updateSettings { it.copy(subscriptionAutoRefreshHours = next) }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (subscriptions.isEmpty()) {
                EmptyState(
                    title = "No subscriptions",
                    description = "Add a subscription URL to keep a provider's configuration list up to date.",
                    actionLabel = "Add subscription",
                    onAction = { showAdd = true }
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(subscriptions, key = { it.id }) { subscription ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = subscription.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = VpnColors.TextPrimary
                                    )
                                    Text(
                                        text = maskUrl(subscription.url),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VpnColors.TextTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { viewModel.refreshSubscription(subscription.id) }) {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = "Refresh ${subscription.name}",
                                        tint = VpnColors.TextSecondary
                                    )
                                }
                                IconButton(onClick = { deleteTarget = subscription }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete ${subscription.name}",
                                        tint = VpnColors.TextSecondary
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = buildString {
                                    append("${subscription.profileCount} configurations")
                                    subscription.lastUpdated?.let { append(" · updated ${Formatters.time(it)}") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = VpnColors.TextSecondary
                            )
                            subscription.lastError?.let { error ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Last refresh failed: $error (previous configurations kept)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VpnColors.Amber
                                )
                            }
                            val total = subscription.totalBytes
                            val used = (subscription.uploadBytes ?: 0L) + (subscription.downloadBytes ?: 0L)
                            if (total != null && total > 0) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Traffic: ${Formatters.bytes(used)} of ${Formatters.bytes(total)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VpnColors.TextSecondary
                                )
                            }
                            subscription.expiryEpochSeconds?.let { expiry ->
                                if (expiry > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Expires: ${Formatters.time(expiry * 1000)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VpnColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (showAdd) {
        AddSubscriptionDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, url, hours ->
                viewModel.addSubscription(name, url, hours)
                showAdd = false
            }
        )
    }

    deleteTarget?.let { subscription ->
        ConfirmDialog(
            title = "Remove subscription?",
            body = "\"${subscription.name}\" and the ${subscription.profileCount} configurations it imported will be deleted.",
            confirmLabel = "Remove",
            destructive = true,
            onDismiss = { deleteTarget = null },
            onConfirm = { viewModel.deleteSubscription(subscription) }
        )
    }
}

@Composable
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add subscription", color = VpnColors.TextPrimary) },
        text = {
            Column {
                Text(
                    text = "The app will contact this URL now and download your configuration list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VpnColors.TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Auto-refresh hours (0 = off)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, url, hours.toIntOrNull() ?: 0) },
                enabled = url.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = VpnColors.SurfaceElevated
    )
}

private fun maskUrl(url: String): String {
    val scheme = url.substringBefore("://", "")
    val rest = url.substringAfter("://", url)
    val host = rest.substringBefore('/').substringBefore('?')
    return if (scheme.isEmpty()) "•••" else "$scheme://$host/…"
}
