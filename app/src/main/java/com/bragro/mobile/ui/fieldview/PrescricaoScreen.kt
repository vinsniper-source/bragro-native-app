package com.bragro.mobile.ui.fieldview

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.PrescricaoData
import com.bragro.mobile.data.repo.PrescricaoRepository
import com.bragro.mobile.ui.theme.Card
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

/** "Prescrição / Taxa Variável" (Task #604/#608) -- VISUALIZADOR simples no
 * app nativo, decisão do usuário: em vez de gerar SHP/ISO-XML no aparelho
 * (o site faz isso no navegador com shpjs, bem mais pesado de replicar em
 * Kotlin), o app só busca em /api/mobile/prescricao os registros já
 * salvos pelo site (savePrescricaoAction já resolveu taxaMedia/Min/Max e
 * gravou o geojson) e desenha as zonas coloridas por taxa -- criar/excluir
 * prescrição continua exclusivo do site. Mapa via osmdroid (mesmo padrão de
 * BoundariesMap em FieldviewScreen.kt), sem WebView/Leaflet -- só que aqui
 * cada FEATURE do FeatureCollection vira um polígono colorido pela taxa
 * (fórmula HSL idêntica a corPorTaxa() do site, ver prescricao-map-view.tsx).
 */
class PrescricaoViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PrescricaoRepository(app)

    var carregando = mutableStateOf(true)
        private set
    var erro = mutableStateOf<String?>(null)
        private set
    var prescricoes = mutableStateOf<List<PrescricaoData>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            val resultado = repository.fetch()
            carregando.value = false
            if (resultado == null) {
                erro.value = "Sem conexão -- não foi possível carregar as prescrições agora."
                return@launch
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescricaoScreen(onBack: () -> Unit, viewModel: PrescricaoViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir
        }
    }
    val carregando by viewModel.carregando
    val erro by viewModel.erro
    val prescricoes by viewModel.prescricoes
    var expandidoId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prescrição / Taxa Variável", color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        },
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
        if (prescricoes.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nenhuma prescrição de taxa variável salva ainda -- crie uma pelo site (importação SHP/ISO-XML).", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(prescricoes) { p ->
                PrescricaoCard(
                    p = p,
                    expandido = expandidoId == p.id,
                    onToggle = { expandidoId = if (expandidoId == p.id) null else p.id },
                )
            }
        }
    }
}

@Composable
private fun PrescricaoCard(p: PrescricaoData, expandido: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.nome, style = MaterialTheme.typography.bodyLarge)
                    val subtitulo = listOfNotNull(p.produto, p.safra, p.cultura, p.talhao).joinToString(" • ")
                    if (subtitulo.isNotBlank()) Text(subtitulo, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onToggle) {
                    Icon(if (expandido) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expandido) "Recolher mapa" else "Ver mapa")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val unidade = p.unidadeTaxa?.let { " $it" } ?: ""
                Column {
                    Text("Taxa mín.", style = MaterialTheme.typography.labelSmall)
                    Text("${p.taxaMin?.let { "%.2f".format(it) } ?: "--"}$unidade", style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text("Taxa média", style = MaterialTheme.typography.labelSmall)
                    Text("${p.taxaMedia?.let { "%.2f".format(it) } ?: "--"}$unidade", style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text("Taxa máx.", style = MaterialTheme.typography.labelSmall)
                    Text("${p.taxaMax?.let { "%.2f".format(it) } ?: "--"}$unidade", style = MaterialTheme.typography.bodyMedium)
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
