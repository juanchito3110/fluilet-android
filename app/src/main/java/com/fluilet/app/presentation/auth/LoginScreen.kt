package com.fluilet.app.presentation.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.domain.model.UserRole
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LoginScreen(
    onLoginSuccess: (UserRole) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AuthEvent.LoginSuccess -> onLoginSuccess(event.role)
            }
        }
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Fluilet",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Lectura adaptativa e inclusiva",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChanged,
                        label = { Text("Correo electrónico") },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChanged,
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(visible = uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = { viewModel.onLoginClicked(UserRole.TUTOR) },
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Ingresar como Tutor", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { viewModel.onLoginClicked(UserRole.ADULTO_INDEPENDIENTE) },
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Ingresar como Estudiante", fontWeight = FontWeight.SemiBold)
                    }

                    AnimatedVisibility(visible = uiState.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(strokeWidth = 3.dp)
                        }
                    }
                }
            }
        }
    }
}