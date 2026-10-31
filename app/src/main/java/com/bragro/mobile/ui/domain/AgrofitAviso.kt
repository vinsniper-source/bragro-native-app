package com.bragro.mobile.ui.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bragro.mobile.data.model.AgrofitConfig
import com.bragro.mobile.data.model.AgrofitConsulta
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.coroutines.delay

/**
 * v85 (mockup v85 aprovado) -- aviso do Agrofit (registro no MAPA) embaixo
 * do campo de item, igual à plataforma (components/domain/agrofit-aviso.tsx).
 * Verde: registrado (na cultura escolhida, se houver). Laranja: sem registro
 * para essa cultura ou registro não ativo. Item não achado: nada aparece.
 * Nunca impede salvar.
 */
private object AgrofitCache {
    val mapa = HashMap<String, AgrofitConsulta?>()
}

private fun listaCurta(itens: List<String>, max: Int): String =
    if (itens.size <= max) itens.joinToString(", ") else itens.take(max).joinToString(", ") + " e mais ${itens.size - max} culturas"

@Composable
fun AgrofitAviso(config: AgrofitConfig, produto: String?, fields: Map<String, String>) {
    val cultura = config.culturaField?.let { fields[it] }.orEmpty().trim()
    val gatilho = config.quando?.let { fields[it.field] }.orEmpty()
    val liberado = config.quando == null || (gatilho.isNotBlank() && config.quando.values.contains(gatilho))
    val nome = produto.orEmpty().trim()
    val chave = "$nome|$cultura"
    var dados by remember { mutableStateOf(AgrofitCache.mapa[chave]) }

    LaunchedEffect(chave, liberado) {
        if (!liberado || nome.length < 3) { dados = null; return@LaunchedEffect }
        if (AgrofitCache.mapa.containsKey(chave)) { dados = AgrofitCache.mapa[chave]; return@LaunchedEffect }
        delay(500)
        val r = try {
            NetworkModule.mobileApi.agrofit(nome, cultura).let { if (it.isSuccessful) it.body() else null }
        } catch (e: Exception) {
            null
        }
        AgrofitCache.mapa[chave] = r
        dados = r
    }

    val d = dados ?: return
    val p = d.produto ?: return
    if (!liberado || !d.encontrado) return

    val classe = p.classe.lowercase()
    val tox = listOf(p.toxicologica, p.ambiental).filter { it.isNotBlank() }.joinToString(" · ")
    val ativo = p.situacao.isBlank() || Regex("ativ|regular|vigente", RegexOption.IGNORE_CASE).containsMatchIn(p.situacao)
    val ok: Boolean
    val tituloForte: String
    val tituloResto: String
    val meta: String
    when {
        !ativo -> {
            ok = false
            tituloForte = "Registro ${p.situacao.lowercase()} no MAPA"
            tituloResto = " · $classe"
            meta = "${p.marca} · registro nº ${p.registro}. Confira com o agrônomo antes de usar."
        }
        d.registradoNaCultura == false -> {
            ok = false
            tituloForte = "Sem registro no MAPA para ${d.cultura.orEmpty()}"
            tituloResto = ""
            meta = "Registrado para: ${listaCurta(p.culturas, 6)}. Confira com o agrônomo antes de aplicar."
        }
        d.registradoNaCultura == true -> {
            ok = true
            tituloForte = "Registrado no MAPA para ${d.cultura.orEmpty()}"
            tituloResto = " · $classe"
            val alvos = d.alvos.orEmpty()
            val txtAlvos = if (alvos.isNotEmpty()) " · alvos na ${d.cultura.orEmpty().lowercase()}: " + alvos.take(4).joinToString(", ") + (if (alvos.size > 4) "…" else "") else ""
            meta = (if (tox.isNotBlank()) "Classe toxicológica: $tox · " else "") + "registro ativo nº ${p.registro}$txtAlvos"
        }
        else -> {
            ok = true
            tituloForte = "${p.classe} · registro ativo"
            tituloResto = ""
            meta = listOf(p.ingrediente, tox, "permitido em: ${listaCurta(p.culturas, 5)}").filter { it.isNotBlank() }.joinToString(" · ")
        }
    }
    val cor = if (ok) MaterialTheme.colorScheme.primary else corAtencao()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(cor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(if (ok) Icons.Outlined.Check else Icons.Outlined.WarningAmber, contentDescription = null, tint = cor, modifier = Modifier.size(16.dp))
        Column {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(tituloForte) }
                    append(tituloResto)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// v86 (mockup v86 aprovado) -- guia de pragas: produtos registrados no MAPA
// para o alvo na cultura escolhida, igual à plataforma (guia-praga.tsx).
// Quem está no Estoque com saldo vem primeiro ("em estoque"). Só consulta.

private object GuiaPragaCache {
    val mapa = HashMap<String, kotlinx.serialization.json.JsonObject?>()
}

@Composable
fun GuiaPraga(cultura: String?, alvo: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val c = cultura.orEmpty().trim()
    val a = alvo.orEmpty().trim()
    val chave = "$c|$a"
    var dados by remember(chave) { mutableStateOf(GuiaPragaCache.mapa[chave]) }
    var filtro by remember(chave) { mutableStateOf("todos") }
    var aberto by remember(chave) { mutableStateOf(false) }

    LaunchedEffect(chave) {
        if (c.isBlank() || a.length < 3) { dados = null; return@LaunchedEffect }
        if (GuiaPragaCache.mapa.containsKey(chave)) { dados = GuiaPragaCache.mapa[chave]; return@LaunchedEffect }
        delay(300)
        val r = com.bragro.mobile.data.repo.ModuleActionsRepository(context.applicationContext).run("guia-praga", item = a, category = c)
        if (r != null) GuiaPragaCache.mapa[chave] = r
        dados = r
    }

    val d = dados ?: return
    if (c.isBlank() || a.isBlank()) return
    fun kotlinx.serialization.json.JsonObject.txt(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.let { if (it.isString) it.content else it.toString() }.orEmpty()
    fun kotlinx.serialization.json.JsonObject.bool(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
    if (!d.bool("indiceDisponivel")) return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val verde = MaterialTheme.colorScheme.primary
    val produtos = (d["produtos"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }.orEmpty()
    if (produtos.isEmpty()) {
        Text(
            "Nenhum produto do Agrofit para esse alvo nessa cultura (ou o nome do alvo está diferente do oficial).",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            modifier = Modifier.padding(top = 4.dp),
        )
        return
    }
    val noEstoque = produtos.count { it.bool("emEstoque") }
    val temOrganico = produtos.any { it.bool("organico") }
    val lista = produtos.filter { p ->
        when (filtro) {
            "estoque" -> p.bool("emEstoque")
            "organicos" -> p.bool("organico")
            else -> true
        }
    }
    val mostrados = if (aberto) lista else lista.take(5)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(verde.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Eco, contentDescription = null, tint = verde, modifier = Modifier.size(16.dp))
            Column {
                Text("Registrados no MAPA para ${a.lowercase()} na ${c.lowercase()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = verde)
                Text(
                    "${produtos.size} " + (if (produtos.size == 1) "produto" else "produtos") + (if (noEstoque > 0) " · $noEstoque no estoque" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }
        if (noEstoque > 0 || temOrganico) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 24.dp)) {
                @Composable
                fun chip(id: String, rotulo: String) {
                    val on = filtro == id
                    Text(
                        rotulo,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else verde,
                        modifier = Modifier
                            .background(if (on) verde else MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                            .border(1.dp, verde.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .clickable { filtro = id; aberto = false }
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
                chip("todos", "Todos")
                if (noEstoque > 0) chip("estoque", "Em estoque")
                if (temOrganico) chip("organicos", "Orgânicos")
            }
        }
        Column(modifier = Modifier.padding(start = 24.dp)) {
            mostrados.forEach { p ->
                androidx.compose.material3.HorizontalDivider(color = verde.copy(alpha = 0.12f))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(p.txt("marca"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        val tox = listOf(p.txt("toxicologica"), p.txt("ambiental")).filter { it.isNotBlank() }.joinToString(" · ")
                        Text(
                            listOf(p.txt("ingrediente"), p.txt("classe").lowercase(), if (p.bool("organico")) "orgânico" else "", tox).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                        )
                    }
                    if (p.bool("emEstoque")) {
                        Text(
                            "em estoque",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(start = 6.dp).background(verde, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        if (lista.size > 5) {
            Text(
                if (aberto) "Mostrar menos ▴" else "Ver os ${lista.size} produtos ▾",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = verde,
                modifier = Modifier.padding(start = 24.dp).clickable { aberto = !aberto },
            )
        }
    }
}

