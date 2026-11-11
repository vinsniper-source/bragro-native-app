package com.bragro.mobile.ui.admin

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.repo.SecurityRepository
import com.bragro.mobile.ui.i18n.Text
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** v134 -- Painel do dono no app (mesmos números do /admin do site), só
 * para o dono da plataforma (ehDonoDaPlataforma no servidor). */
class PainelDonoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SecurityRepository(app)
    var dados = mutableStateOf<JsonObject?>(null)
        private set
    var carregando = mutableStateOf(false)
        private set
    var erro = mutableStateOf<String?>(null)
        private set

    fun carregar() {
        carregando.value = true
        viewModelScope.launch {
            val r = repo.run("admin_panel")
            carregando.value = false
            r.onSuccess { dados.value = it?.jsonObject; erro.value = null }.onFailure { erro.value = it.message }
        }
    }
}

private val Laranja = Color(0xFFD9822B)

@Composable
private fun Selo(texto: String, ligado: Boolean) {
    val cor = if (ligado) MaterialTheme.colorScheme.primary else Laranja
    Surface(color = cor.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
        Text(texto, color = cor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun Bloco(titulo: String, direita: (@Composable () -> Unit)? = null, conteudo: @Composable () -> Unit) {
    com.bragro.mobile.ui.theme.Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(titulo, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            direita?.invoke()
        }
        Spacer(Modifier.height(6.dp))
        conteudo()
    }
}

@Composable
private fun Numero(rotulo: String, valor: String, modifier: Modifier) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(valor, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PainelDonoScreen(onBack: () -> Unit, viewModel: PainelDonoViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.carregar() }
    val d by viewModel.dados
    val carregando by viewModel.carregando
    val erro by viewModel.erro

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.BackNavButton()
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.Bottom) {
                        com.bragro.mobile.ui.domain.IconeNegrito(Icons.Outlined.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, size = 22.dp, modifier = Modifier.padding(end = 8.dp, bottom = 3.dp))
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Painel do dono", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (carregando && d == null) item { CircularProgressIndicator() }
            if (erro != null) item { Text(erro!!, color = Laranja) }
            val dados = d ?: return@LazyColumn
            fun s(k: String) = dados[k]?.jsonPrimitive?.contentOrNull
            fun n(k: String) = dados[k]?.jsonPrimitive?.intOrNull ?: 0
            val bancoNoAr = dados["bancoNoAr"]?.jsonPrimitive?.booleanOrNull == true
            item {
                Bloco("Versão no ar", direita = { Selo("no ar", true) }) {
                    Text(s("versao") ?: "—", style = MaterialTheme.typography.bodySmall)
                    s("versaoMensagem")?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
            item {
                com.bragro.mobile.ui.theme.Card(modifier = Modifier.fillMaxWidth()) {
                    val backup = s("ultimoBackup")?.let { com.bragro.mobile.ui.seguranca.formatIsoPublico(it) } ?: "—"
                    Row { Numero("Clientes pagantes", n("clientesPagantes").toString(), Modifier.weight(1f)); Numero("Em teste", n("clientesEmTeste").toString(), Modifier.weight(1f)); Numero("Faturas em aberto", n("faturasEmAberto").toString(), Modifier.weight(1f)) }
                    HorizontalDivider()
                    Row { Numero("Banco", if (bancoNoAr) "no ar" else "fora", Modifier.weight(1f)); Numero("Último backup", backup, Modifier.weight(1f)); Numero("Robôs (7 dias)", n("robos7Dias").toString(), Modifier.weight(1f)) }
                }
            }
            val integracoes = dados["integracoes"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
            item {
                val ligadas = integracoes.count { it.jsonObject["ligado"]?.jsonPrimitive?.booleanOrNull == true }
                Bloco("Integrações do sistema", direita = { Selo("$ligadas/${integracoes.size} ligadas", ligadas == integracoes.size) }) {
                    integracoes.forEachIndexed { i, el ->
                        val o = el.jsonObject
                        if (i > 0) HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(o["rotulo"]?.jsonPrimitive?.contentOrNull ?: "", style = MaterialTheme.typography.bodyMedium)
                                Text(o["detalhe"]?.jsonPrimitive?.contentOrNull ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Selo(o["status"]?.jsonPrimitive?.contentOrNull ?: "", o["ligado"]?.jsonPrimitive?.booleanOrNull == true)
                        }
                    }
                }
            }
            val clientes = dados["clientes"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
            item {
                Bloco("Clientes (${clientes.size})") {
                    clientes.forEachIndexed { i, el ->
                        val o = el.jsonObject
                        if (i > 0) HorizontalDivider()
                        val abertas = o["faturasEmAberto"]?.jsonPrimitive?.intOrNull ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(o["nome"]?.jsonPrimitive?.contentOrNull ?: "", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (o["situacao"]?.jsonPrimitive?.contentOrNull ?: "") + if (abertas > 0) " · $abertas em aberto" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (abertas > 0) Laranja else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
