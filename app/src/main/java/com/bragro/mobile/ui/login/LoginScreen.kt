package com.bragro.mobile.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import com.bragro.mobile.ui.i18n.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.VisibilityOff
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

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
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
        Image(
            painter = painterResource(if (com.bragro.mobile.ui.i18n.Idioma.argentina) R.drawable.logo_argro else R.drawable.logo_bragro),
            contentDescription = if (com.bragro.mobile.ui.i18n.Idioma.argentina) "ARGro" else "BRAgro",
            modifier = Modifier
                .height(200.dp)
                .offset(x = (-32).dp),
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
            // autoCorrect = false -- pedido do usuário ("aplique isso também
            // nos campos" da tela de login): e-mail não deve sofrer
            // autocorreção do teclado (evita o teclado reinserir/alterar
            // caracteres já apagados/editados pelo usuário).
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrect = false),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = appFieldColors(),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
        // Ícone "ver" (olho) no campo de senha -- pedido do usuário ("em
        // login coloque no campo senha o icone ver"), paridade com o site
        // (PasswordInput.tsx: alterna type="password"/"text" com Eye/EyeOff
        // do lucide-react). Aqui alterna PasswordVisualTransformation() <->
        // VisualTransformation.None, mesmo efeito visual.
        var senhaVisivel by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                    Icon(
                        imageVector = if (senhaVisivel) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (senhaVisivel) "Ocultar senha" else "Ver senha",
                    )
                }
            },
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
    // País e idioma -- mesmo menu do login do site (CurrencyMenu), canto
    // superior direito. Saiu de Configurações (pedido do usuário).
    LoginIdiomaMenu(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 8.dp, end = 8.dp),
    )
    }
}

@Composable
private fun LoginIdiomaMenu(modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var aberto by remember { mutableStateOf(false) }
    val idioma = com.bragro.mobile.ui.i18n.Idioma
    // Cotação ao vivo (rota pública /api/mobile/weather) só pro conversor --
    // buscada na 1ª abertura do menu.
    var fx by remember { mutableStateOf<com.bragro.mobile.data.model.FxRatesData?>(null) }
    var valor by remember { mutableStateOf("100") }
    var de by remember { mutableStateOf("BRL") }
    androidx.compose.runtime.LaunchedEffect(aberto) {
        if (aberto && fx == null) {
            fx = com.bragro.mobile.data.repo.WeatherRepository().fetch()?.fx
        }
    }
    androidx.compose.foundation.layout.Box(modifier) {
        androidx.compose.material3.TextButton(onClick = { aberto = true }) {
            Icon(Icons.Filled.AttachMoney, contentDescription = "Moeda, idioma e conversor", modifier = Modifier.height(18.dp))
            Text(
                if (idioma.argentina) "ARS" else "BRL",
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        androidx.compose.material3.DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            Text("País", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            listOf("BR" to "Brasil (pt-BR · R$)", "AR" to "Argentina (es · ARS)").forEach { (cod, nome) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(nome + if (idioma.pais == cod) " ✓" else "") },
                    onClick = { if (idioma.pais != cod) idioma.definirPais(ctx, cod); aberto = false },
                )
            }
            androidx.compose.material3.HorizontalDivider()
            Text("Idioma", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            listOf("pt" to "Português (Brasil)", "es" to "Español").forEach { (cod, nome) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(nome + if (idioma.codigo == cod) " ✓" else "") },
                    onClick = { if (idioma.codigo != cod) idioma.definir(ctx, cod); aberto = false },
                )
            }
            androidx.compose.material3.HorizontalDivider()
            Text("Conversor", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            fun brlPer(c: String): Double? = when (c) { "BRL" -> 1.0; "USD" -> fx?.usdBrl; else -> fx?.arsBrl }
            fun rotulo(c: String) = if (c == "USD") "U\$D" else c
            androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).width(260.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = valor,
                    onValueChange = { valor = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("BRL", "USD", "ARS").forEach { c ->
                        androidx.compose.material3.TextButton(onClick = { de = c }) {
                            Text(rotulo(c), fontWeight = if (de == c) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
                        }
                    }
                }
                val num = valor.replace(",", ".").toDoubleOrNull()
                listOf("BRL", "USD", "ARS").filter { it != de }.forEach { alvo ->
                    val f = brlPer(de)
                    val t = brlPer(alvo)
                    val res = if (num != null && f != null && t != null && t > 0) num * f / t else null
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rotulo(alvo))
                        Text(res?.let { String.format(java.util.Locale("pt", "BR"), "%,.2f", it) } ?: "—", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
                Text(
                    if (fx?.usdBrl != null) String.format(java.util.Locale("pt", "BR"), "U\$D 1 = R$ %.2f", fx!!.usdBrl) else "Cotação indisponível",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
