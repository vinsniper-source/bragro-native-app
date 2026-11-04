package com.bragro.mobile.ui.fieldview

import androidx.compose.foundation.layout.size
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.SearchableDropdownField
import com.bragro.mobile.ui.theme.appFieldColors
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.bragro.mobile.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.kml.ParsedPolygon
import com.bragro.mobile.data.kml.parseKmlOrKmz
import com.bragro.mobile.data.kml.polygonAreaHectares
import com.bragro.mobile.data.kml.polygonToGeoJson
import com.bragro.mobile.data.local.FarmEntity
import com.bragro.mobile.data.model.FieldBoundaryDto
import com.bragro.mobile.data.model.FieldviewResponse
import com.bragro.mobile.data.model.ProviderIntegrationDto
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.FieldviewRepository
import com.bragro.mobile.data.repo.IntegrationModule
import com.bragro.mobile.data.repo.ProviderIntegrationRepository
import com.bragro.mobile.ui.domain.IntegrationBusy
import com.bragro.mobile.ui.domain.ProviderIntegrationCard
import com.bragro.mobile.ui.domain.StatusBadge
import com.bragro.mobile.ui.domain.displayValueFor
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon as OsmPolygon

// FieldView (Task #107/#110) -- talhões (com área calculada a partir do
// KML/KMZ importado), status de Safra por talhão e status de Frota por
// máquina, mapa nativo (osmdroid) e importação nativa de KML/KMZ direto
// no aparelho (ver data/kml/KmlParser.kt), sem depender mais do site.
/** v80 -- imagem NDVI de um talhão para sobrepor no mapa. */
data class CamadaNdvi(val id: String, val imagem: android.graphics.Bitmap, val sul: Double, val oeste: Double, val norte: Double, val leste: Double, val data: String)

class FieldviewViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = FieldviewRepository(app)
    private val configRepository = ConfigRepository(app)
    // Card "Acesso automático via prestadora de serviço" (Task #341/#54) --
    // ver ProviderIntegrationRepository.kt/ProviderIntegrationCard.kt.
    private val integrationRepository = ProviderIntegrationRepository(app, IntegrationModule.FIELDVIEW)
    // NDVI via satélite (Task #653, gap analysis eixo BI) -- mesmo card,
    // segundo provedor (imagens de satélite em vez de prestadora de
    // FieldView), ver fieldview-client.tsx no site (segundo
    // ProviderIntegrationCard da tela).
    private val ndviIntegrationRepository = ProviderIntegrationRepository(app, IntegrationModule.SATELITE_NDVI)

    var data = mutableStateOf<FieldviewResponse?>(null)
        private set
    var loading = mutableStateOf(false)
        private set
    var importing = mutableStateOf(false)
        private set
    var importError = mutableStateOf<String?>(null)
        private set
    // Lista de fazendas do cadastro (mesma fonte já usada em NfeImportScreen,
    // ver ConfigRepository.farms()/bootstrap) -- alimenta o dropdown opcional
    // "Fazenda (opcional)" do diálogo de import de KML/KMZ.
    var farms = mutableStateOf<List<FarmEntity>>(emptyList())
        private set
    var integration = mutableStateOf<ProviderIntegrationDto?>(null)
        private set
    var integrationBusy = mutableStateOf<IntegrationBusy?>(null)
        private set
    var integrationMessage = mutableStateOf<String?>(null)
        private set
    var ndviIntegration = mutableStateOf<ProviderIntegrationDto?>(null)
        private set
    var ndviIntegrationBusy = mutableStateOf<IntegrationBusy?>(null)
        private set
    var ndviIntegrationMessage = mutableStateOf<String?>(null)
        private set

    // v80 -- NDVI próprio por talhão (null = calculando) e camadas no mapa.
    var ndvi = mutableStateOf<Map<String, com.bragro.mobile.data.model.NdviTalhaoDto?>>(emptyMap())
        private set
    var camadasNdvi = mutableStateOf<List<CamadaNdvi>>(emptyList())
        private set
    var mostrarNdvi = mutableStateOf(false)
    private var ndviJob: kotlinx.coroutines.Job? = null

    /** Calcula um talhão de cada vez (cada um lê imagens de satélite). */
    fun calcularNdvi(ids: List<String>) {
        if (ndviJob?.isActive == true) return
        ndviJob = viewModelScope.launch {
            for (id in ids) {
                if (ndvi.value[id] != null) continue
                ndvi.value = ndvi.value + (id to null)
                val r = repository.ndvi(id)
                ndvi.value = ndvi.value + (id to r)
            }
        }
    }

    fun verNdviNoMapa(id: String, r: com.bragro.mobile.data.model.NdviResultadoDto): Boolean {
        val png = r.png ?: return false
        val lim = r.limites?.takeIf { it.size == 2 && it.all { p -> p.size == 2 } } ?: return false
        val bytes = runCatching { android.util.Base64.decode(png, android.util.Base64.DEFAULT) }.getOrNull() ?: return false
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        val camada = CamadaNdvi(id, bmp, sul = lim[0][0], oeste = lim[0][1], norte = lim[1][0], leste = lim[1][1], data = r.cena.data)
        camadasNdvi.value = camadasNdvi.value.filter { it.id != id } + camada
        mostrarNdvi.value = true
        return true
    }

    fun load() {
        loading.value = true
        viewModelScope.launch {
            data.value = repository.fetch()
            loading.value = false
        }
        viewModelScope.launch { farms.value = configRepository.farms() }
        viewModelScope.launch { integration.value = integrationRepository.get() }
        viewModelScope.launch { ndviIntegration.value = ndviIntegrationRepository.get() }
    }

    fun saveIntegration(provedor: String, apiKey: String) {
        integrationBusy.value = IntegrationBusy.SALVANDO
        integrationMessage.value = null
        viewModelScope.launch {
            val ok = integrationRepository.save(provedor, apiKey)
            integrationMessage.value = if (ok) "Credencial salva." else "Falha ao salvar credencial. Confira a conexão e tente de novo."
            if (ok) integration.value = integrationRepository.get()
            integrationBusy.value = null
        }
    }

    fun disconnectIntegration() {
        integrationBusy.value = IntegrationBusy.DESCONECTANDO
        integrationMessage.value = null
        viewModelScope.launch {
            val ok = integrationRepository.disconnect()
            if (ok) {
                integration.value = integrationRepository.get()
                integrationMessage.value = "Integração desconectada."
            } else {
                integrationMessage.value = "Falha ao desconectar. Confira a conexão e tente de novo."
            }
            integrationBusy.value = null
        }
    }

    fun syncIntegration() {
        integrationBusy.value = IntegrationBusy.SINCRONIZANDO
        integrationMessage.value = null
        viewModelScope.launch {
            val result = integrationRepository.sync()
            integrationMessage.value = result.mensagem
            integration.value = integrationRepository.get()
            integrationBusy.value = null
        }
    }

    fun saveNdviIntegration(provedor: String, apiKey: String) {
        ndviIntegrationBusy.value = IntegrationBusy.SALVANDO
        ndviIntegrationMessage.value = null
        viewModelScope.launch {
            val ok = ndviIntegrationRepository.save(provedor, apiKey)
            ndviIntegrationMessage.value = if (ok) "Credencial salva." else "Falha ao salvar credencial. Confira a conexão e tente de novo."
            if (ok) ndviIntegration.value = ndviIntegrationRepository.get()
            ndviIntegrationBusy.value = null
        }
    }

    fun disconnectNdviIntegration() {
        ndviIntegrationBusy.value = IntegrationBusy.DESCONECTANDO
        ndviIntegrationMessage.value = null
        viewModelScope.launch {
            val ok = ndviIntegrationRepository.disconnect()
            if (ok) {
                ndviIntegration.value = ndviIntegrationRepository.get()
                ndviIntegrationMessage.value = "Integração desconectada."
            } else {
                ndviIntegrationMessage.value = "Falha ao desconectar. Confira a conexão e tente de novo."
            }
            ndviIntegrationBusy.value = null
        }
    }

    fun syncNdviIntegration() {
        ndviIntegrationBusy.value = IntegrationBusy.SINCRONIZANDO
        ndviIntegrationMessage.value = null
        viewModelScope.launch {
            val result = ndviIntegrationRepository.sync()
            ndviIntegrationMessage.value = result.mensagem
            ndviIntegration.value = ndviIntegrationRepository.get()
            ndviIntegrationBusy.value = null
        }
    }

    /** Importa um talhão parseado de um KML/KMZ -- converte pra GeoJSON e
     * calcula a área aproximada localmente (KmlParser.kt), manda pro
     * servidor (upsert por [orgId, talhao]) e recarrega a lista em caso
     * de sucesso. [farmId] (opcional) vincula o talhão importado a uma
     * fazenda do cadastro (ver dropdown em ImportBoundaryDialog) -- null/
     * vazio quando o usuário não escolhe nenhuma, mesmo comportamento
     * opcional do backend. */
    fun importBoundary(talhao: String, polygon: ParsedPolygon, farmId: String?, onDone: (Boolean) -> Unit) {
        importing.value = true
        importError.value = null
        viewModelScope.launch {
            val geojson = polygonToGeoJson(polygon)
            val areaHa = polygonAreaHectares(polygon.points)
            val ok = repository.importBoundary(talhao, polygon.name, geojson, areaHa, farmId?.takeIf { it.isNotBlank() })
            if (ok) {
                data.value = repository.fetch()
            } else {
                importError.value = "Falha ao importar o contorno. Confira a conexão e tente de novo."
            }
            importing.value = false
            onDone(ok)
        }
    }

    /** Registro MANUAL de talhão, sem KML/KMZ nenhum -- pedido do usuário
     * ("fieldview não tem a opção de lançar manualmente só automaticamente...
     * insira o botão + em talhões, máquinas e fazendas/kml"). Reaproveita o
     * MESMO endpoint/upsert de importBoundary acima, só que com um GeoJSON
     * vazio (GeometryCollection sem geometrias) em vez do polígono parseado
     * do arquivo -- o mapa (osmdroid) simplesmente não desenha contorno
     * nenhum pra esse talhão, sem quebrar nada (ver MapPolygonsView abaixo,
     * que já ignora silenciosamente uma geometria sem coordenadas). */
    fun manualBoundary(talhao: String, nome: String?, farmId: String?, onDone: (Boolean) -> Unit) {
        importing.value = true
        importError.value = null
        viewModelScope.launch {
            val emptyGeoJson = kotlinx.serialization.json.buildJsonObject {
                put("type", kotlinx.serialization.json.JsonPrimitive("GeometryCollection"))
                put("geometries", kotlinx.serialization.json.buildJsonArray {})
            }
            val ok = repository.importBoundary(talhao, nome, emptyGeoJson, null, farmId?.takeIf { it.isNotBlank() })
            if (ok) {
                data.value = repository.fetch()
            } else {
                importError.value = "Falha ao lançar o talhão. Confira a conexão e tente de novo."
            }
            importing.value = false
            onDone(ok)
        }
    }
}

