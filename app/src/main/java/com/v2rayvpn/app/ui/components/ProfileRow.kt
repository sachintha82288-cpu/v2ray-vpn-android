package com.v2rayvpn.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2rayvpn.app.core.util.Formatters
import com.v2rayvpn.app.domain.model.Profile
import com.v2rayvpn.app.ui.theme.VpnColors

@Composable
fun ProfileRow(
    profile: Profile,
    selected: Boolean,
    testing: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onQr: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        padding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        onClick = onSelect
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        StatusDot(VpnColors.Green)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = VpnColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(Modifier.size(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(profile.protocol.label, VpnColors.Purple)
                    if (profile.transport != com.v2rayvpn.app.domain.model.Transport.TCP) {
                        Pill(profile.transport.label, VpnColors.Blue)
                    }
                    if (profile.security != com.v2rayvpn.app.domain.model.SecurityType.NONE) {
                        Pill(profile.security.label, VpnColors.Cyan)
                    }
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    text = profile.endpoint,
                    style = MaterialTheme.typography.bodySmall,
                    color = VpnColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        testing -> "Testing…"
                        profile.lastLatencyMs != null ->
                            "${Formatters.latency(profile.lastLatencyMs)} · ${profile.lastTestType ?: "test"} · ${
                                Formatters.time(profile.lastTestAt ?: 0)
                            }"
                        else -> "Not tested yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        testing -> VpnColors.TextSecondary
                        profile.lastLatencyMs != null -> VpnColors.Green
                        else -> VpnColors.TextTertiary
                    }
                )
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = if (profile.favorite) "Remove from favourites" else "Add to favourites",
                    tint = if (profile.favorite) VpnColors.Amber else VpnColors.TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More actions",
                        tint = VpnColors.TextSecondary
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Test latency") }, onClick = {
                        menuOpen = false
                        onTest()
                    })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        menuOpen = false
                        onRename()
                    })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                        menuOpen = false
                        onDuplicate()
                    })
                    DropdownMenuItem(text = { Text("Copy share link") }, onClick = {
                        menuOpen = false
                        onShare()
                    })
                    DropdownMenuItem(text = { Text("Show QR code") }, onClick = {
                        menuOpen = false
                        onQr()
                    })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                        menuOpen = false
                        onDelete()
                    })
                }
            }
        }
    }
}
