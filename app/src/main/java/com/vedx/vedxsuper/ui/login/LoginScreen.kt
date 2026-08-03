package com.vedx.vedxsuper.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val savedCreds = viewModel.getSavedCredentials()
    
    var clientId by remember { mutableStateOf(savedCreds["client_id"] ?: "") }
    var password by remember { mutableStateOf(savedCreds["password"] ?: "") }
    var totpKey by remember { mutableStateOf(savedCreds["totp_key"] ?: "") }
    var apiKey by remember { mutableStateOf(savedCreds["api_key"] ?: "") }

    val uiState by viewModel.uiState.collectAsState()

    // Auto-login trigger if all credentials exist and we are in Idle state
    LaunchedEffect(Unit) {
        if (clientId.isNotBlank() && password.isNotBlank() && totpKey.isNotBlank() && apiKey.isNotBlank() && uiState is LoginViewModel.LoginUiState.Idle) {
            viewModel.login(clientId, password, totpKey, apiKey)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is LoginViewModel.LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "VedxSuper Login", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = clientId,
            onValueChange = { clientId = it },
            label = { Text("Client ID") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = totpKey,
            onValueChange = { totpKey = it },
            label = { Text("TOTP Key") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (uiState is LoginViewModel.LoginUiState.Loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { viewModel.login(clientId, password, totpKey, apiKey) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login")
            }
        }

        if (uiState is LoginViewModel.LoginUiState.Error) {
            Text(
                text = (uiState as LoginViewModel.LoginUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
