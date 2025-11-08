package com.vandeas.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vandeas.desktop.ssh.FileInfo
import com.vandeas.desktop.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowser(
    viewModel: AppViewModel,
    onCreateFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateUp() },
                    enabled = viewModel.currentPath != ""
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go up")
                }
                
                IconButton(onClick = { viewModel.loadFiles(viewModel.currentPath) }) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
                
                IconButton(onClick = onCreateFile) {
                    Icon(Icons.Default.Add, "New file")
                }
            }
            
            HorizontalDivider()

            // Current path
            if (viewModel.currentPath != "") {
                Text(
                    text = viewModel.currentPath,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
                HorizontalDivider()
            }
            
            // File list
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(viewModel.files) { file ->
                    FileItem(
                        file = file,
                        isSelected = viewModel.selectedFile == file,
                        onClick = { viewModel.selectFile(file) },
                        onDelete = { viewModel.deleteFile(file) }
                    )
                }
            }
        }
    }
}

@Composable
fun FileItem(
    file: FileInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (!file.isDirectory) {
                    Text(
                        text = "${file.size} bytes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (!file.isDirectory) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete File") },
            text = { Text("Are you sure you want to delete ${file.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
