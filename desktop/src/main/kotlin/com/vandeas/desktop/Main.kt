package com.vandeas.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.vandeas.desktop.ui.MainScreen
import com.vandeas.desktop.viewmodel.AppViewModel

@Composable
@Preview
fun App(viewModel: AppViewModel) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainScreen(viewModel)
        }
    }
}

fun main() = application {
    val viewModel = remember { AppViewModel() }
    
    Window(
        onCloseRequest = {
            viewModel.disconnect()
            exitApplication()
        },
        title = "Hermes Desktop - SSH Config Manager"
    ) {
        App(viewModel)
    }
}
