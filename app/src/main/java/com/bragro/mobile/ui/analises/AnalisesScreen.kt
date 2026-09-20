package com.bragro.mobile.ui.analises

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.GridOn
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.appFieldColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.NetworkStatus
import com.bragro.mobile.data.model.ColumnConfig
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.data.repo.AnalisesRepository
import com.bragro.mobile.ui.domain.BarSeries
import com.bragro.mobile.ui.domain.EqualWidthBlockRow
import com.bragro.mobile.ui.domain.FarmSelectorButton
import com.bragro.mobile.ui.domain.LabeledIconButton
import com.bragro.mobile.ui.domain.SimpleBarChart
import com.bragro.mobile.ui.domain.defaultSeriesColor
import com.bragro.mobile.ui.domain.RecordTableHeader
import com.bragro.mobile.ui.domain.RecordTableRow
import com.bragro.mobile.ui.domain.exportXlsx
import com.bragro.mobile.ui.print.HtmlPrinter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Fase 2 do app nativo (Task #36): Analises cruzadas entre modulos,
// espelhando src/app/(app)/analises/analises-client.tsx no site (15
// cruzamentos: Planejado x Realizado x Pago, Custo/ha por fonte, Pedido x
// Recebimento, Consumo de Estoque, Clima x Produtividade, Pragas x
// Produtividade, Folha x Custo, Eficiencia de maquina etc.) -- via
// /api/mobile/analises, que reaproveita a MESMA getAnalisesCruzadas() do
// site. Renderizacao GENERICA (cada chave do JSON vira uma secao de
// cards com os campos brutos) em vez de modelar 15 formatos de linha
// diferentes em Kotlin -- mesmo principio do motor generico de
// lista/formulario ja usado nos 16 modulos (DomainListScreen/
// DomainFormScreen, guiados por DomainConfig em vez de 16 telas escritas
// a mao).
class AnalisesViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = AnalisesRepository(app)

    var analises = mutableStateOf<JsonObject?>(null)
        private set
    var safrasDisponiveis = mutableStateOf<List<String>>(emptyList())
        private set
    // Filtro de Cultura ao lado do de Safra -- pedido do usuário ("análises
    // coloque filtro cultura, dividindo a mesma linha com o filtro safra"),
    // mesmo padrão já usado no DRE (DreScreen.kt).
    var culturasDisponiveis = mutableStateOf<List<String>>(emptyList())
        private set
    var loading = mutableStateOf(false)
        private set
    var offline = mutableStateOf(false)
        private set
    var safra = mutableStateOf<String?>(null)
        private set
    var cultura = mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            repository.observeCached().collectLatest { entity ->
                if (entity != null) {
                    analises.value = repository.parse(entity)
                    safrasDisponiveis.value = repository.safras(entity)
                    culturasDisponiveis.value = repository.culturas(entity)
                }
            }
        }
        refresh()
    }

    fun setSafra(value: String?) {
        safra.value = value
        refresh()
    }

    fun setCultura(value: String?) {
        cultura.value = value
        refresh()
    }

    fun refresh() {
        if (loading.value) return
        loading.value = true
        viewModelScope.launch {
            val ok = repository.refresh(safra.value, cultura.value)
            offline.value = !ok
            loading.value = false
        }
    }
}

// Palavras que a quebra automática de camelCase (tituloSecao/tituloCampo
// abaixo) não consegue acertar sozinha -- ou porque a chave original em
// inglês/abreviação vira uma palavra errada em português ("Vs" == "versus",
// nunca deveria aparecer na tela -- pedido do usuário: "ainda há palavras
// em inglês"), ou porque o nome do campo em camelCase não carrega acento
// nenhum ("areaHa" não tem como virar "Área" sozinho -- pedido do usuário:
// "corrija toda a ortografia"). Comparação por palavra INTEIRA (não
// "contains"), já quebrada pelo regex abaixo, pra não bagunçar palavras que
// só COMEÇAM parecido (ex.: não trocar "Vspoderia" se existisse).
// Segunda varredura (pedido do usuário: "ainda há erros de ortografia ç,
// ainda falta moeda e porcentagem, ainda há palavras em inglês" -- os
// problemas PERSISTIAM mesmo após a 1ª rodada de TOKEN_FIXES, ver comentário
// original abaixo): mapeamento ampliado depois de conferir TODO campo
// retornado por getAnalisesCruzadas (lib/services/analises.ts) um por um.
// Achados novos: "farmName"/"noContrato" (campos em inglês/abreviação
// ambígua), "funcao"/"lancamentos*"/"talhao"/"ultimo"/"atras"/"eficiencia"/
// "maquina" (sem cedilha/acento -- a chave original em camelCase nunca teve
// esses caracteres, então a quebra automática sozinha nunca ia acertar),
// "chuvaTotalMm"/"volumeTransportadoTon" (unidades em maiúscula, mesmo
// padrão já corrigido pra "Ha"->"ha"/"Km"->"km"), e "diasPVenc"/
// "diasPVencer"/"valorR" (abreviações cruas tipo "P" e "R" que sobram da
// quebra de camelCase sem virar palavra nenhuma).
private val TOKEN_FIXES = mapOf(
    "Vs" to "x", "Ha" to "ha", "Area" to "Área", "Rh" to "RH", "Nf" to "NF",
    "Os" to "O.S.", "Pct" to "%", "Km" to "km", "Cnpj" to "CNPJ", "Cpf" to "CPF",
    "Cif" to "CIF", "Fob" to "FOB", "Kg" to "kg", "Ph" to "pH",
    "Farm" to "Fazenda", "Name" to "Nome", "Mm" to "mm", "Ton" to "ton",
    "Talhao" to "Talhão", "Funcao" to "Função", "Lancamentos" to "Lançamentos",
    "Ultimo" to "Último", "Atras" to "Atrás", "No" to "Nº",
    "PVenc" to "para vencer", "PVencer" to "para vencer",
    "Eficiencia" to "Eficiência", "Maquina" to "Máquina",
    // Terceira varredura (pedido do usuário: "corrigir Ç", screenshot com
    // "Orcado" em Planejado x Realizado) -- mesma causa das rodadas
    // anteriores: a chave em camelCase (orcado, descricao, ocorrenciasPraga,
    // conciliacaoCaixaVsFinanceiro) nunca teve cedilha/til pra começo, então
    // a quebra automática sozinha não tinha como acertar. Conferido campo a
    // campo de novo contra getAnalisesCruzadas (lib/services/analises.ts)
    // pra não sobrar nenhum.
    "Orcado" to "Orçado", "Descricao" to "Descrição",
    "Ocorrencias" to "Ocorrências", "Conciliacao" to "Conciliação",
    // "R" sozinho (de "valorR", que a quebra de camelCase isola como
    // palavra própria já que não tem letra minúscula depois pra continuar
    // juntando) não vira palavra nenhuma em português -- descartado (ver
    // filtro de vazios em tituloSecao() abaixo) em vez de aparecer cru.
    "R" to "",
)

