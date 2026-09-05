package com.bragro.mobile.ui.fieldview

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import com.bragro.mobile.ui.theme.Card
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
import androidx.compose.material3.Text
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon as OsmPolygon

// FieldView (Task #107/#110) -- talhões (com área calculada a partir do
// KML/KMZ importado), status de Safra por talhão e status de Frota por
// máquina, mapa nativo (osmdroid) e importação nativa de KML/KMZ direto
// no aparelho (ver data/kml/KmlParser.kt), sem depender mais do site.
class FieldviewViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = FieldviewRepository(app)
    private val configRepository = ConfigRepository(app)
    // Card "Acesso automático via prestadora de serviço" (Task #341/#54) --
    // ver ProviderIntegrationRepository.kt/ProviderIntegrationCard.kt.
    private val integrationRepository = ProviderIntegrationRepository(app, IntegrationModule.FIELDVIEW)

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

    fun load() {
        loading.value = true
        viewModelScope.launch {
            data.value = repository.fetch()
            loading.value = false
        }
        viewModelScope.launch { farms.value = configRepository.farms() }
        viewModelScope.launch { integration.value = integrationRepository.get() }
    }

    fun saveIntegration(provedor: String, apiKey: String) {
        integrationBusy.value = IntegrationBusy.SALVANDO
        integrationMessage.value = null
        viewModelScope.launch {
            val ok = integrationRepository.save(provedor, apiKey)
            integrationMessage.value = if (ok) "Credencial salva." else "Falha ao salvar credencial -- confira a conexão e tente de novo."
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
                integrationMessage.value = "Falha ao desconectar -- confira a conexão e tente de novo."
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
                importError.value = "Falha ao importar o contorno -- confira a conexão e tente de novo."
            }
            importing.value = false
            onDone(ok)
        }
    }

    /** Lançamento MANUAL de talhão, sem KML/KMZ nenhum -- pedido do usuário
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
                importError.value = "Falha ao lançar o talhão -- confira a conexão e tente de novo."
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

/** Mostra os campos primitivos de um registro cru (Safra/Frota), exceto
 * campos técnicos -- mesmo critério de "mostrar tudo que veio preenchido"
 * já usado no Ver de DomainListScreen, só que sem um ColumnConfig por trás
 * (esses dados não vêm do domínio genérico). */
