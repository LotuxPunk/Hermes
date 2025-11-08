package com.vandeas.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vandeas.desktop.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AppViewModel) {
    var showNewFileDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hermes Desktop - SSH Config Manager") },
                actions = {
                    Text(
                        text = viewModel.connectionStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    
                    if (viewModel.isConnected) {
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(Icons.Default.Close, "Disconnect")
                        }
                    } else {
                        Button(onClick = { viewModel.openConnectionDialog() }) {
                            Icon(Icons.Default.Settings, "Connect", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Connect")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (viewModel.isConnected) {
                // Tab row for switching between Templates, Mail Configs, and Contact Form Configs
                TabRow(selectedTabIndex = viewModel.activeTab.ordinal) {
                    Tab(
                        selected = viewModel.activeTab == AppViewModel.TabType.TEMPLATES,
                        onClick = { viewModel.selectTab(AppViewModel.TabType.TEMPLATES) },
                        text = { Text("Templates") }
                    )
                    Tab(
                        selected = viewModel.activeTab == AppViewModel.TabType.MAIL_CONFIGS,
                        onClick = { viewModel.selectTab(AppViewModel.TabType.MAIL_CONFIGS) },
                        text = { Text("Mail Configs") }
                    )
                    Tab(
                        selected = viewModel.activeTab == AppViewModel.TabType.CONTACT_FORM_CONFIGS,
                        onClick = { viewModel.selectTab(AppViewModel.TabType.CONTACT_FORM_CONFIGS) },
                        text = { Text("Contact Form Configs") }
                    )
                }
                
                // Main content
                Row(modifier = Modifier.fillMaxSize()) {
                    // File browser (left side)
                    FileBrowser(
                        viewModel = viewModel,
                        onCreateFile = { showNewFileDialog = true },
                        modifier = Modifier.weight(0.3f).fillMaxHeight()
                    )
                    
                    // File editor (right side)
                    FileEditor(
                        viewModel = viewModel,
                        modifier = Modifier.weight(0.7f).fillMaxHeight()
                    )
                }
            } else {
                // Welcome screen when not connected
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Connect to SSH Server",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Click the Connect button to manage your mail templates and configs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Connection dialog
        if (viewModel.showConnectionDialog) {
            ConnectionDialog(
                config = viewModel.config,
                onDismiss = { viewModel.closeConnectionDialog() },
                onConnect = { newConfig ->
                    viewModel.updateConfig(newConfig)
                    viewModel.connect()
                }
            )
        }
        
        // New file dialog
        if (showNewFileDialog) {
            NewFileDialog(
                onDismiss = { showNewFileDialog = false },
                onCreate = { fileName, content ->
                    viewModel.createFile(fileName, content)
                    showNewFileDialog = false
                }
            )
        }
        
        // Error snackbar
        viewModel.errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
        
        // Loading indicator
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
