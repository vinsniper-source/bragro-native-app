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
import com.bragro.mobile.BuildConfig
import com.bragro.mobile.ui.theme.appFieldColors
import com.bragro.mobile.R
import com.bragro.mobile.data.repo.AuthRepository
import com.bragro.mobile.data.repo.LoginResult
import com.bragro.mobile.ui.util.openInCustomTab
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
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(24.dp)),
        verticalArrangement = Arrangement.Center,
        // Logo centralizada -- pedido do usuário ("centralise a logo"),
        // mesmo layout do login do site (login/page.tsx: "items-center
        // text-center").
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Logo nova (logo_bragro) -- pedido do usuário repetiu ("aumente
        // mais o tamanho da logo, login e início"). Tamanho aumentado de
        // 96dp -> 140dp -> 168dp -> 200dp.
        Image(
            painter = painterResource(R.drawable.logo_bragro),
            contentDescription = "BRAgro",
            modifier = Modifier.height(200.dp),
        )
        // Slogan abaixo da logo -- pedido do usuário ("coloque o slogan
        // abaixo da logo"), mesmo texto/estilo do login do site (itálico,
        // negrito, cor primária).
        Text(
            "Conectando a força da nossa terra, carregando o Brasil no coração.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            // BrGreen (fixo) -> colorScheme.primary (adapta por tema) --
            // pedido do usuário ("coloque as cores das fontes preto/branco
            // modo claro/escuro"): BrGreen cru era escuro demais e ficava
            // quase ilegível sobre o fundo quase-preto do modo Escuro.
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "Entre com sua conta para continuar",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = appFieldColors(),
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
            colors = appFieldColors(),
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

        // "Esqueci minha senha" -- embaixo, depois dos campos e do botão
        // Entrar, pedido do usuário ("coloque embaixo depois dos campos
        // esqueceu a senha"), mesma posição do login do site (login-form.tsx:
        // logo após o botão de submit). O app nativo não tem tela própria de
        // recuperação de senha -- abre a mesma página do site.
        androidx.compose.material3.TextButton(
            onClick = { openInCustomTab(context, "${BuildConfig.API_BASE_URL}/esqueci-senha") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Esqueci minha senha", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }

        // "Criar conta gratuita" -- pedido do usuário ("replique a mesma
        // página de login da plataforma no native"): faltava esse link
        // (site tinha, login-form.tsx). Sem tela de cadastro própria no app
        // (self-signup completo -- CPF/CNPJ, onboarding de organização --
        // não compensa reconstruir em Compose) -- abre a mesma página do
        // site /cadastro numa Custom Tab, mesmo padrão já usado acima pra
        // "Esqueci minha senha". O botão "Entrar com o Google" do site foi
        // REMOVIDO (não configurado no Supabase/Google Cloud, sem como
        // consertar por código) -- nunca existiu aqui no native, então não
        // há nada a remover deste lado.
        androidx.compose.material3.TextButton(
            onClick = { openInCustomTab(context, "${BuildConfig.API_BASE_URL}/cadastro") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Ainda não tem conta? Criar conta gratuita", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
        Text(
            "Precisa de internet na primeira vez. Depois de logar, o app continua funcionando sem conexao.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
