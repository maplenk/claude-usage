package com.qbapps.claudeusage.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Password-style input field for the Claude session key with
 * visibility toggle, clipboard paste, and validate button.
 */
@Composable
fun SessionKeyInput(
    value: String,
    onValueChange: (String) -> Unit,
    onValidate: () -> Unit,
    isValidating: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Session Key") },
            placeholder = { Text("sk-ant-...") },
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        clipboardManager.getText()?.text?.let { pasted ->
                            onValueChange(pasted)
                        }
                    }) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = "Paste from clipboard",
                        )
                    }
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        val icon = if (passwordVisible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        }
                        Icon(icon, contentDescription = "Toggle password visibility")
                    }
                }
            },
            isError = errorMessage != null,
            supportingText = if (errorMessage != null) {
                { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
            } else {
                null
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onValidate,
            enabled = value.isNotBlank() && !isValidating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isValidating) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(20.dp)
                        .width(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Validating...")
            } else {
                Text("Validate Key")
            }
        }
    }
}
