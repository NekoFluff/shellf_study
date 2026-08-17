package com.crazyfluff.shellfstudy.shared.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.crazyfluff.shellfstudy.shared.designsystem.rememberNotificationPermissionRequest
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

private const val PERSONAL_ACCESS_TOKENS_URL = "https://www.wanikani.com/settings/personal_access_tokens"

object AuthScreenTestTags {
    const val TOKEN_FIELD = "auth_token_field"
    const val SUBMIT_BUTTON = "auth_submit_button"
    const val ERROR_TEXT = "auth_error_text"
    const val LOADING_INDICATOR = "auth_loading_indicator"
    const val TOKEN_LINK = "auth_token_link"
}

@Composable
fun AuthRoute(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val requestPermission = rememberNotificationPermissionRequest { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }
    LaunchedEffect(uiState.pendingNotificationRequest) {
        if (uiState.pendingNotificationRequest) requestPermission()
    }
    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) onAuthenticated()
    }

    AuthScreen(
        uiState = uiState,
        onTokenInputChange = viewModel::onTokenInputChange,
        onSubmit = viewModel::submitToken
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onTokenInputChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Shellf Study",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter your WaniKani API token to get started. You can find or create one at:",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "wanikani.com/settings/personal_access_tokens",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                ),
                modifier = Modifier
                    .testTag(AuthScreenTestTags.TOKEN_LINK)
                    .clickable { uriHandler.openUri(PERSONAL_ACCESS_TOKENS_URL) }
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = uiState.tokenInput,
                onValueChange = onTokenInputChange,
                label = { Text("WaniKani API token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                isError = uiState.errorMessage != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AuthScreenTestTags.TOKEN_FIELD)
            )

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(AuthScreenTestTags.ERROR_TEXT)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSubmit,
                enabled = !uiState.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AuthScreenTestTags.SUBMIT_BUTTON)
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .testTag(AuthScreenTestTags.LOADING_INDICATOR),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Log in")
                }
            }
        }
    }
}