@Composable
private fun RawRecordFields(obj: JsonObject) {
    Column {
        obj.entries
            .filter { (k, v) -> k !in IGNORED_KEYS && v.jsonPrimitive.contentOrNull?.isNotBlank() == true }
            .forEach { (k, v) ->
                val raw = v.jsonPrimitive.contentOrNull ?: ""
                val colType = if (ISO_DATETIME_PREFIX.containsMatchIn(raw)) "date" else "text"
                Text(
                    "$k: ${displayValueFor(k, raw, colType)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldviewScreen(onBack: () -> Unit, onNavigateToFrota: () -> Unit = {}, viewModel: FieldviewViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.load() }
    // osmdroid exige um user agent nao-vazio (senao os servidores de tile
    // do OSM podem rejeitar a requisicao) e se beneficia de um diretorio
    // de cache proprio -- configuracao unica, feita aqui (roda de novo a
    // cada entrada na tela, mas e idempotente/barata).
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
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
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Talhões", "Máquinas", "Fazendas/KML")
    var pendingPolygons by remember { mutableStateOf<List<ParsedPolygon>>(emptyList()) }
    var pendingIndex by remember { mutableStateOf(0) }
    // Diálogo de lançamento MANUAL de talhão (sem KML/KMZ) -- pedido do
    // usuário. Aberto pelo botão "+" nas abas Talhões e Fazendas/KML.
    var manualDialogOpen by remember { mutableStateOf(false) }

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
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("FieldView", color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary) }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Talhões, contornos (mapa) e status de safra/máquinas -- importe o contorno direto de um KML/KMZ na aba Fazendas/KML.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // Card "Acesso automático via prestadora de serviço" (Task
            // #341/#54) -- mesma posição/UX do site (topo da tela, acima das
            // abas, ver fieldview-client.tsx), única peça que ainda faltava
            // no mobile (a credencial já era 100% funcional no site).
            ProviderIntegrationCard(
                providers = listOf("Climate FieldView", "John Deere Operations Center", "Outro", "Trimble Ag Software"),
                // Aviso resumido -- pedido do usuário ("resuma os avisos").
                descricao = "Hoje os limites de talhão são importados manualmente por KML/KMZ. Credencial salva com segurança abaixo.",
                integration = integration,
                busy = integrationBusy,
                syncMessage = integrationMessage,
                onSave = { provedor, apiKey -> viewModel.saveIntegration(provedor, apiKey) },
                onDisconnect = { viewModel.disconnectIntegration() },
                onSync = { viewModel.syncIntegration() },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            // Espaço maior entre o card "Acesso automático" e as abas
            // Talhões/Máquinas/Fazendas -- pedido do usuário ("dê um espaço
            // maior entre o bloco acesso automático do bloco talhões
            // máquinas fazendas"): antes o TabRow vinha colado direto embaixo
            // do card, sem nenhum respiro.
            Spacer(modifier = Modifier.height(20.dp))
            // Ícone em cada aba (Eco/DirectionsCar/Map, mesmos já usados no
            // conteudo de cada uma abaixo) -- pedido do usuário ("outros
            // tantos ícones"), TabRow antes só tinha texto.
            val tabIcons = listOf(Icons.Filled.Eco, Icons.Filled.DirectionsCar, Icons.Filled.Map)
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(label) },
                        icon = { Icon(tabIcons[i], contentDescription = null) },
                    )
                }
            }
            // Botão único "Importar KML/KMZ" nas 3 abas -- pedido do usuário
            // ("o botão importar kml tem que servir também para máquinas e
            // mapas, retire o botão importar máquinas"): antes só Talhões/
            // Fazendas-KML tinham este botão e Máquinas tinha um botão
            // separado ("+ Lançar máquina manualmente (Frota)"), removido
            // agora.
            //
            // "Lançar talhão manualmente" (Task #201, auditoria de paridade
            // com o site): o diálogo (ManualBoundaryDialog), o estado
            // (manualDialogOpen) e a chamada de API (viewModel.manualBoundary)
            // já existiam prontos e corretos, mas sem NENHUM botão que
            // abrisse o diálogo -- feature morta, igual ao "Lançar talhão
            // manualmente" do site (fieldview-client.tsx), que o app nunca
            // teve gatilho. Corrigido aqui.
            Row(
                // Espaço maior entre os dois botões -- pedido do usuário
                // ("dê um espaço maior também para importar kml e lançar
                // talhão"): 8.dp ficava os dois quase colados/cortando o 2º
                // rótulo na tela.
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                OutlinedButton(
                    onClick = { kmlPicker.launch(arrayOf("application/vnd.google-earth.kml+xml", "application/vnd.google-earth.kmz", "application/octet-stream", "*/*")) },
                    enabled = !importing,
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(if (importing) "Importando..." else "Importar KML/KMZ")
                }
                OutlinedButton(onClick = { manualDialogOpen = true }, enabled = !importing) {
                    Icon(Icons.Filled.EditLocationAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Lançar talhão manualmente")
                }
            }
            when {
                loading -> Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                }
                data == null -> Text("Sem conexão -- não foi possível carregar.", modifier = Modifier.padding(16.dp))
                else -> when (tab) {
                    0 -> TalhaoStatusList(data!!.talhaoStatus)
                    1 -> MaquinaStatusList(data!!.maquinaStatus)
                    else -> FazendasKmlTab(boundaries = data!!.boundaries, farms = farms)
                }
            }
        }
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
        Text("Nenhum lançamento de Safra com talhão preenchido ainda.", modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(rows.size) { i ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Ícone + rótulo -- pedido do usuário ("acho que todo o
                    // fieldview... não tem sequer ícones... e outros tantos
                    // ícones"): esta aba era a única das 3 (Talhões/
                    // Máquinas/Fazendas-KML) sem NENHUM ícone no card, só
                    // texto solto (RawRecordFields), diferente do padrão
                    // ícone+label já usado nas outras 2 abas e no resto do
                    // app.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Eco, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                        Text("Talhão", style = MaterialTheme.typography.titleSmall)
                    }
                    RawRecordFields(rows[i])
                }
            }
        }
    }
}

