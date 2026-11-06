package com.awd.teledrive.ui.screens.multidrive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.awd.teledrive.domain.model.DriveProvider
import com.awd.teledrive.domain.model.ProviderState
import com.awd.teledrive.domain.model.ProviderStatus

/**
 * MULTI DRIVE tab — one place for every storage backend.
 *
 * Status of each provider is honest: "Connected" only when the integration really works,
 * "Unavailable" when the provider's API does not allow it (Google Photos, WhatsApp), and
 * "Not connected" when the integration exists but the user still has to sign in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiDriveScreen(
    onOpenAccounts: () -> Unit,
    onOpenGoogleDrive: () -> Unit,
    onOpenAdmin: () -> Unit,
    viewModel: MultiDriveViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var detailsFor by remember { mutableStateOf<ProviderStatus?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Multi Drive") },
                actions = {
                    TextButton(onClick = onOpenAdmin) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin", modifier = Modifier.size(20.dp))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "All your clouds in one place",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Browse Telegram, Google Drive and more from a single list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            state.notice?.let { notice ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(notice, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                Card(
                    onClick = onOpenAccounts,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Accounts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Active: ${state.activeTelegramLabel} · ${state.telegramAccounts} Telegram slot(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("Switch", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            items(state.providers) { status ->
                ProviderCard(
                    status = status,
                    onClick = {
                        when {
                            status.provider == DriveProvider.GOOGLE_DRIVE -> onOpenGoogleDrive()
                            status.provider == DriveProvider.TELEGRAM -> detailsFor = status
                            else -> detailsFor = status
                        }
                    }
                )
            }

            item {
                Text(
                    "Google Photos and WhatsApp cannot be browsed by a third-party app — tap them to see exactly why.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 26.dp)
                )
            }
        }
    }

    detailsFor?.let { status ->
        AlertDialog(
            onDismissRequest = { detailsFor = null },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text(status.provider.displayName) },
            text = {
                Column {
                    Text(status.provider.tagline, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "What this needs",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(status.provider.requirement, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { detailsFor = null }) { Text("Close") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderCard(status: ProviderStatus, onClick: () -> Unit) {
    val (accent, icon) = providerVisual(status.provider)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.65f))), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = status.provider.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    StateChip(status.state)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = status.accountLabel ?: status.provider.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StateChip(state: ProviderState) {
    val label = when (state) {
        ProviderState.CONNECTED -> "Connected"
        ProviderState.NOT_CONNECTED -> "Not connected"
        ProviderState.UNAVAILABLE -> "Unavailable"
    }
    val color = when (state) {
        ProviderState.CONNECTED -> Color(0xFF34D399)
        ProviderState.NOT_CONNECTED -> MaterialTheme.colorScheme.primary
        ProviderState.UNAVAILABLE -> Color(0xFFF59E0B)
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label, fontSize = 10.sp) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = color,
            disabledContainerColor = color.copy(alpha = 0.14f)
        )
    )
}

private fun providerVisual(provider: DriveProvider): Pair<Color, ImageVector> = when (provider) {
    DriveProvider.TELEGRAM -> Color(0xFF2AABEE) to Icons.Default.CloudDone
    DriveProvider.ONEDRIVE -> Color(0xFF0078D4) to Icons.Default.Cloud
    DriveProvider.GOOGLE_DRIVE -> Color(0xFF0F9D58) to Icons.Default.Storage
    DriveProvider.GOOGLE_PHOTOS -> Color(0xFFEA4335) to Icons.Default.PhotoLibrary
    DriveProvider.MEGA -> Color(0xFFD9272E) to Icons.Default.Lock
    DriveProvider.WHATSAPP -> Color(0xFF25D366) to Icons.Default.Cloud
}