// "fazendaId" adicionado -- bug real reportado pelo usuário (screenshot):
// mostrava o cuid cru da fazenda ("cmrphel3k000njx04ml1uqa7j") junto com
// "local" (que já é o NOME amigável da mesma fazenda, ex.: "FAZ. SÃO
// PEDRO") -- puro ruído redundante, nenhum ganho em mostrar os dois.
private val IGNORED_KEYS = setOf("id", "orgId", "criadoEm", "editadoEm", "updatedAt", "fazendaId")

// Datas cruas (Safra/Frota) chegam como ISO completo com fuso
// ("2026-08-05T00:00:00.000Z") -- outro bug real reportado ("está com fuso
// horário"): esta tela não tem um ColumnConfig por trás pra saber QUAL
// campo é data (diferente do formulário genérico), então detecta pelo
// FORMATO do valor em vez do nome da chave -- qualquer valor que comece com
// "AAAA-MM-DDT" é tratado como data e formatado em DD/MM/AAAA (mesmo
// displayValueFor colType="date" já usado no resto do app), sem o "T...Z"
// cru vazando pra tela.
private val ISO_DATETIME_PREFIX = Regex("^\\d{4}-\\d{2}-\\d{2}T")

// Rótulos em português pra cada chave crua (Safra/Frota) -- bug real
// encontrado (usuário: "corrigir as palavras inglês para o português"):
// RawRecordFields mostrava a chave JSON crua ("os", "kmOdometro",
// "custoPorKm"...) como rótulo, em vez do label em português já usado pelo
// mesmo campo no domínio genérico (ver registry.ts, domínios "frota" e
// "safra"). Mesmo texto exato dos rótulos de lá, pra não inventar
// terminologia nova.
private val RAW_FIELD_LABELS: Map<String, String> = mapOf(
    "os" to "O.S.",
    "data" to "Data",
    "entrada" to "Entrada",
    "proxRevisao" to "Próxima Revisão",
    "cultura" to "Cultura",
    "safra" to "Safra",
    "local" to "Local",
    "areaTotal" to "Área Total (ha)",
    "talhao" to "Talhão",
    "colaborador" to "Colaborador",
    "responsavel" to "Responsável",
    "funcao" to "Função",
    "frota" to "Frota/Equipamento",
    "oficina" to "Oficina",
    "operacao" to "Operação",
    "sistema" to "Sistema",
    "kmOdometro" to "Km/Odômetro",
    "horimetro" to "Horímetro",
    "total" to "Total (R$)",
    "custoPorKm" to "Custo por Km (R$)",
    "custoPorHora" to "Custo por Hora (R$)",
    "revisaoVencida" to "Revisão Vencida",
    "acumFrota" to "Acumulado (R$)",
    "emEstoque" to "Em Estoque",
    "alertaEstoque" to "Alerta Estoque",
    "dias" to "Dias",
    "status" to "Status",
    // v18 -- demais chaves que apareciam cruas no card do talhão.
    "dataInicio" to "Data início",
    "dataFim" to "Data fim",
    "hectare" to "Hectare",
    "categoria" to "Categoria",
    "subcategoriaVariedade" to "Subcategoria/Variedade",
    "subcategoria" to "Subcategoria",
    "item" to "Item",
    "unidade" to "Unidade",
    "qtd" to "Qtd",
    "unitario" to "Unitário",
    "produtividadeScTon" to "Produtividade (sc/t)",
    "observacoes" to "Observações",
    "vazaoDose" to "Vazão/Dose",
    "planejado" to "Planejado",
    "realizado" to "Realizado",
    "custoScTon" to "Custo sc/t",
    "custoHa" to "Custo/ha",
    "variacao" to "Variação",
    "desvio" to "Desvio",
    "fornecedor" to "Fornecedor",
    "valor" to "Valor",
)

