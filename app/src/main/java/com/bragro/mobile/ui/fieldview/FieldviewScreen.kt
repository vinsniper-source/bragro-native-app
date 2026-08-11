package com.bragro.mobile.ui.fieldview

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.bragro.mobile.data.model.FieldBoundaryDto
import com.bragro.mobile.data.model.FieldviewResponse
import com.bragro.mobile.data.repo.FieldviewRepository
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

    var data = mutableStateOf<FieldviewResponse?>(null)
        private set
    var loading = mutableStateOf(false)
        private set
    var importing = mutableStateOf(false)
        private set
    var importError = mutableStateOf<String?>(null)
        private set

    fun load() {
        loading.value = true
        viewModelScope.launch {
            data.value = repository.fetch()
            loading.value = false
        }
    }

    /** Importa um talhão parseado de um KML/KMZ -- converte pra GeoJSON e
     * calcula a área aproximada localmente (KmlParser.kt), manda pro
     * servidor (upsert por [orgId, talhao]) e recarrega a lista em caso
     * de sucesso. */
    fun importBoundary(talhao: String, polygon: ParsedPolygon, onDone: (Boolean) -> Unit) {
        importing.value = true
        importError.value = null
        viewModelScope.launch {
            val geojson = polygonToGeoJson(polygon)
            val areaHa = polygonAreaHectares(polygon.points)
            val ok = repository.importBoundary(talhao, polygon.name, geojson, areaHa)
            if (ok) {
                data.value = repository.fetch()
            } else {
                importError.value = "Falha ao importar o contorno -- confira a conexão e tente de novo."
            }
            importing.value = false
            onDone(ok)
        }
    }
}

private val IGNORED_KEYS = setOf("id", "orgId", "criadoEm", "editadoEm", "updatedAt")

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
                Text(
                    "$k: ${displayValueFor(k, v.jsonPrimitive.contentOrNull ?: "", "text")}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldviewScreen(onBack: () -> Unit, viewModel: FieldviewViewModel = viewModel()) {
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
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Talhões", "Máquinas", "Fazendas/KML")
    var pendingPolygons by remember { mutableStateOf<List<ParsedPolygon>>(emptyList()) }
    var pendingIndex by remember { mutableStateOf(0) }

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
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
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
                    else -> FazendasKmlTab(
                        boundaries = data!!.boundaries,
                        importing = importing,
                        onImportClick = { kmlPicker.launch(arrayOf("application/vnd.google-earth.kml+xml", "application/vnd.google-earth.kmz", "application/octet-stream", "*/*")) },
                    )
                }
            }
        }
    }

    val currentPolygon = pendingPolygons.getOrNull(pendingIndex)
    if (currentPolygon != null) {
        ImportBoundaryDialog(
            polygon = currentPolygon,
            index = pendingIndex,
            total = pendingPolygons.size,
            saving = importing,
            error = importError,
            onDismiss = { pendingPolygons = emptyList(); pendingIndex = 0 },
            onConfirm = { talhao ->
                viewModel.importBoundary(talhao, currentPolygon) { ok ->
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
                Column(modifier = Modifier.padding(12.dp)) { RawRecordFields(rows[i]) }
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
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    }
                    RawRecordFields(rows[i])
                }
            }
        }
    }
}

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(b.nome ?: b.talhao, style = MaterialTheme.typography.titleSmall)
                    }
                    Text("Talhão: ${b.talhao}", style = MaterialTheme.typography.bodySmall)
                    b.areaHaCalc?.let { Text("Área calculada: $it ha", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

/** Aba "Fazendas/KML" -- botão de importação nativa de KML/KMZ, mapa
 * (osmdroid) com o contorno de todos os talhões já importados, e a lista
 * resumo (mesma BoundariesList de sempre) embaixo. */
@Composable
private fun FazendasKmlTab(
    boundaries: List<FieldBoundaryDto>,
    importing: Boolean,
    onImportClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedButton(
            onClick = onImportClick,
            enabled = !importing,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(if (importing) "Importando..." else "Importar KML/KMZ")
        }
        BoundariesMap(boundaries)
        BoundariesList(boundaries, modifier = Modifier.weight(1f))
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
    onDismiss: () -> Unit,
    onConfirm: (talhao: String) -> Unit,
) {
    var talhao by remember(polygon) { mutableStateOf(polygon.name ?: "") }

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
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && talhao.isNotBlank(),
                onClick = { onConfirm(talhao.trim()) },
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}
