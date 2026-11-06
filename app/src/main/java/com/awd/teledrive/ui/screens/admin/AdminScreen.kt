package com.awd.teledrive.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.awd.teledrive.core.Config
import com.awd.teledrive.domain.model.DriveProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    var unlocked by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    val disabled by viewModel.disabledProviders.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val noticeEnabled by viewModel.noticeEnabled.collectAsState()
    val consent by viewModel.consent.collectAsState()
    val audit by viewModel.audit.collectAsState()

    var noticeDraft by remember(notice) { mutableStateOf(notice) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin panel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!unlocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                Text("Owner / admin access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Enter the admin PIN to manage this device's settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8); pinError = false },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = pinError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (pinError) {
                    Text(
                        "Wrong PIN",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (pin == Config.ADMIN_PIN) {
                            viewModel.logAdminLogin()
                            unlocked = true
                        } else pinError = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Unlock") }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                SectionTitle("Providers on this device")
                Text(
                    "Turning a provider off only hides it in Multi Drive on this device. No data is touched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                DriveProvider.entries.forEach { provider ->
                    val enabled = provider.key !in disabled
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(provider.displayName, modifier = Modifier.weight(1f))
                        Switch(
                            checked = enabled,
                            onCheckedChange = { viewModel.setProviderEnabled(provider.key, it) }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }

                Spacer(Modifier.height(22.dp))
                SectionTitle("Broadcast notice")
                OutlinedTextField(
                    value = noticeDraft,
                    onValueChange = { noticeDraft = it },
                    label = { Text("Message shown on the Multi Drive tab") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = noticeEnabled,
                        onCheckedChange = { viewModel.setNotice(noticeDraft, it) }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Show notice", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.setNotice(noticeDraft, true) }) { Text("Save") }
                }

                Spacer(Modifier.height(22.dp))
                SectionTitle("Support access (consent)")
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "This device has " + (if (consent) "agreed" else "not agreed") +
                                " to share diagnostics with support.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The user sees this switch too and can turn it off whenever they want. " +
                                "Only a support bundle (logs, device info) is shared — never documents or photos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Audit log", modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearAudit) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
                Text(
                    "Every admin action and sign-in is recorded locally and visible to the user.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (audit.isEmpty()) {
                    Text("No entries yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    audit.take(60).forEach { entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.action, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    viewModel.format(entry),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 6.dp)
    )
}
