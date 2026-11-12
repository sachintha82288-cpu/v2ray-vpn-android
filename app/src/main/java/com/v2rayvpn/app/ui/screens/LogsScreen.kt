package com.v2rayvpn.app.ui.screens

import android.content.Intent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.v2rayvpn.app.core.log.LogLevel
import com.v2rayvpn.app.ui.AppViewModel
import com.v2rayvpn.app.ui.components.ConfirmDialog
import com.v2rayvpn.app.ui.components.EmptyState
import com.v2rayvpn.app.ui.components.GlassCard
import com.v2rayvpn.app.ui.components.OptionChip
import com.v2rayvpn.app.ui.components.OutlineButton
import com.v2rayvpn.app.ui.components.ScreenBackground
import com.v2rayvpn.app.ui.components.ScreenHeader
import com.v2rayvpn.app.ui.theme.VpnColors

@Composable
fun LogsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val entries by viewModel.logs.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var filter by remember { mutableStateOf<LogLevel?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(entries, filter) {
        if (filter == null) entries else entries.filter { it.level == filter }
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            ScreenHeader(
                title = "Logs & diagnostics",
                subtitle = "Credentials are redacted before storage",
                onBack = onBack
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionChip("All", filter == null) { filter = null }
                OptionChip("Info", filter == LogLevel.INFO) { filter = LogLevel.INFO }
                OptionChip("Warn", filter == LogLevel.WARN) { filter = LogLevel.WARN }
                OptionChip("Error", filter == LogLevel.ERROR) { filter = LogLevel.ERROR }
            }
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(text = "Copy", modifier = Modifier.weight(1f)) {
                    clipboard.setText(AnnotatedString(viewModel.logText()))
                    viewModel.postMessage("Log copied to the clipboard")
                }
                OutlineButton(text = "Share", modifier = Modifier.weight(1f)) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "V2RAY VPN diagnostics")
                        putExtra(Intent.EXTRA_TEXT, viewModel.logText())
                    }
                    context.startActivity(Intent.createChooser(intent, "Share diagnostics"))
                }
                OutlineButton(text = "Clear", modifier = Modifier.weight(1f)) { confirmClear = true }
            }
            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                EmptyState(
                    title = "No entries",
                    description = "Connection events, engine messages and errors appear here. Nothing is uploaded anywhere."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered.reversed()) { entry ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            padding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                        ) {
                            Text(
                                text = "${entry.level.name} · ${com.v2rayvpn.app.core.util.Formatters.timeShort(entry.timestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = when (entry.level) {
                                    LogLevel.ERROR -> VpnColors.Red
                                    LogLevel.WARN -> VpnColors.Amber
                                    LogLevel.INFO -> VpnColors.Cyan
                                    LogLevel.DEBUG -> VpnColors.TextTertiary
                                }
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = VpnColors.TextPrimary
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear the log?",
            body = "All entries in this session will be removed.",
            confirmLabel = "Clear",
            destructive = true,
            onDismiss = { confirmClear = false },
            onConfirm = { viewModel.clearLogs() }
        )
    }
}