// v18 -- chaves em R$ (R$ 00.000,00) e chaves numéricas (1.234,5).
private val RAW_MONEY_KEYS = setOf("unitario", "planejado", "realizado", "custoScTon", "custoHa", "variacao", "total", "custoPorKm", "custoPorHora", "acumFrota", "valor")
private val RAW_NUMBER_KEYS = setOf("hectare", "qtd", "produtividadeScTon", "vazaoDose", "areaTotal", "kmOdometro", "horimetro", "dias")
private val RAW_LONG_KEYS = setOf("observacoes", "obs", "descricao")

// Campos checkbox cujo valor cru vem "true"/"false" -- precisam de
// colType="checkbox" em displayValueFor pra virar "Sim"/"Não" em vez do
// literal cru (outro bug real: "revisaoVencida: false" aparecia na tela).
private val RAW_CHECKBOX_KEYS = setOf("revisaoVencida", "emEstoque", "alertaEstoque")

/** Mostra os campos primitivos de um registro cru (Safra/Frota), exceto
 * campos técnicos. v18 -- duas colunas; rótulo em cinza com o valor logo na
 * frente, quebrando em quantas linhas precisar (nada cortado); textos longos
 * ocupam a largura toda; R$ 00.000,00 e números no padrão brasileiro. */
