package com.vandeas.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vandeas.desktop.model.SshConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDialog(
    config: SshConfig,
    onDismiss: () -> Unit,
    onConnect: (SshConfig) -> Unit
) {
    var host by remember { mutableStateOf(config.host) }
    var port by remember { mutableStateOf(config.port.toString()) }
    var username by remember { mutableStateOf(config.username) }
    var password by remember { mutableStateOf(config.password) }
    var templatesPath by remember { mutableStateOf(config.templatesPath) }
    var mailConfigsPath by remember { mutableStateOf(config.mailConfigsPath) }
    var contactFormConfigsPath by remember { mutableStateOf(config.contactFormConfigsPath) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SSH Connection Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                
                Divider()
                
                Text(
                    "Remote Paths",
                    style = MaterialTheme.typography.titleSmall
                )
                
                OutlinedTextField(
                    value = templatesPath,
                    onValueChange = { templatesPath = it },
                    label = { Text("Templates Path") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("/path/to/templates") }
                )
                
                OutlinedTextField(
                    value = mailConfigsPath,
                    onValueChange = { mailConfigsPath = it },
                    label = { Text("Mail Configs Path") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("/path/to/mail-configs") }
                )
                
                OutlinedTextField(
                    value = contactFormConfigsPath,
                    onValueChange = { contactFormConfigsPath = it },
                    label = { Text("Contact Form Configs Path") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("/path/to/contact-form-configs") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newConfig = SshConfig(
                        host = host,
                        port = port.toIntOrNull() ?: 22,
                        username = username,
                        password = password,
                        templatesPath = templatesPath,
                        mailConfigsPath = mailConfigsPath,
                        contactFormConfigsPath = contactFormConfigsPath
                    )
                    onConnect(newConfig)
                },
                enabled = host.isNotBlank() && username.isNotBlank()
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
