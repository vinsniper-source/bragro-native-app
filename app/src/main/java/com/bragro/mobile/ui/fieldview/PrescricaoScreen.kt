package com.bragro.mobile.ui.fieldview

import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.Refresh
import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.basicMarquee
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.outlined.Info
import com.bragro.mobile.ui.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.NetworkStatus
import com.bragro.mobile.data.model.PrescricaoData
import com.bragro.mobile.data.repo.PrescricaoRepository
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.print.HtmlPrinter
import com.bragro.mobile.ui.util.shareBinaryFile
import com.bragro.mobile.ui.util.shareTextFile
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon as OsmPolygon
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** "Prescrição / Taxa Variável" (Task #604/#608) -- busca em
 * /api/mobile/prescricao os registros salvos (savePrescricaoAction já
 * resolveu taxaMedia/Min/Max e gravou o geojson) e desenha as zonas
 * coloridas por taxa. Mapa via osmdroid (mesmo padrão de BoundariesMap em
 * FieldviewScreen.kt), sem WebView/Leaflet -- cada FEATURE do
 * FeatureCollection vira um polígono colorido pela taxa (fórmula HSL
 * idêntica a corPorTaxa() do site, ver prescricao-map-view.tsx).
 *
 * A partir da v1.2.85 (Task #651, pedido do usuário "crie no native como
 * foi criado na plataforma") o FAB "+" abre PrescricaoNovoScreen, que
 * também cria prescrições -- só a partir de ISO-XML importado no aparelho
 * (ver IsoXmlVraParser.kt); importação de SHP continua exclusiva do site.
 *
 * A EXPORTAÇÃO de SHP e ISO-XML (Task #898, pedido do usuário "dê paridade
 * ao SHP no app nativo") passou a existir aqui também -- diferente da
 * importação, exportar não precisa decodificar o formato binário no
 * aparelho: o SERVIDOR gera os bytes (exportarShpAction/
 * exportarIsoXmlAction, prescricao/actions.ts, mesma lib @mapbox/shp-write
 * usada pelo site, só rodando em Node) e devolve pronto; o app só
 * decodifica/grava/compartilha (shareBinaryFile/shareTextFile).
 */
class PrescricaoViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PrescricaoRepository(app)

    var carregando = mutableStateOf(true)
        private set
    var erro = mutableStateOf<String?>(null)
        private set
    var prescricoes = mutableStateOf<List<PrescricaoData>>(emptyList())
        private set
    // Ícone Nuvem no cabeçalho (mesmo padrão de DomainListScreen/offline) --
    // true quando o último fetch falhou, pra avisar que a lista pode estar
    // desatualizada/vazia por falta de conexão, não por não ter dado cadastrado.
    var offline = mutableStateOf(false)
        private set
    // Id da prescrição com uma exportação em andamento (SHP ou ISO-XML) --
    // desabilita os 2 botões daquele card enquanto a chamada ao servidor
    // não volta, mesmo critério de pendingAction em outras telas.
    var exportandoId = mutableStateOf<String?>(null)
        private set

    init {
        carregar()
    }

    /** Exportar SHP (Task #898) -- onResult recebe os bytes do .zip já
     * decodificados + nome do arquivo, ou null+mensagem em caso de falha.
     * Quem chamar (PrescricaoScreen) é responsável por shareBinaryFile. */
    fun exportarShp(id: String, onResult: (ByteArray?, String?, String) -> Unit) {
        exportandoId.value = id
        viewModelScope.launch {
            val resultado = repository.exportarShp(id)
            exportandoId.value = null
            if (resultado == null || !resultado.ok || resultado.base64.isNullOrBlank()) {
                onResult(null, resultado?.error ?: "Sem conexão -- não foi possível exportar agora.", "")
                return@launch
            }
            try {
                val bytes = android.util.Base64.decode(resultado.base64, android.util.Base64.DEFAULT)
                onResult(bytes, null, resultado.filename ?: "prescricao.zip")
            } catch (e: Exception) {
                onResult(null, "Falha ao processar o arquivo recebido.", "")
            }
        }
    }

    /** Exportar ISO-XML (TASKDATA.XML) -- devolve o texto já pronto (sem
     * base64, ver exportarIsoXmlAction no servidor). */
    fun exportarIsoXml(id: String, onResult: (String?, String?, String) -> Unit) {
        exportandoId.value = id
        viewModelScope.launch {
            val resultado = repository.exportarIsoXml(id)
            exportandoId.value = null
            if (resultado == null || !resultado.ok || resultado.xml.isNullOrBlank()) {
                onResult(null, resultado?.error ?: "Sem conexão -- não foi possível exportar agora.", "")
                return@launch
            }
            onResult(resultado.xml, null, resultado.filename ?: "TASKDATA.xml")
        }
    }

    /** Reaproveitada tanto no carregamento inicial quanto ao voltar da tela
     * "Nova Prescrição" (Task #651) -- sem cache no Room aqui, então sem
     * isso a prescrição recém-criada só apareceria depois de fechar e
     * reabrir o módulo. */
    fun carregar() {
        viewModelScope.launch {
            val resultado = repository.fetch()
            carregando.value = false
            offline.value = resultado == null
            if (resultado == null) {
                erro.value = "Sem conexão -- não foi possível carregar as prescrições agora."
                return@launch
            }
            erro.value = null
            prescricoes.value = resultado.prescricoes
        }
    }
}

/** Mesma fórmula de corPorTaxa() do site (prescricao-map-view.tsx): gradiente
 * HSL contínuo verde (hue 120) -> vermelho (hue 0) conforme a posição
 * relativa da taxa entre min e max do conjunto. Sem valor/faixa uniforme
 * (max<=min) cai no cinza neutro (slate-400), igual ao site. */
private fun corPorTaxa(taxa: Double?, minTaxa: Double, maxTaxa: Double): Int {
    if (taxa == null || !taxa.isFinite() || maxTaxa <= minTaxa) return android.graphics.Color.parseColor("#94a3b8")
    val t = max(0.0, min(1.0, (taxa - minTaxa) / (maxTaxa - minTaxa)))
    val hue = (120 - t * 120).toFloat()
    // android.graphics.Color só tem HSVToColor (value=brilho), o site usa
    // hsl(hue,70%,45%) (lightness) -- converte manualmente pra não
    // distorcer as cores usando os mesmos números num espaço diferente.
    return hslToRgb(hue, 0.70f, 0.45f)
}

private fun hslToRgb(hueDeg: Float, saturation: Float, lightness: Float): Int {
    val c = (1 - kotlin.math.abs(2 * lightness - 1)) * saturation
    val hPrime = (hueDeg % 360) / 60f
    val x = c * (1 - kotlin.math.abs(hPrime % 2 - 1))
    val (r1, g1, b1) = when {
        hPrime < 1 -> Triple(c, x, 0f)
        hPrime < 2 -> Triple(x, c, 0f)
        hPrime < 3 -> Triple(0f, c, x)
        hPrime < 4 -> Triple(0f, x, c)
        hPrime < 5 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = lightness - c / 2
    val r = ((r1 + m) * 255).roundToInt().coerceIn(0, 255)
    val g = ((g1 + m) * 255).roundToInt().coerceIn(0, 255)
    val b = ((b1 + m) * 255).roundToInt().coerceIn(0, 255)
    return android.graphics.Color.rgb(r, g, b)
}

/** Cada Feature do FeatureCollection vira um polígono (anel externo,
 * coordinates[0], mesmo recorte de geoJsonPolygonToGeoPoints em
 * FieldviewScreen.kt) + a taxa (properties.taxa) pra colorir. */
private fun parseFeatures(geojson: JsonElement?): List<Pair<List<GeoPoint>, Double?>> {
    if (geojson == null) return emptyList()
    return try {
        val features = geojson.jsonObject["features"]?.jsonArray ?: return emptyList()
        features.mapNotNull { feature ->
            val geometry = feature.jsonObject["geometry"] ?: return@mapNotNull null
            val coordinates = geometry.jsonObject["coordinates"]?.jsonArray ?: return@mapNotNull null
            val outerRing = coordinates.firstOrNull()?.jsonArray ?: return@mapNotNull null
            val points = outerRing.mapNotNull { coordPair ->
                val pair = coordPair.jsonArray
                if (pair.size < 2) return@mapNotNull null
                val lon = pair[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                val lat = pair[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                GeoPoint(lat, lon)
            }
            val taxa = feature.jsonObject["properties"]?.jsonObject?.get("taxa")?.jsonPrimitive?.doubleOrNull
            if (points.size >= 3) points to taxa else null
        }
    } catch (e: Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PrescricaoScreen(onBack: () -> Unit, onNovo: () -> Unit = {}, viewModel: PrescricaoViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir
        }
    }
    // Recarrega ao voltar da tela "Nova Prescrição" (Task #651) -- o
    // ViewModel sobrevive na pilha de navegação, então sem isso a
    // prescrição recém-criada só apareceria depois de fechar/reabrir o
    // módulo (sem cache no Room aqui pra atualizar sozinho).
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.carregar()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val carregando by viewModel.carregando
    val erro by viewModel.erro
    val prescricoes by viewModel.prescricoes
    val offline by viewModel.offline
    val exportandoId by viewModel.exportandoId
    var expandidoId by remember { mutableStateOf<String?>(null) }
    var erroExportacao by remember { mutableStateOf<String?>(null) }
    var lancAberto by remember { mutableStateOf(false) }
    var infoAberto by remember { mutableStateOf(false) }
    val imprimir: () -> Unit = {
        val headers = listOf("Nome", "Produto", "Safra", "Cultura", "Talhão", "Taxa mín.", "Taxa média", "Taxa máx.")
        val rows = prescricoes.map {
            listOf(
                it.nome, it.produto ?: "", it.safra ?: "", it.cultura ?: "", it.talhao ?: "",
                it.taxaMin?.let { v -> "%,.2f".format(java.util.Locale("pt", "BR"), v) } ?: "",
                it.taxaMedia?.let { v -> "%,.2f".format(java.util.Locale("pt", "BR"), v) } ?: "",
                it.taxaMax?.let { v -> "%,.2f".format(java.util.Locale("pt", "BR"), v) } ?: "",
            )
        }
        HtmlPrinter.printSimpleTable(context, "Prescrição / Taxa Variável", headers, rows)
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
                // Título desce uma linha, mesmo ajuste já aplicado nos
                // demais módulos (DomainListScreen/FinanceiroScreen) --
                // pedido do usuário ("abaixe o título do módulo e deixe na
                // mesma altura dos modulos mais antigos").
                title = { androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                    // v21 -- ícone verde do módulo ao lado do título.
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        com.bragro.mobile.ui.domain.IconeNegrito(androidx.compose.material.icons.Icons.Outlined.Map, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, size = 22.dp, modifier = androidx.compose.ui.Modifier.padding(end = 8.dp, bottom = 3.dp))

                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Prescrição / Taxa Variável", maxLines = 1, overflow = TextOverflow.Clip, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.basicMarquee())
                    }
                
                    }
                } },
                // Ícones Imprimir + Nuvem na mesma altura do título -- pedido
                // do usuário ("inclua o icone e nuvem e impressoora na mesma
                // altura do título nesses módulos"), mesmo padrão dos demais
                // módulos (ver DomainListScreen.kt).
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.HomeNavButton()
                    }
                },
            )
        },
        // Botão flutuante removido -- "+ Nova" fica no bloco Ações (padrão v7).
    ) { padding ->
        if (carregando) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (erro != null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(erro ?: "", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "painel") {
                com.bragro.mobile.ui.domain.PainelMonitoramento(
                    com.bragro.mobile.ui.domain.PainelModulo(
                        titulo = "Prescrição / Taxa variável",
                        subtitulo = "${prescricoes.size} prescrição(ões) salvas",
                        kpis = listOf(
                            com.bragro.mobile.ui.domain.PainelKpi("Prescrições", prescricoes.size.toDouble(), "numero", null, "mapas salvos", "Lançamentos"),
                            com.bragro.mobile.ui.domain.PainelKpi("Produtos", prescricoes.mapNotNull { it.produto?.takeIf { s -> s.isNotBlank() } }.toSet().size.toDouble(), "numero", null, "diferentes", "Lançamentos"),
                            com.bragro.mobile.ui.domain.PainelKpi("Em SHP", prescricoes.count { it.origemTipo == "SHP" }.toDouble(), "numero", null, "importadas de .zip", "Lançamentos"),
                            com.bragro.mobile.ui.domain.PainelKpi("Em ISO-XML", prescricoes.count { it.origemTipo == "ISO_XML" }.toDouble(), "numero", null, "TASKDATA.XML", "Lançamentos"),
                        ),
                        barras = emptyList(),
                    ),
                    onKpi = { lancAberto = true },
                )
            }
            item(key = "acoes") {
                com.bragro.mobile.ui.domain.AcoesEmAbas(
                    dados = {
                        com.bragro.mobile.ui.domain.LabeledIconButton(
                            icon = Icons.AutoMirrored.Outlined.ViewList,
                            label = "Lançamentos",
                            active = lancAberto,
                            onClick = { lancAberto = !lancAberto },
                        )
                        com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.Info, label = "Formatos", onClick = { infoAberto = true })
                    },
                    operacoes = {
                        com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.Add, label = "+ Nova", onClick = onNovo)
                        com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.Refresh, label = "Atualizar", onClick = { viewModel.carregar() })
                        com.bragro.mobile.ui.domain.NuvemButton()
                    },
                    arquivos = {
                        com.bragro.mobile.ui.domain.ArquivosTabela("Prescrição / Taxa Variável", listOf("Nome", "Produto", "Safra", "Cultura", "Talhão", "Taxa mín.", "Taxa média", "Taxa máx.")) {
                            prescricoes.map {
                                listOf(
                                    it.nome, it.produto ?: "", it.safra ?: "", it.cultura ?: "", it.talhao ?: "",
                                    it.taxaMin?.let { v -> "%,.2f".format(java.util.Locale("pt", "BR"), v) } ?: "",
                                    it.taxaMedia?.let { v -> "%,.2f".format(java.util.Locale("pt", "BR"), v) } ?: "",
                                    it.taxaMax?.let { v -> "%,.2f".format(java.util.Locale("pt", "BR"), v) } ?: "",
                                )
                            }
                        }
                    },
                )
            }
            item(key = "lanc-header") {
                com.bragro.mobile.ui.domain.CabecalhoLancamentos(total = prescricoes.size, aberto = lancAberto, titulo = "Prescrições") { lancAberto = !lancAberto }
            }
            if (prescricoes.isEmpty()) item {
                Text("Nenhuma prescrição de taxa variável salva ainda -- toque em + Nova pra importar um ISO-XML, ou crie pelo site (SHP/ISO-XML).", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
            }
            if (lancAberto) items(prescricoes) { p ->
                PrescricaoCard(
                    p = p,
                    expandido = expandidoId == p.id,
                    onToggle = { expandidoId = if (expandidoId == p.id) null else p.id },
                    exportando = exportandoId == p.id,
                    onExportarShp = {
                        viewModel.exportarShp(p.id) { bytes, erroMsg, filename ->
                            if (bytes != null) shareBinaryFile(context, bytes, filename, "application/zip")
                            else erroExportacao = erroMsg
                        }
                    },
                    onExportarIsoXml = {
                        viewModel.exportarIsoXml(p.id) { xml, erroMsg, filename ->
                            if (xml != null) shareTextFile(context, filename, "application/xml", xml)
                            else erroExportacao = erroMsg
                        }
                    },
                )
            }
        }
    }

    if (infoAberto) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { infoAberto = false },
            title = { Text("Formatos suportados") },
            text = { Text("ISO-XML (TASKDATA.XML) pelo app ou pelo site; Shapefile (.zip com .shp/.dbf) pelo site. Exporta em SHP e ISO-XML pelos ícones de cada prescrição.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { infoAberto = false }) { Text("Ok") } },
        )
    }

    if (erroExportacao != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { erroExportacao = null },
            title = { Text("Não foi possível exportar") },
            text = { Text(erroExportacao ?: "") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { erroExportacao = null }) { Text("Ok") }
            },
        )
    }
}