@Composable
private fun RawRecordFields(obj: JsonObject) {
    val ptBr = java.util.Locale("pt", "BR")
    val itens = obj.entries
        .filter { (k, v) -> k !in IGNORED_KEYS && (v as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.isNotBlank() == true }
        .map { (k, v) ->
            val raw = v.jsonPrimitive.contentOrNull ?: ""
            val label = RAW_FIELD_LABELS[k] ?: k.replaceFirstChar { it.uppercase() }
            val n = raw.toDoubleOrNull()
            val valor = when {
                k == "status" -> raw
                k in RAW_MONEY_KEYS && n != null -> java.text.NumberFormat.getCurrencyInstance(ptBr).format(n).replace('\u00A0', ' ')
                k == "hectare" || k == "areaTotal" -> if (n != null) "${java.text.NumberFormat.getNumberInstance(ptBr).format(n)} ha" else raw
                k in RAW_NUMBER_KEYS && n != null -> java.text.NumberFormat.getNumberInstance(ptBr).format(n)
                else -> displayValueFor(k, raw, when {
                    k in RAW_CHECKBOX_KEYS -> "checkbox"
                    ISO_DATETIME_PREFIX.containsMatchIn(raw) -> "date"
                    else -> "text"
                })
            }
            Triple(k, label, valor)
        }
    val status = itens.firstOrNull { it.first == "status" }
    val longos = itens.filter { it.first in RAW_LONG_KEYS || (it.first != "status" && it.third.length > 40) }
    val curtos = itens.filter { it != status && it !in longos }
    val mut = MaterialTheme.colorScheme.onSurfaceVariant
    @Composable
    fun celula(label: String, valor: String, mod: Modifier) {
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = mut)); append("$label: "); pop()
                append(valor)
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = mod,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        curtos.chunked(2).forEach { par ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                celula(par[0].second, par[0].third, Modifier.weight(1f))
                if (par.size > 1) celula(par[1].second, par[1].third, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            }
        }
        if (status != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Status: ", style = MaterialTheme.typography.bodySmall, color = mut)
                StatusBadge(status.third)
            }
        }
        longos.forEach { celula(it.second, it.third, Modifier.fillMaxWidth()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldviewScreen(onBack: () -> Unit, onNavigateToFrota: () -> Unit = {}, onOpenPrescricao: () -> Unit = {}, viewModel: FieldviewViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.load() }
    // osmdroid exige um user agent nao-vazio (senao os servidores de tile
    // do OSM podem rejeitar a requisicao) e se beneficia de um diretorio
    // de cache proprio -- configuracao unica, feita aqui (roda de novo a
    // cada entrada na tela, mas e idempotente/barata).
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = "BRAgro/${com.bragro.mobile.BuildConfig.VERSION_NAME} (${context.packageName})"
            osmdroidBasePath = context.cacheDir
        }
    }
    val data by viewModel.data
    val loading by viewModel.loading
    val importing by viewModel.importing
    val importError by viewModel.importError
    val farms by viewModel.farms
    val integration by viewModel.integration
    val integrationBusy by viewModel.integrationBusy
    val integrationMessage by viewModel.integrationMessage
    val ndviIntegration by viewModel.ndviIntegration
    val ndviIntegrationBusy by viewModel.ndviIntegrationBusy
    val ndviIntegrationMessage by viewModel.ndviIntegrationMessage
    val ndviTalhoes by viewModel.ndvi
    val camadasNdvi by viewModel.camadasNdvi
    val mostrarNdvi by viewModel.mostrarNdvi
    var contaSateliteAberta by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Talhões", "Máquinas", "Fazendas/KML")
    var pendingPolygons by remember { mutableStateOf<List<ParsedPolygon>>(emptyList()) }
    var pendingIndex by remember { mutableStateOf(0) }
    // Diálogo de registro MANUAL de talhão (sem KML/KMZ) -- pedido do
    // usuário. Aberto pelo botão "+" nas abas Talhões e Fazendas/KML.
    var manualDialogOpen by remember { mutableStateOf(false) }
    // v40 -- a lista de talhões só aparece depois de tocar no ícone Talhões.
    var listaAberta by remember { mutableStateOf(false) }
    var prestadoraAberta by remember { mutableIntStateOf(0) }

    val kmlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val parsed = parseKmlOrKmz(context, uri)
            if (parsed.isNotEmpty()) {
                pendingPolygons = parsed
                pendingIndex = 0
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.BackNavButton()
                    }
                },
                title = { androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                    // v21 -- ícone verde do módulo ao lado do título.
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        com.bragro.mobile.ui.domain.IconeNegrito(androidx.compose.material.icons.Icons.Outlined.Map, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, size = 22.dp, modifier = androidx.compose.ui.Modifier.padding(end = 8.dp, bottom = 3.dp))

                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Talhões e Mapas", color = MaterialTheme.colorScheme.onSurface)
                    }
                
                    }
                } },
                actions = {
                    // v18 -- ícone de Prescrição saiu do cabeçalho; fica só
                    // em Arquivos > Prescrição (mesmo ícone).
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.HomeNavButton()
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Padrão v7: Painel de monitoramento + Ações; o resto (prestadora,
            // NDVI, abas) virou ícone em Ações ou janela.
            val talhoes = data?.talhaoStatus.orEmpty()
            val boundaries = data?.boundaries.orEmpty()
            fun cultura(o: JsonObject) = (o["cultura"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty().uppercase()
            val comContorno = boundaries.count { b -> b.geojson != null && b.geojson.toString() !in setOf("{}", "null") }
            com.bragro.mobile.ui.domain.PainelMonitoramento(
                com.bragro.mobile.ui.domain.PainelModulo(
                    titulo = "Talhões e Mapas",
                    subtitulo = "${talhoes.size} talhões · ${farms.size} fazendas",
                    kpis = listOf(
                        com.bragro.mobile.ui.domain.PainelKpi("Talhões", talhoes.size.toDouble(), "numero", null, "com registros", "Talhões"),
                        com.bragro.mobile.ui.domain.PainelKpi("Com contorno", comContorno.toDouble(), "numero", null, "aparecem no mapa", "Mapa"),
                        com.bragro.mobile.ui.domain.PainelKpi("Soja", talhoes.count { cultura(it).contains("SOJA") }.toDouble(), "numero", null, "talhões", "Talhões"),
                        com.bragro.mobile.ui.domain.PainelKpi("Cana", talhoes.count { cultura(it).contains("CANA") }.toDouble(), "numero", null, "talhões", "Talhões"),
                        com.bragro.mobile.ui.domain.PainelKpi("Máquinas", data?.maquinaStatus.orEmpty().size.toDouble(), "numero", null, "com registros", "Máquinas"),
                    ),
                    barras = emptyList(),
                ),
                onKpi = { k -> tab = when (k.destino) { "Máquinas" -> 1; "Mapa" -> 2; else -> 0 }; listaAberta = true },
            )
            // v14: abas Dados / Operações / Arquivos, iguais aos outros módulos.
            com.bragro.mobile.ui.domain.AcoesEmAbas(
                dados = {
                    val tabIcons = listOf(Icons.Outlined.Eco, Icons.Outlined.DirectionsCar, Icons.Outlined.Map)
                    tabs.forEachIndexed { i, label ->
                        com.bragro.mobile.ui.domain.LabeledIconButton(icon = tabIcons[i], label = if (i == 2) "Mapa/KML" else label, active = tab == i && listaAberta, onClick = { if (tab == i && listaAberta) listaAberta = false else { tab = i; listaAberta = true } })
                    }
                },
                operacoes = {
                    com.bragro.mobile.ui.domain.LabeledIconButton(
                        icon = Icons.Outlined.UploadFile, label = "KML/KMZ", loading = importing,
                        onClick = { kmlPicker.launch(arrayOf("application/vnd.google-earth.kml+xml", "application/vnd.google-earth.kmz", "application/octet-stream", "*/*")) },
                    )
                    com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.EditLocationAlt, label = "+ Talhão", onClick = { manualDialogOpen = true })
                    com.bragro.mobile.ui.domain.LabeledIconButton(icon = com.bragro.mobile.ui.domain.IconesVazados.OfflineBolt, label = "Prestadora", active = integration != null, onClick = { prestadoraAberta = 1 })
                    com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.Public, label = "NDVI", active = camadasNdvi.isNotEmpty(), onClick = { prestadoraAberta = 2 })
                },
                arquivos = {
                    com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.Map, label = "Prescrição", onClick = onOpenPrescricao)
                    com.bragro.mobile.ui.domain.ArquivosTabela("Talhões e Mapas", listOf("Talhão", "Nome", "Área (ha)")) {
                        boundaries.map { b -> listOf(b.talhao, b.nome.orEmpty(), b.areaHaCalc?.let { "%,.2f".format(java.util.Locale("pt", "BR"), it) }.orEmpty()) }
                    }
                },
            )
            com.bragro.mobile.ui.domain.CabecalhoLancamentos(
                total = when (tab) { 0 -> talhoes.size; 1 -> data?.maquinaStatus.orEmpty().size; else -> boundaries.size },
                aberto = listaAberta,
                titulo = tabs[tab],
            ) { listaAberta = !listaAberta }
            if (listaAberta) Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                }
                data == null -> Text("Sem conexão. Não foi possível carregar.", modifier = Modifier.padding(16.dp))
                else -> when (tab) {
                    0 -> TalhaoStatusList(data!!.talhaoStatus)
                    1 -> MaquinaStatusList(data!!.maquinaStatus)
                    else -> FazendasKmlTab(
                        boundaries = data!!.boundaries,
                        farms = farms,
                        camadasNdvi = camadasNdvi,
                        mostrarNdvi = mostrarNdvi,
                        onMostrarNdvi = { viewModel.mostrarNdvi.value = it },
                        onPolygonDrawn = { poly -> pendingPolygons = listOf(poly); pendingIndex = 0 },
                    )
                }
            }
            }
        }
    }

    if (prestadoraAberta != 0) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { prestadoraAberta = 0 },
            modifier = Modifier.fillMaxWidth(0.96f),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            title = { Text(if (prestadoraAberta == 1) "Prestadora (talhões/KML)" else "NDVI dos talhões") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (prestadoraAberta == 1) ProviderIntegrationCard(
                        providers = listOf("Climate FieldView", "John Deere Operations Center", "Outro", "Trimble Ag Software"),
                        titulo = "Acesso automático via prestadora (talhões/KML)",
                        descricao = "Talhões importados por KML/KMZ. A conexão automática depende de parceria com o fabricante.",
                        integration = integration,
                        busy = integrationBusy,
                        syncMessage = integrationMessage,
                        onSave = { provedor, apiKey -> viewModel.saveIntegration(provedor, apiKey) },
                        onDisconnect = { viewModel.disconnectIntegration() },
                        onSync = { viewModel.syncIntegration() },
                    ) else {
                        // v80 (mockup v80 aprovado) -- NDVI próprio de cada talhão.
                        val bs = data?.boundaries.orEmpty()
                        LaunchedEffect(bs.map { it.id }) {
                            viewModel.calcularNdvi(bs.filter { temContorno(it) }.map { it.id })
                        }
                        NdviLista(
                            boundaries = bs,
                            culturaDoTalhao = { t ->
                                data?.talhaoStatus.orEmpty().firstOrNull { (it["talhao"] as? kotlinx.serialization.json.JsonPrimitive)?.content == t }
                                    ?.let { (it["cultura"] as? kotlinx.serialization.json.JsonPrimitive)?.content }
                            },
                            estados = ndviTalhoes,
                            onVerNoMapa = { id, r ->
                                if (viewModel.verNdviNoMapa(id, r)) {
                                    prestadoraAberta = 0
                                    tab = 2
                                    listaAberta = true
                                }
                            },
                        )
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { contaSateliteAberta = !contaSateliteAberta }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Já tem conta no Sentinel Hub ou Planet? (opcional)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(if (contaSateliteAberta) "Fechar ‹" else "Conectar ›", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (contaSateliteAberta) ProviderIntegrationCard(
                        providers = listOf("Sentinel Hub", "Planet", "EOS Data Analytics", "Outro"),
                        titulo = "Conta própria de satélite (opcional)",
                        descricao = "O NDVI acima já é calculado pelo BRAgro sem conta. Se tiver assinatura própria: Sentinel Hub (CLIENT_ID|CLIENT_SECRET) ou Planet (chave).",
                        integration = ndviIntegration,
                        busy = ndviIntegrationBusy,
                        syncMessage = ndviIntegrationMessage,
                        onSave = { provedor, apiKey -> viewModel.saveNdviIntegration(provedor, apiKey) },
                        onDisconnect = { viewModel.disconnectNdviIntegration() },
                        onSync = { viewModel.syncNdviIntegration() },
                    )
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { prestadoraAberta = 0 }) { Text("Fechar") } },
        )
    }

    if (manualDialogOpen) {
        ManualBoundaryDialog(
            saving = importing,
            error = importError,
            farms = farms,
            onDismiss = { manualDialogOpen = false },
            onConfirm = { talhao, nome, farmId ->
                viewModel.manualBoundary(talhao, nome, farmId) { ok ->
                    if (ok) manualDialogOpen = false
                }
            },
        )
    }

    val currentPolygon = pendingPolygons.getOrNull(pendingIndex)
    if (currentPolygon != null) {
        ImportBoundaryDialog(
            polygon = currentPolygon,
            index = pendingIndex,
            total = pendingPolygons.size,
            saving = importing,
            error = importError,
            farms = farms,
            onDismiss = { pendingPolygons = emptyList(); pendingIndex = 0 },
            onConfirm = { talhao, farmId ->
                viewModel.importBoundary(talhao, currentPolygon, farmId) { ok ->
                    if (ok) {
                        if (pendingIndex + 1 < pendingPolygons.size) {
                            pendingIndex += 1
                        } else {
                            pendingPolygons = emptyList()
                            pendingIndex = 0
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun TalhaoStatusList(rows: List<JsonObject>) {
    if (rows.isEmpty()) {
        Text("Nenhum registro de Safra com talhão preenchido ainda.", modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(rows.size) { i ->
            // Setinha de recolher/expandir -- pedido do usuário ("na imagem
            // 3 coloque setinha nos blocos"): mesmo padrão ExpandLess/
            // ExpandMore já usado nos cards de registro (DomainListScreen.kt),
            // que faltava nestes 2 blocos (Talhão/Máquina).
            var open by remember { mutableStateOf(false) }
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Ícone + rótulo -- pedido do usuário ("acho que todo o
                    // fieldview... não tem sequer ícones... e outros tantos
                    // ícones"): esta aba era a única das 3 (Talhões/
                    // Máquinas/Fazendas-KML) sem NENHUM ícone no card, só
                    // texto solto (RawRecordFields), diferente do padrão
                    // ícone+label já usado nas outras 2 abas e no resto do
                    // app.
                    Row(
                        Modifier.fillMaxWidth().clickable { open = !open },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Eco, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                        val tit = run {
                            val o = rows[i]
                            fun c(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                            listOfNotNull(c("talhao")?.let { "Talhão $it" } ?: "Talhão", c("os")?.let { "O.S. $it" }).joinToString(" · ")
                        }
                        Text(tit, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        com.bragro.mobile.ui.domain.CantoL(open)
                    }
                    if (open) RawRecordFields(rows[i])
                }
            }
        }
    }
}

@Composable
private fun MaquinaStatusList(rows: List<JsonObject>) {
    if (rows.isEmpty()) {
        Text("Nenhum registro de Frota ainda.", modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(rows.size) { i ->
            // proxRevisao vencida -- achado da auditoria de paridade (Task
            // #201, pedido do usuário "insira tudo que falta no fieldview
            // da plataforma no app native"): o site calcula `vencida =
            // proxRevisao < now` e mostra um badge vermelho/verde
            // (fieldview-client.tsx); o app só mostrava a data crua, sem
            // nenhuma indicação visual de atraso.
            val proxRevisaoIso = rows[i]["proxRevisao"]?.jsonPrimitive?.contentOrNull
            val vencida = proxRevisaoIso?.let { isDatePast(it) } ?: false
            // Setinha de recolher/expandir -- pedido do usuário ("na imagem
            // 3 coloque setinha nos blocos"): mesmo padrão ExpandLess/
            // ExpandMore já usado nos cards de registro (DomainListScreen.kt),
            // que faltava neste bloco (Máquina não tinha nenhuma).
            var open by remember { mutableStateOf(false) }
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Ícone SEM rótulo -- achado da auditoria (usuário:
                    // "outros tantos ícones"): o ícone do carro ficava
                    // sozinho, sem nenhum texto ao lado explicando o que
                    // representa (diferente do padrão ícone+label do resto
                    // do app, ex.: BoundariesList abaixo já tem Map + nome).
                    Row(
                        Modifier.fillMaxWidth().clickable { open = !open },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                        Text("Máquina", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (!proxRevisaoIso.isNullOrBlank()) {
                            Text(
                                if (vencida) "Revisão vencida" else "Revisão em dia",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (vencida) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(
                                        (if (vencida) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                                        RoundedCornerShape(50),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        com.bragro.mobile.ui.domain.CantoL(open)
                    }
                    if (open) RawRecordFields(rows[i])
                }
            }
        }
    }
}

/** true se a data (ISO, "yyyy-MM-dd..." ou completa com hora) já passou --
 * mesmo critério do site (`vencida = proxRevisao < now`, fieldview-client.tsx). */
private fun isDatePast(iso: String): Boolean {
    val datePart = iso.take(10)
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val d = sdf.parse(datePart) ?: return false
        d.time < System.currentTimeMillis()
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoundariesList(boundaries: List<FieldBoundaryDto>, modifier: Modifier = Modifier) {
    if (boundaries.isEmpty()) {
        Text("Nenhum talhão com contorno importado ainda. Toque em \"Importar KML/KMZ\" acima.", modifier = modifier.padding(16.dp))
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        items(boundaries, key = { it.id }) { b ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Row sem largura definida + Text sem maxLines/overflow --
                    // pedido do usuário ("limite a tela, não deixe nenhum
                    // caractere passar do limite da tela"): um nome de
                    // talhão/KML longo crescia o Row inteiro além da borda
                    // do Card em vez de truncar.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            b.nome ?: b.talhao,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text("Talhão: ${b.talhao}", style = MaterialTheme.typography.bodySmall)
                    b.areaHaCalc?.let { Text("Área calculada: $it ha", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

/** Aba "Fazendas/KML" -- botão de importação nativa de KML/KMZ, links
 * "Abrir no Google Maps/Earth" POR FAZENDA (auditoria de paridade, Task
 * #226 -- mesmo recurso do site em fieldview-client.tsx, faltava só aqui no
 * app), mapa (osmdroid) com o contorno de todos os talhões já importados, e
 * a lista resumo (mesma BoundariesList de sempre) embaixo. */
@Composable
private fun FazendasKmlTab(
    boundaries: List<FieldBoundaryDto>,
    farms: List<FarmEntity>,
    camadasNdvi: List<CamadaNdvi> = emptyList(),
    mostrarNdvi: Boolean = false,
    onMostrarNdvi: (Boolean) -> Unit = {},
    onPolygonDrawn: (ParsedPolygon) -> Unit = {},
) {
    // Desenho direto no mapa (paridade com a ferramenta de polígono do site,
    // Task #832): toque no mapa adiciona vértices; "Concluir" entrega o
    // polígono pro mesmo diálogo de confirmação do KML (nome + fazenda).
    var drawing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!drawing) {
                // v42 -- "Desenhar" entrou na fileira de ícones do Mapa/KML
                // (ver FarmMapLinksRow abaixo); aqui fica só o modo de desenho.
            } else {
                Text("${draft.size} pontos", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { draft = draft.dropLast(1) }, enabled = draft.isNotEmpty()) { Text("Desfazer") }
                TextButton(onClick = { drawing = false; draft = emptyList() }) { Text("Cancelar") }
                Button(
                    enabled = draft.size >= 3,
                    onClick = {
                        onPolygonDrawn(ParsedPolygon(null, draft.map { it.latitude to it.longitude }))
                        drawing = false
                        draft = emptyList()
                    },
                ) { Text("Concluir") }
            }
        }
        if (drawing) {
            Text(
                "Toque no mapa para marcar os vértices do talhão (mínimo 3).",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        // Botão "Importar KML/KMZ" que existia aqui foi promovido pra cima
        // (fileira comum acima das abas, ver FieldviewScreen) -- fica
        // visível tanto em Talhões quanto em Fazendas/KML agora, sem
        // duplicar nesta aba.
        FarmMapLinksRow(boundaries, farms, onDesenhar = { drawing = true; draft = emptyList() })
        if (camadasNdvi.isNotEmpty()) {
            // v80 -- Ruas / NDVI (mockup v80).
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                val dataUnica = camadasNdvi.singleOrNull()?.data?.let { "${it.substring(8, 10)}/${it.substring(5, 7)}" }.orEmpty()
                listOf(false to "Satélite", true to "NDVI $dataUnica".trim()).forEach { (valor, rotulo) ->
                    val ativo = mostrarNdvi == valor
                    Text(
                        rotulo,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ativo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(if (ativo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onMostrarNdvi(valor) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
        val ctxMapa = androidx.compose.ui.platform.LocalContext.current.applicationContext
        var centroFazendas by remember { mutableStateOf<GeoPoint?>(null) }
        LaunchedEffect(Unit) {
            val r = com.bragro.mobile.data.repo.ModuleActionsRepository(ctxMapa).run("fazendas-coord")
            val lista = (r?.get("fazendas") as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val lat = (o["latitude"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                val lon = (o["longitude"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                if (lat != null && lon != null) GeoPoint(lat, lon) else null
            }
            if (lista.isNotEmpty()) centroFazendas = GeoPoint(lista.map { it.latitude }.average(), lista.map { it.longitude }.average())
        }
        BoundariesMap(
            boundaries, drawing = drawing, draft = draft, onTap = { draft = draft + it },
            camadasNdvi = if (mostrarNdvi) camadasNdvi else emptyList(),
            centroFazendas = centroFazendas,
            onDesenhar = { drawing = true; draft = emptyList() },
        )
        if (mostrarNdvi && camadasNdvi.isNotEmpty()) LegendaNdvi()
        BoundariesList(boundaries, modifier = Modifier.weight(1f))
    }
}

/** Centroide aproximado do PRIMEIRO talhão com geometria de verdade
 * vinculado a uma fazenda (mesmo critério do site, ver geometryCentroid()/
 * googleMapsUrl() em lib/geo.ts) -- reaproveita geoJsonPolygonToGeoPoints já
 * usado pra desenhar o overlay do mapa, só que tirando a média dos pontos em
 * vez de desenhar. */
private fun farmCentroid(boundaries: List<FieldBoundaryDto>, farmId: String): GeoPoint? {
    for (b in boundaries) {
        if (b.farmId != farmId) continue
        val points = geoJsonPolygonToGeoPoints(b.geojson)
        if (points.isNotEmpty()) {
            val lat = points.sumOf { it.latitude } / points.size
            val lon = points.sumOf { it.longitude } / points.size
            return GeoPoint(lat, lon)
        }
    }
    return null
}

/** Mesma URL do Google Maps montada em lib/geo.ts (googleMapsUrl) --
 * coordenada exata quando o centroide existe, ou uma busca pelo nome como
 * alternativa (fazenda ainda sem talhão georreferenciado). */
private fun googleMapsUrl(point: GeoPoint?, fallbackQuery: String): String =
    if (point != null) "https://www.google.com/maps?q=${point.latitude},${point.longitude}"
    else "https://www.google.com/maps/search/?api=1&query=${Uri.encode(fallbackQuery)}"

/** Espelho de googleEarthUrl() em lib/geo.ts -- pedido do usuário ("link o
 * maps do app native ao google earth"). "1000a,1000d" = câmera a ~1000m de
 * altitude olhando reto pra baixo, zoom suficiente pra enxergar o talhão
 * inteiro sem o usuário precisar reajustar. */
private fun googleEarthUrl(point: GeoPoint?, fallbackQuery: String): String =
    if (point != null) "https://earth.google.com/web/@${point.latitude},${point.longitude},1000a,1000d,35y,0h,0t,0r"
    else "https://earth.google.com/web/search/${Uri.encode(fallbackQuery)}"

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FarmMapLinksRow(
    boundaries: List<FieldBoundaryDto>,
    farms: List<FarmEntity>,
    onDesenhar: () -> Unit,
) {
    val context = LocalContext.current
    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    // v42 -- o que ficava solto aqui embaixo virou uma fileira de ícones, no
    // mesmo padrão do bloco Ações: Maps, Earth e Desenhar. Os botões de
    // fazenda só aparecem depois de tocar em Maps ou em Earth.
    var destino by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            com.bragro.mobile.ui.domain.LabeledIconButton(
                icon = Icons.Outlined.Map,
                label = "Maps",
                active = destino == "maps",
                modifier = Modifier.weight(1f),
                onClick = { destino = if (destino == "maps") null else "maps" },
            )
            com.bragro.mobile.ui.domain.LabeledIconButton(
                icon = Icons.Outlined.Public,
                label = "Earth",
                active = destino == "earth",
                modifier = Modifier.weight(1f),
                onClick = { destino = if (destino == "earth") null else "earth" },
            )
            com.bragro.mobile.ui.domain.LabeledIconButton(
                icon = Icons.Outlined.EditLocationAlt,
                label = "Desenhar",
                modifier = Modifier.weight(1f),
                onClick = onDesenhar,
            )
        }
        if (destino != null && farms.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (farm in farms) {
                    val ponto = farmCentroid(boundaries, farm.id)
                    OutlinedButton(
                        onClick = { openUrl(if (destino == "earth") googleEarthUrl(ponto, farm.name) else googleMapsUrl(ponto, farm.name)) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(farm.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Clip)
                    }
                }
            }
        }
    }
}

/** Mapa nativo (osmdroid, MAPNIK -- OpenStreetMap, sem chave de API)
 * mostrando o contorno de cada talhão já importado. Sem overlays.clear()
 * + re-adicionar tudo a cada "update" seria mais eficiente diffar, mas a
 * lista de talhões de uma organização é pequena (poucas dezenas no
 * máximo) -- não vale a complexidade. */
@Composable
private fun BoundariesMap(
    boundaries: List<FieldBoundaryDto>,
    drawing: Boolean = false,
    draft: List<GeoPoint> = emptyList(),
    onTap: (GeoPoint) -> Unit = {},
    camadasNdvi: List<CamadaNdvi> = emptyList(),
    // v1.6.4 (mockup v105, item 8) -- abre nas fazendas com coordenada e botão Desenhar talhão.
    centroFazendas: GeoPoint? = null,
    onDesenhar: (() -> Unit)? = null,
) {
    val strokeColor = MaterialTheme.colorScheme.primary.toArgb()
    val onTapState = androidx.compose.runtime.rememberUpdatedState(onTap)
    val drawingState = androidx.compose.runtime.rememberUpdatedState(drawing)
    // v44 -- mapa estável: altura fixa, recortado no próprio quadro (não
    // cobre mais os ícones), sem repetir o mundo nem "soltar" para fora,
    // overlays só refeitos quando o conteúdo muda (acabou o piscar) e o
    // zoom do usuário é respeitado (só centraliza uma vez).
    var mapaRef by remember { mutableStateOf<MapView?>(null) }
    // [0] = assinatura do conteúdo desenhado, [1] = já centralizou
    val estadoMapa = remember { arrayOfNulls<Any>(2) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(300.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
    ) {
    AndroidView(
        modifier = Modifier.matchParentSize().clipToBounds(),
        factory = { ctx ->
            MapView(ctx).apply {
                // v1.6.4 (mockup v105, item 8) -- satélite igual à plataforma.
                MapaBRAgro.aplicarSatelite(this)
                MapaBRAgro.credito(this)
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false
                setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
                minZoomLevel = 3.0
                maxZoomLevel = 19.0
                isTilesScaledToDpi = true
                clipToOutline = true
                // A tela rola na vertical: enquanto o dedo está no mapa, o
                // gesto é do mapa (arrastar/pinçar), não da página.
                setOnTouchListener { v, e ->
                    v.parent?.requestDisallowInterceptTouchEvent(e.action != android.view.MotionEvent.ACTION_UP && e.action != android.view.MotionEvent.ACTION_CANCEL)
                    false
                }
                overlays.add(
                    org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (!drawingState.value || p == null) return false
                            onTapState.value(GeoPoint(p.latitude, p.longitude))
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    }),
                )
                controller.setZoom(4.0)
                controller.setCenter(GeoPoint(-15.0, -55.0))
                mapaRef = this
            }
        },
        update = { mv ->
            val assinatura = listOf(boundaries.map { it.id }, draft.toList(), strokeColor, camadasNdvi.map { it.id + it.data }, centroFazendas)
            if (estadoMapa[0] == assinatura) return@AndroidView
            estadoMapa[0] = assinatura
            // mantém os overlays fixos (nomes do satélite + eventos de toque) e refaz só os polígonos
            val fixos = mv.overlays.count { it is org.osmdroid.views.overlay.TilesOverlay || it is org.osmdroid.views.overlay.MapEventsOverlay }
            while (mv.overlays.size > fixos) mv.overlays.removeAt(mv.overlays.size - 1)
            val allPoints = mutableListOf<GeoPoint>()
            // v80 -- camada NDVI (por baixo dos contornos).
            camadasNdvi.forEach { c ->
                mv.overlays.add(
                    org.osmdroid.views.overlay.GroundOverlay().apply {
                        setImage(c.imagem)
                        setPosition(GeoPoint(c.norte, c.oeste), GeoPoint(c.sul, c.leste))
                    },
                )
            }
            boundaries.forEach { boundary ->
                val points = geoJsonPolygonToGeoPoints(boundary.geojson)
                if (points.size >= 3) {
                    val polygon = OsmPolygon(mv)
                    polygon.setPoints(points)
                    polygon.setStrokeColor(strokeColor)
                    polygon.setStrokeWidth(3f)
                    polygon.setFillColor(if (camadasNdvi.isNotEmpty()) 0 else (strokeColor and 0x00FFFFFF) or 0x33000000)
                    mv.overlays.add(polygon)
                    allPoints.addAll(points)
                }
            }
            if (draft.isNotEmpty()) {
                val d = OsmPolygon(mv)
                d.setPoints(if (draft.size >= 3) draft + draft.first() else draft)
                d.setStrokeColor(0xFFE65100.toInt())
                d.setStrokeWidth(4f)
                d.setFillColor(if (draft.size >= 3) 0x33E65100 else 0)
                mv.overlays.add(d)
            }
            MapaBRAgro.credito(mv)
            if (camadasNdvi.isNotEmpty()) {
                val c = camadasNdvi.last()
                mv.controller.setZoom(15.0)
                mv.controller.setCenter(GeoPoint((c.norte + c.sul) / 2, (c.leste + c.oeste) / 2))
                estadoMapa[1] = true
            }
            // Centraliza nos talhões UMA vez; depois o zoom é do usuário.
            if (estadoMapa[1] != true && allPoints.isNotEmpty()) {
                estadoMapa[1] = true
                mv.controller.setZoom(14.0)
                mv.controller.setCenter(allPoints.first())
            } else if (estadoMapa[1] != true && centroFazendas != null) {
                estadoMapa[1] = true
                mv.controller.setZoom(14.0)
                mv.controller.setCenter(centroFazendas)
            }
            mv.invalidate()
        },
        onRelease = { mapView -> mapView.onDetach() },
    )
        if (onDesenhar != null && !drawing) {
            Button(
                onClick = onDesenhar,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) { Text("⬠ Desenhar talhão", style = MaterialTheme.typography.labelMedium) }
        }
        // Zoom por botões (pinçar continua funcionando).
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("+" to true, "−" to false).forEach { (rotulo, mais) ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .clickable { mapaRef?.let { if (mais) it.controller.zoomIn() else it.controller.zoomOut() } },
                    contentAlignment = Alignment.Center,
                ) { Text(rotulo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}


/** Converte o GeoJSON Polygon cru salvo em FieldBoundaryDto.geojson
 * (coordenadas em ordem lon,lat, padrão GeoJSON) pra uma lista de
 * GeoPoint (lat,lon, ordem que o osmdroid espera) -- so o anel externo
 * (coordinates[0]), mesmo recorte do KmlParser. Qualquer formato
 * inesperado (nulo, não é objeto, sem "coordinates" etc.) devolve lista
 * vazia em vez de derrubar a tela. */
private fun geoJsonPolygonToGeoPoints(geojson: JsonElement?): List<GeoPoint> {
    if (geojson == null) return emptyList()
    return try {
        val coordinates = geojson.jsonObject["coordinates"]?.jsonArray ?: return emptyList()
        val outerRing = coordinates.firstOrNull()?.jsonArray ?: return emptyList()
        outerRing.mapNotNull { coordPair ->
            val pair = coordPair.jsonArray
            if (pair.size < 2) return@mapNotNull null
            val lon = pair[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val lat = pair[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            GeoPoint(lat, lon)
        }
    } catch (e: Exception) {
        AppLog.e("FieldviewScreen", "Falha ao converter GeoJSON de contorno de talhão pra pontos do mapa", e)
        emptyList()
    }
}

/** Confirmação de nome do talhão antes de salvar (a chave única de
 * negócio, ver comentário em FieldviewImportRequest/Models.kt) -- pré-
 * preenchida com o <name> do Placemark do KML/KMZ, se houver. Quando o
 * KML tem mais de um polígono, mostra "(i/total)" no título e a tela
 * avança pro próximo automaticamente após salvar (ver onConfirm em
 * FieldviewScreen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportBoundaryDialog(
    polygon: ParsedPolygon,
    index: Int,
    total: Int,
    saving: Boolean,
    error: String?,
    farms: List<FarmEntity>,
    onDismiss: () -> Unit,
    onConfirm: (talhao: String, farmId: String?) -> Unit,
) {
    var talhao by remember(polygon) { mutableStateOf(polygon.name ?: "") }
    // Vínculo opcional com o cadastro de Fazendas (Task farmId em
    // /api/mobile/fieldview, action=import_boundary) -- null enquanto
    // nenhuma fazenda é escolhida, mesmo comportamento opcional do backend.
    var selectedFarm by remember(polygon) { mutableStateOf<FarmEntity?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (total > 1) "Importar talhão (${index + 1}/$total)" else "Importar talhão") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Contorno com ${polygon.points.size} pontos. Confirme o nome do talhão (chave usada para cruzar com Safra/Frota).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = talhao, onValueChange = { talhao = it },
                    label = { Text("Talhão *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
                // Dropdown opcional -- mesma lista de fazendas já cacheada
                // pelo bootstrap (ConfigRepository.farms(), reaproveitada de
                // NfeImportScreen). Vazio (null) é uma opção explícita
                // ("Nenhuma"), já que o vínculo é opcional no backend.
                OptionalFarmDropdown(
                    farms = farms,
                    selected = selectedFarm,
                    onSelect = { selectedFarm = it },
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && talhao.isNotBlank(),
                onClick = { onConfirm(talhao.trim(), selectedFarm?.id) },
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}

/** Diálogo de registro MANUAL de talhão (sem KML/KMZ) -- pedido do
 * usuário. Mesmo layout do ImportBoundaryDialog acima, só que sem o passo
 * de escolher um arquivo/polígono: o usuário digita o número/nome do talhão
 * direto, e o servidor cria o registro com um GeoJSON vazio (ver
 * FieldviewViewModel.manualBoundary). O contorno pode ser importado depois,
 * a qualquer momento, sem perder o vínculo (upsert por [orgId, talhao]). */
@Composable
private fun ManualBoundaryDialog(
    saving: Boolean,
    error: String?,
    farms: List<FarmEntity>,
    onDismiss: () -> Unit,
    onConfirm: (talhao: String, nome: String?, farmId: String?) -> Unit,
) {
    var talhao by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }
    var selectedFarm by remember { mutableStateOf<FarmEntity?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Lançar talhão manualmente") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Cria o talhão na hora, sem precisar de um arquivo KML/KMZ. Você pode importar o contorno depois, se quiser.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = talhao, onValueChange = { talhao = it },
                    label = { Text("Talhão (número/nome) *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it },
                    label = { Text("Nome (opcional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
                OptionalFarmDropdown(
                    farms = farms,
                    selected = selectedFarm,
                    onSelect = { selectedFarm = it },
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && talhao.isNotBlank(),
                onClick = { onConfirm(talhao.trim(), nome.trim().ifBlank { null }, selectedFarm?.id) },
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}

/** Dropdown "Fazenda (opcional)" do diálogo de import de KML/KMZ -- mesmo
 * padrão visual do FarmDropdown de NfeImportScreen.kt (ExposedDropdownMenuBox
 * com OutlinedTextField somente-leitura), só que com uma opção extra
 * "Nenhuma" pra limpar a seleção (o vínculo com fazenda é opcional aqui,
 * diferente do NF-e, onde a fazenda de destino é obrigatória). */
@Composable
private fun OptionalFarmDropdown(farms: List<FarmEntity>, selected: FarmEntity?, onSelect: (FarmEntity?) -> Unit) {
    SearchableDropdownField(
        value = selected?.id ?: "",
        label = "Fazenda (opcional)",
        options = remember(farms) { farms.map { it.id to it.name } },
        onSelect = { id -> onSelect(farms.firstOrNull { it.id == id }) },
        emptyOptionLabel = "Nenhuma",
    )
}


// ---------------------------------------------------------------------------
// v80 -- NDVI próprio (mockup v80 aprovado)
// ---------------------------------------------------------------------------

private val CORES_NDVI = listOf(
    androidx.compose.ui.graphics.Color(0xFFA67C52) to "Solo/palha <0,2",
    androidx.compose.ui.graphics.Color(0xFFD9822B) to "Baixo 0,2–0,4",
    androidx.compose.ui.graphics.Color(0xFFE8C448) to "Médio 0,4–0,6",
    androidx.compose.ui.graphics.Color(0xFF6EAA50) to "Bom 0,6–0,8",
    androidx.compose.ui.graphics.Color(0xFF24683E) to "Muito alto >0,8",
)
private val CURTO_NDVI = listOf("solo", "baixo", "médio", "bom", "muito alto")

private fun temContorno(b: FieldBoundaryDto): Boolean = geoJsonPolygonToGeoPoints(b.geojson).size >= 3

@Composable
private fun NdviLista(
    boundaries: List<FieldBoundaryDto>,
    culturaDoTalhao: (String) -> String?,
    estados: Map<String, com.bragro.mobile.data.model.NdviTalhaoDto?>,
    onVerNoMapa: (String, com.bragro.mobile.data.model.NdviResultadoDto) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val verde = MaterialTheme.colorScheme.primary
    val laranja = com.bragro.mobile.ui.domain.corAtencao()
    val br = java.util.Locale("pt", "BR")
    fun n2(v: Double) = String.format(br, "%.2f", v)
    fun dataBr(d: String) = if (d.length >= 10) "${d.substring(8, 10)}/${d.substring(5, 7)}" else d
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Calculado pelo BRAgro com imagens abertas do satélite Sentinel-2 (10 m, nova imagem a cada ~5 dias). Nuvem e sombra são descartadas.",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
        if (boundaries.isEmpty()) Text("Nenhum talhão cadastrado ainda.", style = MaterialTheme.typography.bodySmall, color = muted)
        boundaries.forEach { b ->
            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            val cultura = culturaDoTalhao(b.talhao)
            val titulo = "Talhão ${b.talhao}" + (cultura?.let { " · $it" } ?: "")
            if (!temContorno(b)) {
                Text(titulo, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text("Importe o KML ou desenhe o contorno no mapa para calcular.", style = MaterialTheme.typography.labelSmall, color = muted)
                return@forEach
            }
            val st = estados[b.id]
            val a = st?.atual
            when {
                st == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titulo, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("calculando…", style = MaterialTheme.typography.labelSmall, color = muted)
                }
                a == null -> {
                    Text(titulo, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(st.erro ?: "Não foi possível calcular agora.", style = MaterialTheme.typography.labelSmall, color = laranja)
                }
                else -> {
                    val fx = when { a.media < 0.2 -> 0; a.media < 0.4 -> 1; a.media < 0.6 -> 2; a.media < 0.8 -> 3; else -> 4 }
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(titulo, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("imagem de ${dataBr(a.cena.data)} · ${a.validoPct}% sem nuvem", style = MaterialTheme.typography.labelSmall, color = muted)
                            st.anterior?.let { ant ->
                                val dif = a.media - ant.media
                                val (txt, cor) = when {
                                    dif > 0.01 -> "↑ +${n2(dif)} melhorando" to verde
                                    dif < -0.01 -> "↓ −${n2(-dif)} piorando" to laranja
                                    else -> "estável" to muted
                                }
                                Row {
                                    Text("Antes (${dataBr(ant.data)}): ${n2(ant.media)} · ", style = MaterialTheme.typography.labelSmall, color = muted)
                                    Text(txt, style = MaterialTheme.typography.labelSmall, color = cor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(n2(a.media), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                CURTO_NDVI[fx],
                                style = MaterialTheme.typography.labelSmall,
                                color = if (fx >= 3) verde else if (fx == 2) muted else laranja,
                                fontWeight = if (fx <= 1) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50))) {
                        a.faixasPct.forEachIndexed { i, p ->
                            if (p > 0) Box(Modifier.weight(p.toFloat()).fillMaxHeight().background(CORES_NDVI.getOrNull(i)?.first ?: muted))
                        }
                    }
                    Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (a.png != null && a.limites != null) {
                            Button(onClick = { onVerNoMapa(b.id, a) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp), modifier = Modifier.height(30.dp)) {
                                Text("Ver no mapa", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            a.faixasPct.mapIndexed { i, p -> "$p% ${CURTO_NDVI.getOrElse(i) { "" }}" }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendaNdvi() {
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        (CORES_NDVI + (androidx.compose.ui.graphics.Color(0xFFC8C8C8) to "Nuvem")).forEach { (cor, rotulo) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(cor))
                Spacer(Modifier.width(3.dp))
                Text(rotulo, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Text(
        "Contém dados Copernicus Sentinel modificados.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
