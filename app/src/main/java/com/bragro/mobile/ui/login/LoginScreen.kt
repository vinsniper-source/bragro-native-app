package com.bragro.mobile.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.R
import com.bragro.mobile.data.repo.AuthRepository
import com.bragro.mobile.data.repo.LoginResult
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import android.app.Application
import androidx.lifecycle.AndroidViewModel

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val authRepository = AuthRepository(app)

    var loading = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            errorMessage.value = "Preencha e-mail e senha."
            return
        }
        loading.value = true
        errorMessage.value = null
        viewModelScope.launch {
            when (val result = authRepository.login(email.trim(), password)) {
                is LoginResult.Success -> onSuccess()
                is LoginResult.Failure -> errorMessage.value = result.message
            }
            loading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, viewModel: LoginViewModel = viewModel()) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val loading by viewModel.loading
    val error by viewModel.errorMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(24.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        // Logo BRAgro do lado esquerdo -- pedido do usuário ("coloque a logo
        // BRAgro do lado esquerdo tanto no início como em login"), mesma
        // imagem usada no cabeçalho do Início (ver HomeScreen.kt).
        Image(
            painter = painterResource(R.drawable.logo_oficial_header),
            contentDescription = "BRAgro",
            modifier = Modifier.height(56.dp),
        )
        Text("Entre com sua conta", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        if (error != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
            Text(error!!, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 20.dp))
        Button(
            onClick = { viewModel.login(email, password, onLoggedIn) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
            } else {
                Text("Entrar")
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
        Text(
            "Precisa de internet na primeira vez. Depois de logar, o app continua funcionando sem conexao.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
