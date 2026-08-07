package com.vedx.vedxsuper.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.vedx.vedxsuper.auth.AuthState

@Composable
fun LoginScreen(
    viewModel: LoginViewModelV2,
    biometricAuthManager: BiometricAuthManager,
    onLoginSuccess: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    
    var clientCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showBiometricPrompt by remember { mutableStateOf(false) }

    val activity = LocalContext.current as FragmentActivity

    // Prefill if tokens exist
    LaunchedEffect(Unit) {
        viewModel.getPrefillData()?.let { prefill ->
            if (clientCode.isBlank()) clientCode = prefill.clientCode
            if (apiKey.isBlank()) apiKey = prefill.apiKey
        }
    }

    LaunchedEffect(Unit) {
        if (authState is AuthState.RequiresLogin && (authState as AuthState.RequiresLogin).canRetry) {
            if (biometricAuthManager.isBiometricEnabled()) {
                showBiometricPrompt = true
            } else {
                viewModel.retryAuth()
            }
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            onLoginSuccess()
        }
    }

    if (showBiometricPrompt) {
        LaunchedEffect(Unit) {
            biometricAuthManager.authenticate(
                activity = activity,
                onSuccess = { viewModel.retryAuth() },
                onError = { showBiometricPrompt = false }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "VedxSuper Pro",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Institutional Auth V2",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = clientCode,
            onValueChange = { clientCode = it.uppercase() },
            label = { Text("Client Code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = totp,
            onValueChange = { totp = it },
            label = { Text("TOTP or Secret Key") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("SmartAPI Key") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.login(clientCode, password, totp, apiKey) },
            enabled = !isLoading && clientCode.isNotBlank() && password.isNotBlank() && totp.isNotBlank() && apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Secure Login", fontWeight = FontWeight.Bold)
            }
        }
    }
}
