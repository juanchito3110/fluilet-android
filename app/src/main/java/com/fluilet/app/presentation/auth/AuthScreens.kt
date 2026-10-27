package com.fluilet.app.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.fluilet.app.presentation.common.SelectableChip
import com.fluilet.app.presentation.common.CheckMark
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fluilet.app.presentation.brand.FluiletFullLogo
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.R
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.presentation.common.ErrorMessage
import com.fluilet.app.presentation.common.PrimaryButton

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    onDone: () -> Unit,
    isError: Boolean = false,
    supportingText: String? = null
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(if (visible) R.string.auth_hide_password else R.string.auth_show_password)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/** Pestañas de la pantalla de acceso. */
enum class AuthTab { LOGIN, REGISTER }

/**
 * Iniciar Sesión / Crear Cuenta: logo completo de Fluilet arriba, pestañas claras y el acceso rápido al
 * Modo Demo. [onClose] solo existe al abrirla desde "✨ Ver Intro / Cuenta" (vista previa sin cerrar sesión).
 */
@Composable
fun AuthScreen(
    initialTab: AuthTab = AuthTab.LOGIN,
    onClose: (() -> Unit)? = null,
    loginViewModel: LoginViewModel = hiltViewModel(),
    registerViewModel: RegisterViewModel = hiltViewModel(),
    demoViewModel: DemoModeViewModel = hiltViewModel()
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val demo by demoViewModel.state.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (onClose != null) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.auth_back_to_panel)) }
            }
            FluiletFullLogo(Modifier.fillMaxWidth().padding(top = 8.dp))
            Text(stringResource(R.string.auth_login_subtitle), style = MaterialTheme.typography.bodyLarge)

            TabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == AuthTab.LOGIN, onClick = { tab = AuthTab.LOGIN }, text = { Text(stringResource(R.string.auth_tab_login), style = MaterialTheme.typography.titleSmall) })
                Tab(selected = tab == AuthTab.REGISTER, onClick = { tab = AuthTab.REGISTER }, text = { Text(stringResource(R.string.auth_tab_register), style = MaterialTheme.typography.titleSmall) })
            }

            when (tab) {
                AuthTab.LOGIN -> LoginForm(loginViewModel)
                AuthTab.REGISTER -> RegisterForm(registerViewModel)
            }

            if (demo.enabled) {
                OutlinedButton(
                    onClick = demoViewModel::enter,
                    enabled = !demo.isLoading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        if (demo.isLoading) stringResource(R.string.auth_demo_loading) else stringResource(R.string.auth_demo),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                ErrorMessage(demo.errorMessage)
            }
        }
    }
}

@Composable
private fun LoginForm(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmail,
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(state.password, viewModel::onPassword, stringResource(R.string.auth_password), ImeAction.Done, viewModel::submit)
        ErrorMessage(state.errorMessage)
        PrimaryButton(stringResource(R.string.auth_login), viewModel::submit, enabled = state.canSubmit, loading = state.isLoading)
    }
}

@Composable
private fun RegisterForm(viewModel: RegisterViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.auth_register_subtitle), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.fullName,
            onValueChange = viewModel::onName,
            label = { Text(stringResource(R.string.auth_full_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmail,
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(
            value = state.password,
            onChange = viewModel::onPassword,
            label = stringResource(R.string.auth_password),
            imeAction = ImeAction.Done,
            onDone = viewModel::submit,
            isError = state.passwordHint != null,
            supportingText = state.passwordHint ?: stringResource(R.string.auth_password_rules)
        )

        Text(stringResource(R.string.auth_role_label), style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RoleChip(UserRole.TUTOR, R.string.auth_role_tutor, state.role, viewModel::onRole)
            RoleChip(UserRole.ADULTO_INDEPENDIENTE, R.string.auth_role_adult, state.role, viewModel::onRole)
        }
        ErrorMessage(state.errorMessage)
        PrimaryButton(stringResource(R.string.auth_register), viewModel::submit, enabled = state.canSubmit, loading = state.isLoading)
    }
}

@Composable
private fun RoleChip(role: UserRole, label: Int, selected: UserRole, onSelect: (UserRole) -> Unit) {
    SelectableChip(
        selected = role == selected,
        onClick = { onSelect(role) },
        label = { Text(stringResource(label), style = MaterialTheme.typography.bodyMedium) },
        modifier = Modifier.fillMaxWidth()
    )
}