/** Rotulo de secao/campo mais legivel: "planejadoVsRealizado" -> "Planejado
 * x Realizado" (nao mais "Planejado Vs Realizado" -- "Vs" e a abreviacao em
 * INGLES de "versus", corrigido pra "x" via TOKEN_FIXES acima, mesmo
 * criterio usado pelas legendas do grafico no site, ver analises-client.tsx
 * "Planejado x Realizado"). So cosmetico -- nao muda a chave usada para nada
 * alem de exibicao. */
private fun tituloSecao(chave: String): String {
    val comEspacos = chave.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
    val palavras = comEspacos.split(" ").map { palavra ->
        val capitalizada = palavra.replaceFirstChar { it.uppercase() }
        TOKEN_FIXES[capitalizada] ?: capitalizada
    }
    // Filtra tokens que viraram vazio (ex.: "R" de "valorR", ver TOKEN_FIXES)
    // -- sem isso sobrava espaço duplo ("Valor  Contrato") ou espaço solto
    // no fim/começo do título.
    return palavras.filter { it.isNotBlank() }.joinToString(" ")
}

// Campos monetários conhecidos do backend (getAnalisesCruzadas, ver
// lib/services/analises.ts no site) -- comparação por "contains" pra pegar
// variações (custoHa, custoHaTotal, custoFonte, custoFazenda, etc.) sem
// precisar listar toda combinação. Pedido do usuário ("em análises coloque
// a moeda"): o renderizador genérico (ObjetoCard) só imprimia o número cru,
// sem "R$", diferente do site que já formata esses mesmos campos como
// moeda (ver analises-client.tsx).
// "margem" (margemPorSaca) e "conciliado" (conciliadoFinanceiro,
// saldoCaixaInterno já cai em "saldo") entraram na varredura -- pedido do
// usuário ("ainda falta moeda"): eram os 2 campos monetários de
// getAnalisesCruzadas (lib/services/analises.ts) que nenhuma palavra-chave
// da lista cobria, então continuavam imprimindo o número cru sem "R$".
private val CAMPOS_MOEDA = setOf(
    "valor", "custo", "gasto", "receita", "despesa", "bruto", "liquido",
    "preco", "pago", "orcado", "rateado", "saldo", "folhamensal", "base",
    "margem", "conciliado",
    // Custo por hectare de CustoHaFonteItem (bloco "Custo Ha Por Fonte") --
    // pedido do usuário ("ainda falta moeda"): esses 5 campos SÃO valores em
    // R$/ha, mas o nome não carrega nenhuma das palavras-chave acima
    // ("financeiroDiretoHa", não "custoFinanceiroHa"), então continuavam
    // imprimindo o número cru. Nome completo em minúsculo (não só
    // "financeiro"/"frota" soltos) pra não capturar por engano campos de
    // CONTAGEM que também têm essas palavras (ex.: "lancamentosFrota",
    // "lancamentosSafra" -- esses são quantidade de lançamentos, não R$).
    "financeirodiretoha", "financeiroindiretoha", "frotaha", "safraha",
    "arrendamentoha",
)

private fun formatarMoedaBr(numero: Double): String =
    "R$ " + String.format(java.util.Locale("pt", "BR"), "%,.2f", numero)