@Composable
private fun PrescricaoCard(
    p: PrescricaoData,
    expandido: Boolean,
    onToggle: () -> Unit,
    exportando: Boolean = false,
    onExportarShp: () -> Unit = {},
    onExportarIsoXml: () -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.nome, style = MaterialTheme.typography.bodyLarge)
                    val subtitulo = listOfNotNull(p.produto, p.safra, p.cultura, p.talhao).joinToString(" • ")
                    if (subtitulo.isNotBlank()) Text(subtitulo, style = MaterialTheme.typography.labelSmall)
                }
                // Exportar SHP/ISO-XML (Task #898, paridade com os 2 botões
                // já existentes no site, onExportarShp/onExportarIsoXml em
                // prescricao-client.tsx) -- bytes/texto gerados no servidor,
                // ver PrescricaoViewModel.exportarShp/exportarIsoXml acima.
                if (exportando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onExportarShp) {
                        Icon(Icons.Outlined.Download, contentDescription = "Exportar SHP", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onExportarIsoXml) {
                        Icon(Icons.Outlined.Description, contentDescription = "Exportar ISO-XML", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                com.bragro.mobile.ui.domain.CantoL(expandido, onClick = onToggle)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val unidade = p.unidadeTaxa?.let { " $it" } ?: ""
                Column {
                    Text("Taxa mín.", style = MaterialTheme.typography.labelSmall)
                    Text("${p.taxaMin?.let { "%,.2f".format(java.util.Locale("pt", "BR"), it) } ?: "--"}$unidade", style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text("Taxa média", style = MaterialTheme.typography.labelSmall)
                    Text("${p.taxaMedia?.let { "%,.2f".format(java.util.Locale("pt", "BR"), it) } ?: "--"}$unidade", style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text("Taxa máx.", style = MaterialTheme.typography.labelSmall)
                    Text("${p.taxaMax?.let { "%,.2f".format(java.util.Locale("pt", "BR"), it) } ?: "--"}$unidade", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (expandido) {
                PrescricaoMap(p)
            }
        }
    }
}

/** Mapa nativo (osmdroid, MAPNIK) das zonas de uma prescrição -- um polígono
 * por Feature, colorido pela taxa (gradiente verde->vermelho, mesma fórmula
 * do site). Sem overlay de contorno grosso: fillAlpha mais alto que
 * BoundariesMap (aqui a COR é a informação, não só o contorno). */
@Composable
private fun PrescricaoMap(p: PrescricaoData) {
    val zonas = remember(p.id) { parseFeatures(p.geojson) }
    val minTaxa = p.taxaMin ?: zonas.mapNotNull { it.second }.minOrNull() ?: 0.0
    val maxTaxa = p.taxaMax ?: zonas.mapNotNull { it.second }.maxOrNull() ?: 0.0
    if (zonas.isEmpty()) {
        Text("Sem zonas geográficas nesta prescrição.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
        return
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(280.dp).padding(vertical = 8.dp),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
            }
        },
        update = { mv ->
            mv.overlays.clear()
            val allPoints = mutableListOf<GeoPoint>()
            zonas.forEach { (points, taxa) ->
                val cor = corPorTaxa(taxa, minTaxa, maxTaxa)
                val polygon = OsmPolygon(mv)
                polygon.setPoints(points)
                polygon.setStrokeColor(android.graphics.Color.parseColor("#1e293b"))
                polygon.setStrokeWidth(2f)
                // fillOpacity 0.55 do site -- 0.55*255 ~= 140 (0x8C).
                // 0x8C000000 excede Int.MAX_VALUE como literal (Kotlin o
                // trata como Long) -- .toInt() reinterpreta os mesmos 32
                // bits como Int (com sinal), igual Java faria implicitamente.
                polygon.setFillColor((cor and 0x00FFFFFF) or 0x8C000000.toInt())
                mv.overlays.add(polygon)
                allPoints.addAll(points)
            }
            if (allPoints.isNotEmpty()) mv.controller.setCenter(allPoints.first())
            mv.invalidate()
        },
        onRelease = { mapView -> mapView.onDetach() },
    )
}
