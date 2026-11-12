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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.v2rayvpn.app.domain.model.Profile
import com.v2rayvpn.app.ui.AppViewModel
import com.v2rayvpn.app.ui.components.ConfirmDialog
import com.v2rayvpn.app.ui.components.EmptyState
import com.v2rayvpn.app.ui.components.OptionChip
import com.v2rayvpn.app.ui.components.ProfileRow
import com.v2rayvpn.app.ui.components.QrDialog
import com.v2rayvpn.app.ui.components.ScreenBackground
import com.v2rayvpn.app.ui.components.ScreenHeader
import com.v2rayvpn.app.ui.components.TextInputDialog
import com.v2rayvpn.app.ui.theme.VpnColors

private enum class SortMode(val label: String) { NAME("Name"), LATENCY("Latency"), RECENT("Recent") }

@Composable
fun ServersScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onImport: () -> Unit
) {
    val profiles by viewModel.profiles.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val testing by viewModel.testing.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.NAME) }
    var renameTarget by remember { mutableStateOf<Profile?>(null) }
    var deleteTarget by remember { mutableStateOf<Profile?>(null) }
    var qrTarget by remember { mutableStateOf<Profile?>(null) }

    val filtered = remember(profiles, query, favoritesOnly, sortMode) {
        profiles
            .filter { !favoritesOnly || it.favorite }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, true) ||
                    it.server.contains(query, true) ||
                    it.protocol.label.contains(query, true)
            }
            .sortedWith(
                when (sortMode) {
                    SortMode.NAME -> compareBy<Profile> { it.name.lowercase() }
                    SortMode.LATENCY -> compareBy(nullsLast()) { it.lastLatencyMs }
                    SortMode.RECENT -> compareByDescending { it.lastUsedAt ?: 0L }
                }
            )
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            ScreenHeader(
                title = "Servers",
                subtitle = "${profiles.size} configuration${if (profiles.size == 1) "" else "s"} on this device",
                onBack = onBack,
                action = {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Filled.Add, contentDescription = "Import a configuration", tint = VpnColors.Purple)
                    }
                }
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search name, host or protocol") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionChip("Favourites", favoritesOnly) { favoritesOnly = !favoritesOnly }
                OptionChip("Test all", false) { viewModel.testAll() }
                OptionChip("Use fastest", false) {
                    val fastest = profiles.filter { it.lastLatencyMs != null }
                        .minByOrNull { it.lastLatencyMs ?: Long.MAX_VALUE }
                    if (fastest == null) {
                        viewModel.postMessage("Test servers first to rank them")
                    } else {
                        viewModel.selectProfile(fastest)
                    }
                }
                OptionChip("Sort: ${sortMode.label}", true) {
                    sortMode = when (sortMode) {
                        SortMode.NAME -> SortMode.LATENCY
                        SortMode.LATENCY -> SortMode.RECENT
                        SortMode.RECENT -> SortMode.NAME
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                EmptyState(
                    title = if (profiles.isEmpty()) "No configurations yet" else "Nothing matches",
                    description = if (profiles.isEmpty()) {
                        "Import a share link, JSON configuration, QR code or subscription URL. This app does not ship with servers."
                    } else {
                        "Try a different search term or turn off the favourites filter."
                    },
                    actionLabel = if (profiles.isEmpty()) "Import" else null,
                    onAction = if (profiles.isEmpty()) onImport else null
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { profile ->
                        ProfileRow(
                            profile = profile,
                            selected = settings.selectedProfileId == profile.id,
                            testing = testing.contains(profile.id),
                            onSelect = { viewModel.selectProfile(profile) },
                            onTest = { viewModel.testLatency(profile) },
                            onFavorite = { viewModel.toggleFavorite(profile) },
                            onRename = { renameTarget = profile },
                            onDuplicate = { viewModel.duplicateProfile(profile) },
                            onShare = {
                                clipboard.setText(AnnotatedString(viewModel.shareUri(profile)))
                                viewModel.postMessage("Share link copied to the clipboard")
                            },
                            onQr = { qrTarget = profile },
                            onDelete = { deleteTarget = profile }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    renameTarget?.let { profile ->
        TextInputDialog(
            title = "Rename configuration",
            label = "Name",
            initialValue = profile.name,
            onDismiss = { renameTarget = null },
            onConfirm = {
                viewModel.renameProfile(profile, it)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { profile ->
        ConfirmDialog(
            title = "Delete configuration?",
            body = "\"${profile.name}\" will be removed from this device. This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleteTarget = null },
            onConfirm = { viewModel.deleteProfile(profile) }
        )
    }

    qrTarget?.let { profile ->
        QrDialog(
            text = viewModel.shareUri(profile),
            title = profile.name,
            onDismiss = { qrTarget = null }
        )
    }
}