/** Espelho de tituloSecao() só que pra nomes de CAMPO em vez de SEÇÃO --
 * pedido do usuário ("corrija toda a ortografia do módulo, há palavras que
 * são maiúsculas estão minúsculas e vice-versa"): antes o card genérico
 * (ObjetoCard) imprimia a chave crua do JSON ("custoHaTotal",
 * "percentualFolha") direto na tela; agora todo nome de campo passa por
 * aqui e sai formatado de forma consistente ("Custo Ha Total"), em vez de
 * uma mistura de camelCase cru com o resto da tela em Title Case normal. */
private fun tituloCampo(campo: String): String = tituloSecao(campo)

/** @param campo nome do campo no JSON -- usado só pra decidir formatação
 * (moeda/porcentagem), nunca alterado nem exibido por essa função. */
private fun valorParaTexto(campo: String, el: JsonElement): String = when (el) {
    is JsonNull -> "—"
    is JsonArray -> "${el.size} item(ns)"
    is JsonObject -> "${el.size} campo(s)"
    is JsonPrimitive -> {
        val texto = el.content
        if (texto.isBlank()) {
            "—"
        } else if (texto == "true" || texto == "false") {
            // Booleano cru ("pago", "ativo" etc. -- ver getAnalisesCruzadas
            // em lib/services/analises.ts) virando literalmente "true"/
            // "false" na tela -- pedido do usuário ("procure as palavras
            // true e false e substitua pelo nome que ela refere"). Análises
            // renderiza o JSON de forma genérica (ObjetoCard) e não passava
            // por nenhuma tradução Sim/Não, diferente do restante do app
            // (ver colType == "checkbox" em StatusStyle.kt).
            if (texto == "true") "Sim" else "Não"
        } else {
            val numero = texto.toDoubleOrNull()
            val campoMin = campo.lowercase()
            when {
                // "porcentagem" -- pedido do usuário: campos percentuais
                // (percentualFolha etc.) só mostravam o número cru (ex.:
                // "35.5"), sem "%", diferente do site (ver
                // analises-client.tsx: "...percentualFolha.toLocaleString
                // ("pt-BR")}%").
                numero != null && (campoMin.contains("percentual") || campoMin.contains("pct")) ->
                    "${String.format(java.util.Locale("pt", "BR"), "%.1f", numero)}%"
                numero != null && CAMPOS_MOEDA.any { campoMin.contains(it) } -> formatarMoedaBr(numero)
                else -> texto
            }
        }
    }
    else -> "—"
}

// Exportação CSV/PDF/Imprimir -- pedido do usuário ("construir CSV/PDF/
// Imprimir também" pra DRE/Análises). Análises não tem uma tabela única
// (é um JSON genérico com 15 cruzamentos diferentes, cada um com campos
// próprios -- ver comentário da AnalisesViewModel), então em vez de
// modelar 15 formatos de exportação diferentes, a exportação também é
// GENÉRICA: uma tabela "longa" (Seção / Item / Campo / Valor), uma linha
// por campo de cada card já mostrado na tela (mesmo texto de
// valorParaTexto usado no ObjetoCard) -- mesmo princípio da renderização.
private val ANALISES_EXPORT_COLUMNS = listOf(
    ColumnConfig(key = "secao", label = "Seção", type = "text"),
    ColumnConfig(key = "item", label = "Item", type = "text"),
    ColumnConfig(key = "campo", label = "Campo", type = "text"),
    ColumnConfig(key = "valor", label = "Valor", type = "text"),
)

private fun analisesExportConfig(): DomainConfig = DomainConfig(id = "analises", label = "Análises", columns = ANALISES_EXPORT_COLUMNS)

private fun analisesExportRecords(data: JsonObject): List<Map<String, String?>> {
    val linhas = mutableListOf<Map<String, String?>>()
    data.entries.forEach { (chave, valor) ->
        val secao = tituloSecao(chave)
        when (valor) {
            is JsonArray -> valor.forEachIndexed { indice, item ->
                if (item is JsonObject) {
                    item.entries.forEach { (campo, v) ->
                        linhas.add(mapOf("secao" to secao, "item" to (indice + 1).toString(), "campo" to tituloCampo(campo), "valor" to valorParaTexto(campo, v)))
                    }
                }
            }
            is JsonObject -> valor.entries.forEach { (campo, v) ->
                linhas.add(mapOf("secao" to secao, "item" to "", "campo" to tituloCampo(campo), "valor" to valorParaTexto(campo, v)))
            }
            else -> linhas.add(mapOf("secao" to secao, "item" to "", "campo" to "", "valor" to valorParaTexto(chave, valor)))
        }
    }
    return linhas
}

