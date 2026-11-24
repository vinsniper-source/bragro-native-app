package com.bragro.mobile.ui.admin

import kotlinx.serialization.json.doubleOrNull
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
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
private val Amarelo = Color(0xFFC9A000)

/** v283 -- ok = verde, warn = amarelo, bad = laranja (sem vermelho). */
@Composable
private fun corTom(tom: String): Color = when (tom.lowercase()) {
    "ok", "green", "verde" -> MaterialTheme.colorScheme.primary
    "warn", "warning", "yellow", "amarelo" -> Amarelo
    "bad", "over", "danger", "red", "erro", "critical" -> Laranja
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun tamanho(b: Double): String = when {
    b >= 1024.0 * 1024 * 1024 -> "%.1f GB".format(b / (1024.0 * 1024 * 1024))
    b >= 1024.0 * 1024 -> "%.0f MB".format(b / (1024.0 * 1024))
    b >= 1024.0 -> "%.0f KB".format(b / 1024.0)
    else -> "%.0f B".format(b)
}

@Composable
private fun Selo(texto: String, ligado: Boolean) {
    val cor = if (ligado) MaterialTheme.colorScheme.primary else Laranja
    // v275 (mockup v330) -- só o texto colorido, sem fundo.
    Text(texto, color = cor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Bloco(titulo: String, direita: (@Composable () -> Unit)? = null, conteudo: @Composable () -> Unit) {
    com.bragro.mobile.ui.theme.Card(modifier = Modifier.fillMaxWidth()) {
        // v275 (mockup v330) -- margem interna: nada encostado na borda.
        // v289 (mockup v377) -- todo bloco começa recolhido: título e resumo à direita; tocar abre/fecha.
        var aberto by androidx.compose.runtime.saveable.rememberSaveable(titulo.substringBefore(" (")) { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { aberto = !aberto }.padding(vertical = 2.dp)) {
                Text(titulo, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                direita?.invoke()
                Spacer(Modifier.width(8.dp))
                com.bragro.mobile.ui.domain.CantoL(aberto)
            }
            if (aberto) {
                Spacer(Modifier.height(6.dp))
                conteudo()
            }
        }
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
                Bloco("Resumo", direita = { Text("${n("clientesEmTeste")} em teste", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }) {
                    androidx.compose.foundation.layout.Column { // v289 (mockup v377) -- virou bloco recolhível
                    val backup = s("ultimoBackup")?.let { com.bragro.mobile.ui.seguranca.formatIsoPublico(it) } ?: "—"
                    Row { Numero("Clientes pagantes", n("clientesPagantes").toString(), Modifier.weight(1f)); Numero("Em teste", n("clientesEmTeste").toString(), Modifier.weight(1f)); Numero("Faturas em aberto", n("faturasEmAberto").toString(), Modifier.weight(1f)) }
                    HorizontalDivider()
                    Row { Numero("Banco", if (bancoNoAr) "no ar" else "fora", Modifier.weight(1f)); Numero("Último backup", backup, Modifier.weight(1f)); Numero("Robôs (7 dias)", n("robos7Dias").toString(), Modifier.weight(1f)) }
                    // v283 (mockup v355 item 8) -- tamanho do banco, backups guardados e pendências.
                    HorizontalDivider()
                    val bytes = dados["bancoBytes"]?.jsonPrimitive?.doubleOrNull
                    Row { Numero("Tamanho do banco", bytes?.let { tamanho(it) } ?: "—", Modifier.weight(1f)); Numero("Backups guardados", n("backupsGuardados").toString(), Modifier.weight(1f)); Numero("Pendências", n("pendencias").toString(), Modifier.weight(1f)) }
                    }
                }
            }
            fun lista(k: String) = (dados[k] as? kotlinx.serialization.json.JsonArray) ?: kotlinx.serialization.json.JsonArray(emptyList())
            fun JsonObject.t(k: String) = this[k]?.jsonPrimitive?.contentOrNull ?: ""
            val verificacoes = lista("verificacoes")
            if (verificacoes.isNotEmpty()) item {
                val ok = verificacoes.count { it.jsonObject.t("tom") == "ok" }
                Bloco("Verificações automáticas", direita = { Selo("$ok/${verificacoes.size}", ok == verificacoes.size) }) {
                    verificacoes.forEachIndexed { i, el ->
                        val o = el.jsonObject
                        if (i > 0) HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(o.t("nome"), style = MaterialTheme.typography.bodyMedium)
                                if (o.t("detalhe").isNotBlank()) Text(o.t("detalhe"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(o.t("rotulo"), color = corTom(o.t("tom")), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            val limites = lista("limites")
            if (limites.isNotEmpty()) item {
                Bloco("Limites de uso") {
                    limites.forEachIndexed { i, el ->
                        val o = el.jsonObject
                        if (i > 0) HorizontalDivider()
                        val usado = o["usado"]?.jsonPrimitive?.doubleOrNull
                        val limite = o["limite"]?.jsonPrimitive?.doubleOrNull
                        val un = o.t("unidade").let { if (it == "count") "" else it }
                        fun fmt(v: Double?) = when {
                            v == null -> "—"
                            un.equals("bytes", true) || un.equals("B", true) -> tamanho(v)
                            v == Math.floor(v) -> "%,.0f".format(java.util.Locale("pt", "BR"), v) + if (un.isNotBlank()) " $un" else ""
                            else -> "%,.1f".format(java.util.Locale("pt", "BR"), v) + if (un.isNotBlank()) " $un" else ""
                        }
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(o.t("rotulo"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${fmt(usado)} / ${fmt(limite)}", style = MaterialTheme.typography.labelSmall, color = corTom(o.t("nivel")))
                            }
                            if (usado != null && limite != null && limite > 0) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { (usado / limite).toFloat().coerceIn(0f, 1f) },
                                    color = corTom(o.t("nivel")),
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            }
                        }
                    }
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
            val eventos = lista("eventos")
            item {
                Bloco("Eventos de segurança (${eventos.size})") {
                    if (eventos.isEmpty()) Text("Nenhum evento.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    eventos.forEachIndexed { i, el ->
                        val o = el.jsonObject
                        if (i > 0) HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(o.t("tipo"), style = MaterialTheme.typography.bodyMedium)
                                if (o.t("detalhe").isNotBlank()) Text(o.t("detalhe"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(com.bragro.mobile.ui.seguranca.formatIsoPublico(o.t("quando")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            val erros = lista("erros")
            item {
                Bloco("Ocorrências / erros (${erros.size})", direita = { if (erros.isEmpty()) Selo("nenhum", true) }) {
                    erros.forEachIndexed { i, el ->
                        val o = el.jsonObject
                        if (i > 0) HorizontalDivider()
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(o.t("mensagem"), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            val vezes = o["n"]?.jsonPrimitive?.intOrNull ?: 1
                            Text(
                                listOf(o.t("origem"), o.t("local"), "${vezes}×", com.bragro.mobile.ui.seguranca.formatIsoPublico(o.t("ultimo"))).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Laranja,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            val app = dados["appAndroid"] as? JsonObject
            item {
                Bloco("App Android", direita = { Text(app?.t("versao")?.ifBlank { null } ?: "—", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }) {
                    if (app == null) Text("Nenhuma versão publicada.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Row {
                        Numero("Versão", app.t("versao").ifBlank { "—" }, Modifier.weight(1f))
                        Numero("Publicado", com.bragro.mobile.ui.seguranca.formatIsoPublico(app.t("publicadoEm")), Modifier.weight(1f))
                        Numero("Tamanho", app["tamanhoBytes"]?.jsonPrimitive?.doubleOrNull?.let { tamanho(it) } ?: "—", Modifier.weight(1f))
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