@Composable
private fun MaquinaStatusList(rows: List<JsonObject>) {
    if (rows.isEmpty()) {
        Text("Nenhum lançamento de Frota ainda.", modifier = Modifier.padding(16.dp))
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
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Ícone SEM rótulo -- achado da auditoria (usuário:
                    // "outros tantos ícones"): o ícone do carro ficava
                    // sozinho, sem nenhum texto ao lado explicando o que
                    // representa (diferente do padrão ícone+label do resto
                    // do app, ex.: BoundariesList abaixo já tem Map + nome).
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
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
                    }
                    RawRecordFields(rows[i])
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
        Text("Nenhum talhão com contorno importado ainda -- toque em \"Importar KML/KMZ\" acima.", modifier = modifier.padding(16.dp))
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
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            b.nome ?: b.talhao,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.weight(1f).basicMarquee(),
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
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Botão "Importar KML/KMZ" que existia aqui foi promovido pra cima
        // (fileira comum acima das abas, ver FieldviewScreen) -- fica
        // visível tanto em Talhões quanto em Fazendas/KML agora, sem
        // duplicar nesta aba.
        FarmMapLinksRow(boundaries, farms)
        BoundariesMap(boundaries)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FarmMapLinksRow(boundaries: List<FieldBoundaryDto>, farms: List<FarmEntity>) {
    if (farms.isEmpty()) return
    val context = LocalContext.current
    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("Abrir no Google Maps:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (farm in farms) {
                val ponto = farmCentroid(boundaries, farm.id)
                OutlinedButton(onClick = { openUrl(googleMapsUrl(ponto, farm.name)) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(farm.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.basicMarquee())
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("Abrir no Google Earth:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (farm in farms) {
                val ponto = farmCentroid(boundaries, farm.id)
                OutlinedButton(onClick = { openUrl(googleEarthUrl(ponto, farm.name)) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                    Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(farm.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.basicMarquee())
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
private fun BoundariesMap(boundaries: List<FieldBoundaryDto>) {
    val strokeColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(280.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(14.0)
            }
        },
        update = { mv ->
            mv.overlays.clear()
            val allPoints = mutableListOf<GeoPoint>()
            boundaries.forEach { boundary ->
                val points = geoJsonPolygonToGeoPoints(boundary.geojson)
                if (points.size >= 3) {
                    val polygon = OsmPolygon(mv)
                    polygon.setPoints(points)
                    polygon.setStrokeColor(strokeColor)
                    polygon.setStrokeWidth(3f)
                    // Mesma cor do traço pro preenchimento, so que com
                    // alfa baixo (0x33 ~= 20% de opacidade) -- mascara os
                    // 3 bytes de RGB originais e substitui só o alfa.
                    polygon.setFillColor((strokeColor and 0x00FFFFFF) or 0x33000000)
                    mv.overlays.add(polygon)
                    allPoints.addAll(points)
                }
            }
            if (allPoints.isNotEmpty()) {
                mv.controller.setCenter(allPoints.first())
            }
            mv.invalidate()
        },
        // Sem isso, o MapView do osmdroid (cache de tiles em memoria/disco +
        // threads de download) continua vivo mesmo depois que esta tela sai
        // de composicao (ex.: usuario aperta Voltar) -- vazamento de memoria
        // classico de osmdroid em Compose. onRelease (disponivel desde
        // Compose UI 1.4, presente na 1.6.8 usada neste projeto -- ver
        // app/build.gradle.kts) e chamado exatamente uma vez, quando a View
        // sai de composicao pra nao ser reaproveitada -- ponto certo pra
        // liberar o MapView, igual a documentacao do osmdroid recomenda.
        onRelease = { mapView -> mapView.onDetach() },
    )
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
                    "Contorno com ${polygon.points.size} pontos -- confirme o nome do talhão (chave usada pra cruzar com Safra/Frota).",
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

/** Diálogo de lançamento MANUAL de talhão (sem KML/KMZ) -- pedido do
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionalFarmDropdown(farms: List<FarmEntity>, selected: FarmEntity?, onSelect: (FarmEntity?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "Nenhuma",
            onValueChange = {},
            readOnly = true,
            label = { Text("Fazenda (opcional)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = appFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Nenhuma") }, onClick = { onSelect(null); expanded = false })
            for (farm in farms) {
                DropdownMenuItem(text = { Text(farm.name) }, onClick = { onSelect(farm); expanded = false })
            }
        }
    }
}