// Espelho de ModuleBlockSpec/ModuleCategoryBlock (DomainListScreen.kt) --
// mesmo padrão de bloco com título + Card, duplicado aqui em vez de
// compartilhado pra não arriscar mexer nos módulos que já estão
// funcionando (mesma decisão já tomada nos outros arquivos).
private data class AnalisesBlockSpec(
    val title: String,
    val vertical: Boolean,
    val content: @Composable () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalisesCategoryBlock(spec: AnalisesBlockSpec, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    Column(modifier = modifier) {
        Text(
            spec.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Card(modifier = Modifier.fillMaxWidth().let { if (fillHeight) it.weight(1f) else it }) {
            if (spec.vertical) {
                Column(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { spec.content() }
            } else {
                // EqualWidthBlockRow (ui/domain/ModuleIconRow.kt) -- achado
                // de auditoria: células de mesma largura, borda vertical
                // entre elas, numa linha só (ellipsis se não couber).
                EqualWidthBlockRow(modifier = Modifier.padding(8.dp)) { spec.content() }
            }
        }
    }
}

// Barra oval (pill) alternando entre categorias (Dados/Operações/Arquivos),
// mostrando só os ícones da categoria selecionada -- mesmo padrão de
// ModuleCategoryTabs (DomainListScreen.kt)/DreCategoryTabs (DreScreen.kt),
// duplicado aqui pelo mesmo motivo de AnalisesCategoryBlock (evitar mexer
// em código compartilhado entre os módulos).
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AnalisesCategoryTabs(blocks: List<AnalisesBlockSpec>, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(0) }
    val safeSelected = selected.coerceIn(0, blocks.size - 1)
    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            blocks.forEachIndexed { index, block ->
                SegmentedButton(
                    selected = safeSelected == index,
                    onClick = { selected = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = blocks.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ),
                    label = {
                        Text(
                            block.title,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.basicMarquee(),
                        )
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val active = blocks[safeSelected]
        if (active.vertical) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { active.content() }
        } else {
            // EqualWidthBlockRow (ui/domain/ModuleIconRow.kt) -- mesmo
            // ajuste de AnalisesCategoryBlock acima.
            EqualWidthBlockRow { active.content() }
        }
    }
}

@Composable
private fun ObjetoCard(obj: JsonObject) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Campo "label" tratado à parte -- pedido do usuário ("tem uma
            // palavra escrita label, substitua"): é o nome técnico que a
            // API usa pra identificar o item (ex.: "Safra 23/24 · Soja ·
            // Fazenda X", ver lib/services/analises.ts no site), sem
            // nenhum significado pro usuário final. Antes aparecia
            // literalmente "label: <valor>" na tela; agora vira o título do
            // card, sem o nome do campo na frente.
            obj["label"]?.let { labelValor ->
                Text(valorParaTexto("label", labelValor), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            obj.entries.forEach { (campo, valor) ->
                if (campo == "label") return@forEach
                Row {
                    // tituloCampo() em vez do nome cru do JSON -- pedido do
                    // usuário ("corrija a ortografia, há palavras maiúsculas
                    // que deveriam ser minúsculas e vice-versa"): antes
                    // aparecia a chave crua ("custoHaTotal"), misturando
                    // camelCase técnico com o resto da tela em Title Case.
                    Text("${tituloCampo(campo)}: ", style = MaterialTheme.typography.bodySmall)
                    Text(valorParaTexto(campo, valor), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SecaoAnalise(chave: String, valor: JsonElement) {
    Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tituloSecao(chave), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        when (valor) {
            is JsonArray -> {
                if (valor.isEmpty()) {
                    Text("Sem dados.", style = MaterialTheme.typography.bodySmall)
                } else {
                    valor.forEach { item ->
                        if (item is JsonObject) ObjetoCard(item)
                    }
                }
            }
            is JsonObject -> ObjetoCard(valor)
            else -> Text(valorParaTexto(chave, valor), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------------
// Gráficos comparativos (Task #621, gap de auditoria): o app só mostrava
// os 15 cruzamentos de getAnalisesCruzadas() como cards de texto cru
// (JSON genérico, ver SecaoAnalise/ObjetoCard acima) -- o site
// (analises-client.tsx) sempre teve 13 gráficos de barra reais,
// organizados em 4 abas (Financeiro/Estoque & Operação/Agronômico/
// Pessoas). Réplica aqui com SimpleBarChart (mesma réplica leve de
// gráfico, sem lib nova, já usada em DreScreen e nos módulos) --
// ADITIVO: os cards de texto cru continuam existindo embaixo, isso só
// acrescenta a visualização que faltava. SimpleBarChart não suporta
// barra empilhada (stackId) nem eixo duplo (ComposedChart) -- os 3
// gráficos que usavam essas features no site (Custo/ha por Fazenda e RH
// x Atividade de campo = empilhados; Chuva/Praga x Produtividade = eixo
// duplo) aparecem aqui como séries lado a lado, mesma aproximação já
// aceita nos outros módulos que usam este componente.

private fun JsonObject.strOf(key: String): String {
    val el = this[key]
    if (el == null || el is JsonNull) return ""
    return (el as? JsonPrimitive)?.content ?: ""
}

private fun JsonObject.numOf(key: String): Double {
    val el = this[key]
    if (el == null || el is JsonNull) return 0.0
    return (el as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
}

private fun JsonObject.numOrNullOf(key: String): Double? {
    val el = this[key]
    if (el == null || el is JsonNull) return null
    return (el as? JsonPrimitive)?.content?.toDoubleOrNull()
}

private fun JsonObject.arrOf(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

/** Uma série pronta pra SimpleBarChart a partir de uma lista de linhas
 * (cada linha é um JsonObject do cruzamento) + a chave do campo. */
private fun serieDe(items: List<JsonObject>, label: String, key: String, cor: androidx.compose.ui.graphics.Color): BarSeries =
    BarSeries(label = label, values = items.map { it.numOf(key) }, color = cor)

@Composable
private fun MiniChart(titulo: String, descricao: String, items: List<JsonObject>, labelKey: String, series: List<BarSeries>, isMoney: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (titulo.isNotBlank()) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Text(descricao, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(6.dp))
        if (items.isEmpty()) {
            Text("Sem dados suficientes ainda para este cruzamento.", style = MaterialTheme.typography.bodySmall)
        } else {
            SimpleBarChart(categories = items.map { it.strOf(labelKey) }, series = series, isMoney = isMoney)
        }
    }
}

private data class CustoHaRow(
    val farmName: String,
    val financeiroDiretoHa: Double,
    val financeiroIndiretoHa: Double,
    val frotaHa: Double,
    val safraHa: Double,
    val arrendamentoHa: Double,
)

private fun custoHaRows(items: List<JsonObject>): List<CustoHaRow> = items.map {
    CustoHaRow(
        farmName = it.strOf("farmName"),
        financeiroDiretoHa = it.numOf("financeiroDiretoHa"),
        financeiroIndiretoHa = it.numOf("financeiroIndiretoHa"),
        frotaHa = it.numOf("frotaHa"),
        safraHa = it.numOf("safraHa"),
        arrendamentoHa = it.numOf("arrendamentoHa"),
    )
}

private enum class AnalisesAba(val titulo: String) {
    FINANCEIRO("Financeiro"), ESTOQUE("Estoque & Operação"), AGRONOMICO("Agronômico"), PESSOAS("Pessoas"),
}

/** Bloco "Gráficos" -- colapsável, fechado por padrão (mesmo padrão do
 * GraficosBlock do site e do resto da plataforma), com abas internas
 * espelhando as 4 do site. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AnalisesGraficosBlock(data: JsonObject) {
    var open by remember { mutableStateOf(false) }
    var aba by remember { mutableStateOf(AnalisesAba.FINANCEIRO) }
    // Alternar Custo/ha <-> Custo/saca no gráfico "Custo/ha por Fazenda" --
    // mesmo botão do site (UnidadeToggleButton), usando a Produtividade
    // média (sc/ha) já calculada em climaVsProdutividade (aba Agronômico).
    var unidadeCusto by remember { mutableStateOf("ha") }

    val planejadoVsRealizado = data.arrOf("planejadoVsRealizado")
    val custoHaPorFonte = custoHaRows(data.arrOf("custoHaPorFonte"))
    val contratoVsTransporte = data.arrOf("contratoVsTransporte")
    val margemPorSaca = data.arrOf("margemPorSaca")
    val margemPorTon = margemPorSaca.filter { it.numOrNullOf("custoPorTon") != null || it.numOrNullOf("receitaPorTon") != null }
    val conciliacaoCaixaVsFinanceiro = data.arrOf("conciliacaoCaixaVsFinanceiro")
    val pedidoVsRecebimento = data.arrOf("pedidoVsRecebimento")
    val consumoEstoquePorOrigem = data.arrOf("consumoEstoquePorOrigem")
    val itemConsumoVsSaldo = data.arrOf("itemConsumoVsSaldo")
    val eficienciaMaquina = data.arrOf("eficienciaMaquinaPorHectare").filter { it.numOrNullOf("custoPorHectare") != null }
    val climaVsProdutividade = data.arrOf("climaVsProdutividade")
    val pragaVsProdutividade = data.arrOf("pragaVsProdutividade")
    val rhVsAtividadeCampo = data.arrOf("rhVsAtividadeCampo")

    val produtividadePorFazenda = remember(climaVsProdutividade) {
        val m = mutableMapOf<String, Double>()
        climaVsProdutividade.forEach { c ->
            val prod = c.numOrNullOf("produtividadeMedia")
            if (prod != null && prod > 0) m[c.strOf("local").trim().lowercase()] = prod
        }
        m
    }
    val custoHaExibido = if (unidadeCusto == "ha") custoHaPorFonte else custoHaPorFonte.mapNotNull { f ->
        val prod = produtividadePorFazenda[f.farmName.trim().lowercase()] ?: return@mapNotNull null
        f.copy(
            financeiroDiretoHa = f.financeiroDiretoHa / prod,
            financeiroIndiretoHa = f.financeiroIndiretoHa / prod,
            frotaHa = f.frotaHa / prod,
            safraHa = f.safraHa / prod,
            arrendamentoHa = f.arrendamentoHa / prod,
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Gráficos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
            if (open) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AnalisesAba.entries.forEachIndexed { index, item ->
                            SegmentedButton(
                                selected = aba == item,
                                onClick = { aba = item },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = AnalisesAba.entries.size),
                                label = {
                                    Text(item.titulo, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, modifier = Modifier.basicMarquee())
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    when (aba) {
                        AnalisesAba.FINANCEIRO -> {
                            MiniChart(
                                "Planejado x Realizado x Pago",
                                "Custo Orçado x Realizado (Safra) x efetivamente pago (Financeiro), por Safra/Cultura/Fazenda.",
                                planejadoVsRealizado, "label",
                                listOf(
                                    serieDe(planejadoVsRealizado, "Orçado", "orcado", defaultSeriesColor(0)),
                                    serieDe(planejadoVsRealizado, "Realizado (Safra)", "realizadoSafra", defaultSeriesColor(1)),
                                    serieDe(planejadoVsRealizado, "Pago (Financeiro)", "pagoFinanceiro", defaultSeriesColor(2)),
                                ),
                                isMoney = true,
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text("Custo/ha por Fazenda — fonte por fonte", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("De onde vem o custo (ha ou sc) de cada fazenda: Financeiro direto, indireto (rateado), Frota, Safra e Arrendamento.", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(bottom = 6.dp)) {
                                listOf("ha" to "R$/ha", "sc" to "R$/sc").forEachIndexed { index, (valor, rotulo) ->
                                    SegmentedButton(
                                        selected = unidadeCusto == valor,
                                        onClick = { unidadeCusto = valor },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                                        label = { Text(rotulo) },
                                    )
                                }
                            }
                            if (custoHaExibido.isEmpty()) {
                                Text("Sem dados suficientes ainda para este cruzamento.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                SimpleBarChart(
                                    categories = custoHaExibido.map { it.farmName },
                                    series = listOf(
                                        BarSeries("Financeiro direto", custoHaExibido.map { it.financeiroDiretoHa }, defaultSeriesColor(0)),
                                        BarSeries("Financeiro indireto", custoHaExibido.map { it.financeiroIndiretoHa }, defaultSeriesColor(1)),
                                        BarSeries("Frota", custoHaExibido.map { it.frotaHa }, defaultSeriesColor(2)),
                                        BarSeries("Safra", custoHaExibido.map { it.safraHa }, defaultSeriesColor(0)),
                                        BarSeries("Arrendamento", custoHaExibido.map { it.arrendamentoHa }, defaultSeriesColor(1)),
                                    ),
                                    isMoney = true,
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            MiniChart(
                                "Contrato x Volume Transportado",
                                "Volume contratado (Contratos) x volume transportado (Romaneios), por número de contrato.",
                                contratoVsTransporte, "noContrato",
                                listOf(
                                    serieDe(contratoVsTransporte, "Volume contratado", "volumeContratado", defaultSeriesColor(0)),
                                    serieDe(contratoVsTransporte, "Transportado (ton)", "volumeTransportadoTon", defaultSeriesColor(1)),
                                ),
                                isMoney = false,
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            MiniChart(
                                "Margem por saca",
                                "Custo/saca (Safra) x Receita/saca (Financeiro ÷ Colheita), por Safra/Cultura.",
                                margemPorSaca, "label",
                                listOf(
                                    serieDe(margemPorSaca, "Custo/saca", "custoPorSaca", defaultSeriesColor(2)),
                                    serieDe(margemPorSaca, "Receita/saca", "receitaPorSaca", defaultSeriesColor(0)),
                                    serieDe(margemPorSaca, "Margem/saca", "margemPorSaca", defaultSeriesColor(1)),
                                ),
                                isMoney = true,
                            )
                            if (margemPorTon.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                MiniChart(
                                    "Margem por tonelada",
                                    "Custo/ton x Receita/ton, por Safra/Cultura -- mesma conta de Margem por saca, em toneladas.",
                                    margemPorTon, "label",
                                    listOf(
                                        serieDe(margemPorTon, "Custo/ton", "custoPorTon", defaultSeriesColor(2)),
                                        serieDe(margemPorTon, "Receita/ton", "receitaPorTon", defaultSeriesColor(0)),
                                        serieDe(margemPorTon, "Margem/ton", "margemPorTon", defaultSeriesColor(1)),
                                    ),
                                    isMoney = true,
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            MiniChart(
                                "Conciliação: Caixa Interno x Financeiro",
                                "Saldo do Caixa Interno x lançamentos conciliados no Financeiro, por setor.",
                                conciliacaoCaixaVsFinanceiro, "setor",
                                listOf(
                                    serieDe(conciliacaoCaixaVsFinanceiro, "Saldo Caixa Interno", "saldoCaixaInterno", defaultSeriesColor(1)),
                                    serieDe(conciliacaoCaixaVsFinanceiro, "Conciliado (Financeiro)", "conciliadoFinanceiro", defaultSeriesColor(0)),
                                ),
                                isMoney = true,
                            )
                        }
                        AnalisesAba.ESTOQUE -> {
                            MiniChart(
                                "Pedido x Recebimento em Estoque",
                                "O que foi pedido/entregue (Pedidos) x o que virou entrada em Estoque.",
                                pedidoVsRecebimento, "item",
                                listOf(
                                    serieDe(pedidoVsRecebimento, "Qtd Pedida", "qtdPedida", defaultSeriesColor(0)),
                                    serieDe(pedidoVsRecebimento, "Qtd Entregue", "qtdEntregue", defaultSeriesColor(1)),
                                    serieDe(pedidoVsRecebimento, "Entrada real (Estoque)", "entradaEstoque", defaultSeriesColor(2)),
                                ),
                                isMoney = false,
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            MiniChart(
                                "Consumo de Estoque por origem",
                                "Quem mais tira do estoque geral: Frota, Safra ou Controle Interno.",
                                consumoEstoquePorOrigem, "origem",
                                listOf(serieDe(consumoEstoquePorOrigem, "Quantidade consumida", "qtd", defaultSeriesColor(0))),
                                isMoney = false,
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            MiniChart(
                                "Item mais consumido x Saldo restante",
                                "Top itens por consumo total x saldo atual em Estoque.",
                                itemConsumoVsSaldo, "item",
                                listOf(
                                    serieDe(itemConsumoVsSaldo, "Consumido", "consumido", defaultSeriesColor(2)),
                                    serieDe(itemConsumoVsSaldo, "Saldo atual", "saldoAtual", defaultSeriesColor(1)),
                                ),
                                isMoney = false,
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            MiniChart(
                                "Eficiência de máquina (R$/ha)",
                                "Custo total da máquina (Frota) dividido pelo hectare trabalhado (Safra).",
                                eficienciaMaquina, "frota",
                                listOf(serieDe(eficienciaMaquina, "Custo/ha", "custoPorHectare", defaultSeriesColor(0))),
                                isMoney = true,
                            )
                        }
                        AnalisesAba.AGRONOMICO -> {
                            MiniChart(
                                "Chuva x Produtividade",
                                "Chuva acumulada (mm) x Produtividade média (sc/ha), por fazenda.",
                                climaVsProdutividade, "local",
                                listOf(
                                    serieDe(climaVsProdutividade, "Chuva (mm)", "chuvaTotalMm", defaultSeriesColor(2)),
                                    serieDe(climaVsProdutividade, "Produtividade (sc/ha)", "produtividadeMedia", defaultSeriesColor(1)),
                                ),
                                isMoney = false,
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            MiniChart(
                                "Ocorrências de Praga x Produtividade",
                                "Quantas vezes cada talhão teve praga/doença registrada x a produtividade colhida.",
                                pragaVsProdutividade, "talhao",
                                listOf(
                                    serieDe(pragaVsProdutividade, "Ocorrências de praga", "ocorrenciasPraga", defaultSeriesColor(0)),
                                    serieDe(pragaVsProdutividade, "Produtividade (sc/ha)", "produtividadeMedia", defaultSeriesColor(1)),
                                ),
                                isMoney = false,
                            )
                        }
                        AnalisesAba.PESSOAS -> {
                            MiniChart(
                                "RH x Atividade de campo",
                                "Colaboradores ativos x lançamentos feitos em Safra/Frota/Controle Interno com o mesmo nome.",
                                rhVsAtividadeCampo, "colaborador",
                                listOf(
                                    serieDe(rhVsAtividadeCampo, "Safra", "lancamentosSafra", defaultSeriesColor(1)),
                                    serieDe(rhVsAtividadeCampo, "Frota", "lancamentosFrota", defaultSeriesColor(2)),
                                    serieDe(rhVsAtividadeCampo, "Controle Interno", "lancamentosControleInterno", defaultSeriesColor(0)),
                                ),
                                isMoney = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(label: String, value: String?, options: List<String>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value ?: "Todas",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = appFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todas") }, onClick = { onSelect(null); expanded = false })
            for (opt in options) {
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalisesScreen(onBack: () -> Unit, viewModel: AnalisesViewModel = viewModel()) {
    val analises by viewModel.analises
    val safrasDisponiveis by viewModel.safrasDisponiveis
    val culturasDisponiveis by viewModel.culturasDisponiveis
    val loading by viewModel.loading
    val offline by viewModel.offline
    val safra by viewModel.safra
    val cultura by viewModel.cultura
    val context = LocalContext.current
    // Filtros Safra/Cultura viram ícone (Filtro), mesmo padrão dos outros
    // módulos -- pedido do usuário ("dre, análises... transforme os
    // filtros em ícone").
    var filtrosOpen by remember { mutableStateOf(false) }
    // Recolher/expandir as seções de análise de uma vez -- pedido do usuário
    // ("insira o ícone recolher/expandir os blocos em dre e análises"),
    // mesmo padrão do allExpanded já usado nas listas de lançamentos
    // (DomainListScreen/FinanceiroScreen) e agora também no DRE. Começa
    // FECHADO -- pedido do usuário ("sempre aparecer a tela vazia, só
    // expandir quando clicar no ícone").
    var contentExpanded by remember { mutableStateOf(false) }
    // Tabela/Coluna -- mesmo botão único (achado de auditoria, pedido do
    // usuário: "coloque em todos os módulos"). Análises não tem um registro
    // único por trás (é um JSON genérico com 15 cruzamentos diferentes), a
    // tabela reaproveita a mesma exportação "longa" (Seção/Item/Campo/
    // Valor) já usada em Excel/PDF/Imprimir, sem coluna de ações.
    var tableView by remember { mutableStateOf(false) }
    val tableHScroll = remember { androidx.compose.foundation.ScrollState(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Análises", color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                // Nuvem/Imprimir no canto superior direito -- mesmo padrão
                // já aplicado em todos os outros módulos (inclusive DRE
                // acima), pedido do usuário ("aplique o mesmo padrão... da
                // aba lançamentos").
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            // Fazenda removida / Imprimir antes da Nuvem --
                            // pedido do usuário.
                            if (analises != null && analises!!.entries.isNotEmpty()) {
                                IconButton(onClick = { HtmlPrinter.printList(context, analisesExportConfig(), analisesExportRecords(analises!!)) }) {
                                    Icon(Icons.Filled.Print, contentDescription = "Imprimir", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(onClick = {
                                val msg = if (offline) NetworkStatus.failureMessage(context) else "Conectado -- dados sincronizados com o servidor."
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(if (offline) Icons.Filled.CloudOff else Icons.Filled.Cloud, contentDescription = "Nuvem", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val data = analises
        val temRegistros = data != null && data.entries.isNotEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "analises-icon-row") {
                val dadosBlock = AnalisesBlockSpec("Dados", vertical = false) {
                    LabeledIconButton(
                        icon = Icons.Filled.FilterAlt,
                        label = "Filtros",
                        // Sem tint proprio -- herda onSurface (preto/branco),
                        // pedido do usuario ("tire o fundo verde de todos os
                        // blocos individuais").
                        onClick = { filtrosOpen = !filtrosOpen },
                    )
                    // Ícone de recolher/expandir movido pra dentro de Dados,
                    // junto do Filtro -- mesmo ajuste do DRE (DreScreen.kt),
                    // pedido do usuário ("no módulo análises aplique
                    // exatamente o que aplicou em dre").
                    if (temRegistros) {
                        LabeledIconButton(
                            icon = if (contentExpanded) Icons.Filled.KeyboardDoubleArrowUp else Icons.Filled.KeyboardDoubleArrowDown,
                            label = if (contentExpanded) "Recolher" else "Expandir",
                            onClick = { contentExpanded = !contentExpanded },
                        )
                        LabeledIconButton(
                            icon = if (tableView) Icons.Filled.ViewAgenda else Icons.Filled.TableChart,
                            label = if (tableView) "Bloco" else "Tabela",
                            onClick = { tableView = !tableView },
                        )
                    }
                }
                val operacoesBlock = AnalisesBlockSpec("Operações", vertical = false) {
                    LabeledIconButton(
                        icon = Icons.Filled.Refresh,
                        label = "Atualizar",
                        loading = loading,
                        onClick = { viewModel.refresh() },
                    )
                }
                val arquivosBlock = AnalisesBlockSpec("Arquivos", vertical = false) {
                    if (temRegistros) {
                        LabeledIconButton(
                            icon = Icons.Filled.GridOn,
                            label = "Excel",
                            onClick = { exportXlsx(context, "Análises", ANALISES_EXPORT_COLUMNS, analisesExportRecords(data!!)) },
                        )
                        LabeledIconButton(
                            icon = Icons.Filled.PictureAsPdf,
                            label = "PDF",
                            onClick = { HtmlPrinter.exportPdfDirect(context, analisesExportConfig(), analisesExportRecords(data!!)) },
                        )
                    }
                }
                // Nuvem/Imprimir saíram daqui -- foram promovidos pro canto
                // superior direito da TopAppBar (ver Scaffold acima), mesmo
                // padrão já usado em todos os outros módulos (inclusive
                // DRE). Dados/Operações/Arquivos viraram uma barra oval
                // (pill) que alterna entre categorias -- pedido do usuário
                // ("aplique o mesmo padrão... da aba lançamentos com a
                // barra oval e as categorias os ícones no topo").
                AnalisesCategoryTabs(listOf(dadosBlock, operacoesBlock, arquivosBlock), modifier = Modifier.fillMaxWidth())
            }
            if (filtrosOpen) {
                item {
                    // Cultura ao lado de Safra, mesma linha, blocos separados --
                    // pedido do usuário ("análises coloque filtro cultura,
                    // dividindo a mesma linha com o filtro safra, blocos
                    // separados"), mesmo padrão do DRE (DreScreen.kt).
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            FilterDropdown("Safra", safra, safrasDisponiveis) { viewModel.setSafra(it) }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            FilterDropdown("Cultura", cultura, culturasDisponiveis) { viewModel.setCultura(it) }
                        }
                    }
                }
            }
            if (offline) {
                item {
                    Text(
                        NetworkStatus.failureMessage(context),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (data == null) {
                item {
                    Text(if (loading) "Carregando..." else "Sem dados ainda. Conecte-se à internet e atualize.")
                }
            } else if (tableView) {
                item(key = "table-header") { RecordTableHeader(ANALISES_EXPORT_COLUMNS, tableHScroll, showActions = false) }
                val linhas = analisesExportRecords(data)
                items(linhas.size, key = { linhas[it].hashCode() }) { i ->
                    RecordTableRow(columns = ANALISES_EXPORT_COLUMNS, record = linhas[i], domainId = "", hScroll = tableHScroll)
                }
            } else if (contentExpanded) {
                item(key = "analises-graficos") { AnalisesGraficosBlock(data) }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                data.entries.forEachIndexed { index, (chave, valor) ->
                    item(key = chave) { SecaoAnalise(chave, valor) }
                    if (index < data.entries.size - 1) {
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                }
            }
        }
    }
}
