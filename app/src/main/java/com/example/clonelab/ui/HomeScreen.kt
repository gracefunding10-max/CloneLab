package com.example.clonelab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clonelab.data.CameraSourceType
import com.example.clonelab.data.ClonedApp
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberPink
import com.example.ui.theme.CyberRose
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: CloneLabViewModel,
    onNavigateToSettings: (ClonedApp) -> Unit,
    onNavigateToSandbox: (ClonedApp) -> Unit,
    modifier: Modifier = Modifier
) {
    val clones by viewModel.allClones.collectAsStateWithLifecycle()
    val securityAlert by viewModel.securityAlert.collectAsStateWithLifecycle()
    val ndkWarning by viewModel.ndkWarning.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberViolet),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CloneLab",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Text(
                                text = "Camera Redirect Module • v1.0 Production",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                ),
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_clone_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Clone",
                            tint = CyberCyan
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CyberViolet,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_clone")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Clone Container")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // NDK Warning Banner if present
            if (ndkWarning != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = CyberAmber.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyberAmber, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = CyberAmber,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "NDK Camera Interception Graceful Fallback",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CyberAmber,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = ndkWarning!!.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            IconButton(onClick = { viewModel.dismissNdkWarning() }) {
                                Text("✕", color = CyberAmber, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Security Denylist Alert Banner
            if (securityAlert != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = CyberRose.copy(alpha = 0.18f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyberRose, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = CyberRose,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Security Policy Refusal",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CyberRose,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = securityAlert!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            IconButton(onClick = { viewModel.dismissSecurityAlert() }) {
                                Text("✕", color = CyberRose, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Telemetry Overview
            item {
                StatusOverviewCard(clones = clones)
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Contained Applications (${clones.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Android 8–15 (arm64)",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )
                }
            }

            // Cloned Apps List
            items(clones, key = { it.id }) { clone ->
                ClonedAppItemCard(
                    clone = clone,
                    onToggleRedirect = { enabled ->
                        viewModel.toggleRedirect(clone, enabled)
                    },
                    onConfigure = {
                        viewModel.selectClone(clone)
                        onNavigateToSettings(clone)
                    },
                    onLaunchSandbox = {
                        viewModel.selectClone(clone)
                        onNavigateToSandbox(clone)
                    },
                    onDelete = {
                        viewModel.deleteClone(clone)
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddCloneDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, pkg ->
                viewModel.addCustomClone(name, pkg)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun StatusOverviewCard(clones: List<ClonedApp>) {
    val activeRedirects = clones.count { it.cameraRedirectEnabled && it.cameraSourceUri != null && !it.isKycBlocked }
    val passthroughCount = clones.count { !it.cameraRedirectEnabled || it.cameraSourceUri == null }
    val kycCount = clones.count { it.isKycBlocked }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "CONTAINER RUNTIME STATUS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusMetricItem(
                    label = "Redirected",
                    value = activeRedirects.toString(),
                    color = CyberGreen
                )
                StatusMetricItem(
                    label = "Passthrough",
                    value = passthroughCount.toString(),
                    color = CyberCyan
                )
                StatusMetricItem(
                    label = "KYC Gated",
                    value = kycCount.toString(),
                    color = CyberRose
                )
                StatusMetricItem(
                    label = "Hook Mode",
                    value = "Context",
                    color = CyberViolet
                )
            }
        }
    }
}

@Composable
fun StatusMetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ClonedAppItemCard(
    clone: ClonedApp,
    onToggleRedirect: (Boolean) -> Unit,
    onConfigure: () -> Unit,
    onLaunchSandbox: () -> Unit,
    onDelete: () -> Unit
) {
    val isRedirectActive = clone.cameraRedirectEnabled && clone.cameraSourceUri != null && !clone.isKycBlocked

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (clone.isKycBlocked) CyberRose.copy(alpha = 0.5f)
                else if (isRedirectActive) CyberCyan.copy(alpha = 0.4f)
                else DarkBorder,
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (clone.isKycBlocked) CyberRose.copy(alpha = 0.2f)
                            else if (isRedirectActive) CyberCyan.copy(alpha = 0.2f)
                            else CyberViolet.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (clone.isKycBlocked) Icons.Default.Security
                        else if (isRedirectActive) Icons.Default.Videocam
                        else Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = if (clone.isKycBlocked) CyberRose
                        else if (isRedirectActive) CyberCyan
                        else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = clone.appName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = clone.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                if (!clone.isKycBlocked) {
                    Switch(
                        checked = clone.cameraRedirectEnabled,
                        onCheckedChange = onToggleRedirect,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyberCyan,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.testTag("toggle_redirect_${clone.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val badgeText: String
                val badgeColor: Color
                if (clone.isKycBlocked) {
                    badgeText = "KYC PROTECTED • REDIRECTION REFUSED"
                    badgeColor = CyberRose
                } else if (isRedirectActive) {
                    val detail = if (clone.sourceType == CameraSourceType.GENERATED_PATTERN) {
                        clone.customPatternName
                    } else {
                        clone.sourceType.name
                    }
                    badgeText = "REDIRECTED: $detail @ ${clone.targetFps} FPS"
                    badgeColor = CyberGreen
                } else {
                    badgeText = "REAL CAMERA PASSTHROUGH (100% UNTOUCHED)"
                    badgeColor = CyberAmber
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLaunchSandbox,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("launch_sandbox_${clone.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (clone.isKycBlocked) DarkSurfaceVariant else CyberViolet
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (clone.isKycBlocked) "Audit Gate" else "Sandbox Live")
                }

                OutlinedButton(
                    onClick = onConfigure,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("configure_${clone.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configure",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Settings")
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_${clone.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun AddCloneDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Contained App", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Specify an APK package to run in CloneLab's virtual container.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("App Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_app_name_input")
                )
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it },
                    label = { Text("Package Name (e.g. com.app.camera)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_pkg_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && pkg.isNotBlank()) {
                        onConfirm(name.trim(), pkg.trim())
                    }
                },
                enabled = name.isNotBlank() && pkg.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_button")
            ) {
                Text("Add Clone")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = DarkSurface
    )
}
