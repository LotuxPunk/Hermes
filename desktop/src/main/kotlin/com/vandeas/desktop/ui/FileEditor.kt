package com.vandeas.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vandeas.desktop.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditor(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val selectedFile = viewModel.selectedFile
    var editedContent by remember { mutableStateOf("") }
    
    // Update edited content when file changes
    LaunchedEffect(viewModel.fileContent) {
        editedContent = viewModel.fileContent
    }
    
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface
    ) {
        if (selectedFile != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedFile.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = selectedFile.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Button(
                        onClick = { viewModel.saveFile(editedContent) },
                        enabled = editedContent != viewModel.fileContent
                    ) {
                        Icon(Icons.Default.Save, "Save", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save")
                    }
                }

                HorizontalDivider()

                // Editor
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = { Text("File content...") }
                )
            }
        } else {
            // No file selected
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a file to edit",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
