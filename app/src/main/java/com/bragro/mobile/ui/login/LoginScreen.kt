package com.bragro.mobile.ui.login

import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowRight
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.bragro.mobile.ui.theme.LinhaTextFieldClassico as OutlinedTextField
import com.bragro.mobile.ui.i18n.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.text.input.VisualTransformation
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
    // v72b -- 2 etapas: senha conferida, falta o código do autenticador.
    var pendente = mutableStateOf<LoginResult.PrecisaCodigo?>(null)
        private set
    // v139 -- senha provisória: troca antes de entrar.
    var trocarSenha = mutableStateOf<LoginResult.TrocarSenha?>(null)
        private set

    fun salvarNovaSenha(email: String, nova: String, repetir: String, onSuccess: () -> Unit) {
        val p = trocarSenha.value ?: return
        if (nova.length < 8) { errorMessage.value = "A senha precisa ter pelo menos 8 caracteres."; return }
        if (nova != repetir) { errorMessage.value = "As duas senhas não são iguais."; return }
        loading.value = true
        errorMessage.value = null
        viewModelScope.launch {
            when (val r = authRepository.trocarSenha(email.trim(), nova, p)) {
                is LoginResult.Success -> { trocarSenha.value = null; onSuccess() }
                is LoginResult.Failure -> errorMessage.value = r.message
                else -> Unit
            }
            loading.value = false
        }
    }

    fun verificarCodigo(email: String, password: String, codigo: String, onSuccess: () -> Unit) {
        val p = pendente.value ?: return
        if (codigo.length != 6) {
            errorMessage.value = "Digite os 6 números do app autenticador."
            return
        }
        loading.value = true
        errorMessage.value = null
        viewModelScope.launch {
            when (val r = authRepository.verificarCodigo(email.trim(), password, p, codigo)) {
                is LoginResult.Success -> { pendente.value = null; onSuccess() }
                is LoginResult.Failure -> errorMessage.value = r.message
                is LoginResult.PrecisaCodigo -> Unit
                is LoginResult.TrocarSenha -> { pendente.value = null; trocarSenha.value = r }
            }
            loading.value = false
        }
    }

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
                is LoginResult.PrecisaCodigo -> pendente.value = result
                is LoginResult.TrocarSenha -> trocarSenha.value = result
            }
            loading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onLoggedInFazendas: () -> Unit = onLoggedIn, viewModel: LoginViewModel = viewModel()) {
    // v279 (mockup v338 item 10) -- atalho "Fazendas": entra e abre o cadastro.
    var irFazendas by remember { mutableStateOf(false) }
    // v281 (mockup v343) -- o ícone Fazendas fica no canto superior direito (fora do formulário).
    var acionarFazendas by remember { mutableStateOf(0) }
    val aoEntrar: () -> Unit = { if (irFazendas) onLoggedInFazendas() else onLoggedIn() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val loading by viewModel.loading
    val error by viewModel.errorMessage
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 40.dp, vertical = 24.dp)), // v279 (mockup v338) -- margem dos formulários
        verticalArrangement = Arrangement.Center,
        // Logo centralizada -- pedido do usuário ("centralise a logo"),
        // mesmo layout do login do site (login/page.tsx: "items-center
        // text-center").
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Voltamos pro tamanho de ANTES de toda a saga de recorte (v1.2.66 a
        // v1.2.70): usuário confirmou com print ao vivo que a versão v1.2.64
        // (200dp, ContentScale.Fit puro, sem nenhum recorte) já renderizava a
        // logo grande e legível -- todas as tentativas de recorte
        // subsequentes (medir % no PNG do site, depois no drawable com
        // margem de segurança) foram tentativas de resolver um problema que
        // na real não existia neste tamanho maior; só apareciam quando a
        // caixa era pequena (40-48dp) e a moldura transparente do PNG virava
        // proporcionalmente grande demais. Único ajuste pedido agora:
        // CENTRALIZAR sem mexer no tamanho -- a arte visível (letras+
        // diamante) não fica simetricamente centrada dentro do canvas do PNG
        // (sobra ~22% de moldura à esquerda contra ~5% à direita), então
        // centralizar a CAIXA da imagem (Column já faz isso via
        // CenterHorizontally) deixa a arte visualmente puxada pra direita.
        // offset(x = -32dp) desloca só os pixels renderizados (não afeta o
        // cálculo de centralização da Column) pra compensar esse desbalanço
        // e centralizar a arte de verdade -- sem cortar nem redimensionar
        // nada, só reposicionar.
        // v319/v317 (mockups aprovados, iguais ao site) -- login minimalista:
        // logo nova, sem o slogan e sem "Entre com sua conta".
        MarcaBRAgro(modifier = Modifier.height(52.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 28.dp))

        // v284b (mockup v356) -- igual à plataforma: ícone ✉ à esquerda do campo.
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        com.bragro.mobile.ui.domain.IconeNegrito(Icons.Outlined.Email, contentDescription = "E-mail", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp, modifier = Modifier.padding(end = 10.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            // autoCorrect = false -- pedido do usuário ("aplique isso também
            // nos campos" da tela de login): e-mail não deve sofrer
            // autocorreção do teclado (evita o teclado reinserir/alterar
            // caracteres já apagados/editados pelo usuário).
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrect = false),
            modifier = Modifier.weight(1f),
            singleLine = true,
            colors = appFieldColors(),
        )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
        // Ícone "ver" (olho) no campo de senha -- pedido do usuário ("em
        // login coloque no campo senha o icone ver"), paridade com o site
        // (PasswordInput.tsx: alterna type="password"/"text" com Eye/EyeOff
        // do lucide-react). Aqui alterna PasswordVisualTransformation() <->
        // VisualTransformation.None, mesmo efeito visual.
        var senhaVisivel by remember { mutableStateOf(false) }
        // v284b (mockup v356) -- ícone 🔒 à esquerda do campo.
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        com.bragro.mobile.ui.domain.IconeNegrito(Icons.Outlined.Lock, contentDescription = "Senha", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp, modifier = Modifier.padding(end = 10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                    com.bragro.mobile.ui.domain.IconeNegrito(
                        if (senhaVisivel) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (senhaVisivel) "Ocultar senha" else "Ver senha",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 22.dp, // v284 -- negrito
                    )
                }
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            colors = appFieldColors(),
        )
        }

        // v72b -- verificação em 2 etapas.
        val pendente by viewModel.pendente
        var codigo2 by remember { mutableStateOf("") }
        if (pendente != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
            OutlinedTextField(
                value = codigo2,
                onValueChange = { codigo2 = it.filter(Char::isDigit).take(6) },
                label = { Text("Código do app autenticador") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = appFieldColors(),
            )
        }

        // v139 -- "Troque sua senha" (entrou com a senha provisória).
        val trocar by viewModel.trocarSenha
        var nova by remember { mutableStateOf("") }
        var repetir by remember { mutableStateOf("") }
        if (trocar != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
            Text("Troque sua senha", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("Você entrou com uma senha provisória.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = nova, onValueChange = { nova = it }, label = { Text("Nova senha") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = appFieldColors(),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = repetir, onValueChange = { repetir = it }, label = { Text("Repetir senha") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = appFieldColors(),
            )
        }

        if (error != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
            Text(error!!, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }

        // v279 (mockup v338 item 10) -- "Entrar" minimalista: texto verde com seta, à direita.
        val entrar: () -> Unit = {
            if (viewModel.trocarSenha.value != null) viewModel.salvarNovaSenha(email, nova, repetir, aoEntrar)
            else if (viewModel.pendente.value != null) viewModel.verificarCodigo(email, password, codigo2, aoEntrar)
            else viewModel.login(email, password, aoEntrar)
        }
        androidx.compose.runtime.LaunchedEffect(acionarFazendas) {
            if (acionarFazendas > 0 && !loading) {
                irFazendas = true
                // v284 -- como na plataforma: Fazendas entra com o e-mail e a senha
                // digitados e abre o cadastro de fazendas; sem eles, avisa o que falta.
                if (email.isBlank() || password.isBlank()) viewModel.errorMessage.value = "Preencha e-mail e senha e toque em Fazendas."
                else entrar()
            }
        }
        // v281 (mockup v343) -- igual à plataforma: esqueci a senha e criar conta como
        // ícones à esquerda; "Entrar" só o duplo chevron à direita.
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { openInCustomTab(context, "${BuildConfig.API_BASE_URL}/esqueci-senha") }) {
                com.bragro.mobile.ui.domain.IconeNegrito(Icons.Outlined.Key, contentDescription = "Esqueci a senha", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp)
            }
            IconButton(onClick = { openInCustomTab(context, "${BuildConfig.API_BASE_URL}/cadastro") }) {
                com.bragro.mobile.ui.domain.IconeNegrito(Icons.Outlined.PersonAdd, contentDescription = "Criar conta", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            IconButton(onClick = { irFazendas = false; entrar() }, enabled = !loading) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else com.bragro.mobile.ui.domain.IconeNegrito(Icons.Outlined.KeyboardDoubleArrowRight, contentDescription = if (trocar != null) "Salvar e entrar" else "Entrar", tint = androidx.compose.material3.MaterialTheme.colorScheme.primary, size = 30.dp)
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
        Text(
            "Internet só no primeiro acesso.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // País e idioma -- mesmo menu do login do site (CurrencyMenu), canto
    // superior direito. Saiu de Configurações (pedido do usuário).
    // v1.6.4 (mockup v105, item 2) -- + tema (Automático/Claro/Escuro), igual ao login do site.
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // v281 (mockup v343) -- Fazendas só com o ícone, na cor do texto: entra e abre o cadastro.
        IconButton(onClick = { acionarFazendas++ }) {
            com.bragro.mobile.ui.domain.IconeNegrito(Icons.Outlined.Grass, contentDescription = "Fazendas", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, size = 22.dp)
        }
        LoginIdiomaMenu()
        com.bragro.mobile.ui.theme.ThemeToggle()
    }
    }
}

@Composable
private fun LoginIdiomaMenu(modifier: Modifier = Modifier) = com.bragro.mobile.ui.i18n.MenuPaisIdiomaMoeda(modifier) // v1.6.13 (mockup v114)
