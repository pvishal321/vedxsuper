package com.vedx.vedxsuper.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.vedx.vedxsuper.auth.BiometricAuthManager

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    biometricAuthManager: BiometricAuthManager,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var clientCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    var showBiometricPrompt by remember { mutableStateOf(false) }

    val activity = LocalContext.current as FragmentActivity

    // Auto-login effect
    LaunchedEffect(Unit) {
        if (uiState.autoLoginEnabled && !uiState.isLoggedIn) {
            if (biometricAuthManager.isBiometricEnabled()) {
                showBiometricPrompt = true
            } else {
                viewModel.attemptAutoLogin()
            }
        }
    }

    // Success effect
    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            onLoginSuccess()
        }
    }

    // Biometric prompt
    if (showBiometricPrompt) {
        LaunchedEffect(Unit) {
            biometricAuthManager.authenticate(
                activity = activity,
                onSuccess = { viewModel.attemptAutoLogin() },
                onError = { 
                    showBiometricPrompt = false
                    // Stay on login screen
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "VedxSuper",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Secure Login",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Client Code
        OutlinedTextField(
            value = clientCode,
            onValueChange = { clientCode = it.uppercase() },
            label = { Text("Client Code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // TOTP
        OutlinedTextField(
            value = totp,
            onValueChange = { if (it.length <= 6) totp = it },
            label = { Text("TOTP (6 digits)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Auto-login toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = uiState.autoLoginEnabled,
                onCheckedChange = { viewModel.setAutoLoginEnabled(it) }
            )
            Text(
                text = "Enable Auto-Login",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Error
        uiState.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Login Button
        Button(
            onClick = { viewModel.login(clientCode, password, totp) },
            enabled = !uiState.isLoading && clientCode.isNotBlank() 
                      && password.isNotBlank() && totp.length == 6,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Login to Angel One", fontWeight = FontWeight.Bold)
            }
        }
    }
}
