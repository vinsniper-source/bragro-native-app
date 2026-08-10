package com.bragro.mobile.data.kml

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.cos

// Parser nativo de KML/KMZ (Task #110) -- substitui a dependencia do site
// (que usa @tmcw/togeojson no navegador) por um parser 100% on-device,
// usando so android.util.Xml/XmlPullParser (embutido no Android SDK) e
// java.util.zip (JDK puro), sem nenhuma biblioteca nova alem do osmdroid
// ja adicionado pro mapa. So le o anel externo (outerBoundaryIs) de cada
// <Polygon> encontrado -- buracos internos (innerBoundaryIs), se
// existirem, sao ignorados, o mesmo recorte que a maioria das ferramentas
// de agricultura faz pra contorno de talhao.

/** Um poligono (talhao) extraido do KML -- "points" em ordem (lat, lng),
 * ordem natural pra construir GeoPoint(lat, lon) do osmdroid. */
data class ParsedPolygon(val name: String?, val points: List<Pair<Double, Double>>)

/** Le um arquivo KML ou KMZ escolhido pelo usuario (Storage Access
 * Framework, content:// uri) e devolve um ParsedPolygon por
 * <Placemark><Polygon> encontrado. */
fun parseKmlOrKmz(context: Context, uri: Uri): List<ParsedPolygon> {
    val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return emptyList()

    // KMZ e so um .zip com um doc.kml (ou outro *.kml) dentro -- tenta
    // abrir como zip primeiro; se nao for um zip valido (ZipException ou
    // qualquer outro erro), assume que ja e KML puro (fallback
    // deliberado: o MIME type reportado pelos gerenciadores de arquivo
    // pra KML/KMZ e inconsistente entre apps).
    val kmlBytes = extractKmlEntryFromZip(rawBytes) ?: rawBytes

    return try {
        parseKmlBytes(kmlBytes)
    } catch (e: Exception) {
        emptyList()
    }
}

private fun extractKmlEntryFromZip(zipBytes: ByteArray): ByteArray? {
    return try {
        var found: ByteArray? = null
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".kml")) {
                    found = zis.readBytes()
                    break
                }
                entry = zis.nextEntry
            }
        }
        found
    } catch (e: Exception) {
        null
    }
}

private fun parseKmlBytes(bytes: ByteArray): List<ParsedPolygon> {
    val parser: XmlPullParser = Xml.newPullParser()
    parser.setInput(bytes.inputStream(), null)

    val polygons = mutableListOf<ParsedPolygon>()
    var placemarkName: String? = null
    var inPlacemark = false
    var inOuterBoundary = false
    var inCoordinates = false
    var currentRingPoints: List<Pair<Double, Double>>? = null
    val coordsText = StringBuilder()

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> {
                when (parser.name) {
                    "Placemark" -> {
                        inPlacemark = true
                        placemarkName = null
                        currentRingPoints = null
                    }
                    "name" -> {
                        if (inPlacemark && placemarkName == null) {
                            // nextText() consome START_TAG..END_TAG de "name"
                            // sozinho (contrato padrao do XmlPullParser) --
                            // depois disso o parser ja esta posicionado no
                            // END_TAG de "name", entao o parser.next() no fim
                            // do laco avanca corretamente pro proximo evento.
                            placemarkName = parser.nextText().trim().takeIf { it.isNotEmpty() }
                        }
                    }
                    "outerBoundaryIs" -> inOuterBoundary = true
                    "coordinates" -> {
                        if (inOuterBoundary) {
                            inCoordinates = true
                            coordsText.setLength(0)
                        }
                    }
                }
            }
            XmlPullParser.END_TAG -> {
                when (parser.name) {
                    "Placemark" -> inPlacemark = false
                    "outerBoundaryIs" -> inOuterBoundary = false
                    "coordinates" -> {
                        if (inCoordinates) {
                            inCoordinates = false
                            currentRingPoints = parseCoordinatesText(coordsText.toString())
                        }
                    }
                    "Polygon" -> {
                        currentRingPoints?.let { ring ->
                            if (ring.size >= 3) {
                                polygons.add(ParsedPolygon(placemarkName, ring))
                            }
                        }
                        currentRingPoints = null
                    }
                }
            }
            XmlPullParser.TEXT -> {
                if (inCoordinates) coordsText.append(parser.text)
            }
        }
        eventType = parser.next()
    }
    return polygons
}

/** Texto de <coordinates> e uma lista de triplas "lon,lat,alt" separadas
 * por espaco/quebra de linha (altitude e opcional/ignorada). Devolve
 * pares (lat, lon) -- ordem invertida da entrada, ver doc de ParsedPolygon. */
private fun parseCoordinatesText(raw: String): List<Pair<Double, Double>> {
    return raw.trim()
        .split(Regex("\\s+"))
        .mapNotNull { token ->
            if (token.isBlank()) return@mapNotNull null
            val parts = token.split(",")
            if (parts.size < 2) return@mapNotNull null
            val lon = parts[0].toDoubleOrNull()
            val lat = parts[1].toDoubleOrNull()
            if (lon == null || lat == null) null else lat to lon
        }
}

/** Converte um ParsedPolygon (pontos em ordem lat,lon) pra um objeto
 * GeoJSON Polygon padrao (coordenadas em ordem lon,lat -- ordem OPOSTA de
 * ParsedPolygon.points, cuidado ao usar). Usa construtores diretos de
 * JsonObject/JsonArray/JsonPrimitive (kotlinx.serialization.json) em vez
 * da DSL de builder, pra minimizar risco de erro de overload sem
 * compilador disponivel pra validar. */
fun polygonToGeoJson(polygon: ParsedPolygon): JsonObject {
    val ring = JsonArray(
        polygon.points.map { (lat, lon) ->
            JsonArray(listOf(JsonPrimitive(lon), JsonPrimitive(lat)))
        }
    )
    val coordinates = JsonArray(listOf(ring))
    return JsonObject(
        mapOf(
            "type" to JsonPrimitive("Polygon"),
            "coordinates" to coordinates,
        )
    )
}

/** Area aproximada (hectares) de um poligono via formula do cadarco
 * (shoelace) sobre uma projecao equirretangular local (x = lon * 111320 *
 * cos(latMedia), y = lat * 110540, ambos em metros) -- APROXIMACAO
 * deliberada (nao e uma projecao geodesica de verdade), aceitavel pro
 * tamanho tipico de um talhao agricola, mesmo criterio usado em varias
 * calculadoras de area simples. */
fun polygonAreaHectares(points: List<Pair<Double, Double>>): Double {
    if (points.size < 3) return 0.0
    val avgLatRad = Math.toRadians(points.map { it.first }.average())
    val projected = points.map { (lat, lon) ->
        val x = lon * 111320.0 * cos(avgLatRad)
        val y = lat * 110540.0
        x to y
    }
    var sum = 0.0
    for (i in projected.indices) {
        val (x1, y1) = projected[i]
        val (x2, y2) = projected[(i + 1) % projected.size]
        sum += x1 * y2 - x2 * y1
    }
    val areaM2 = abs(sum) / 2.0
    return areaM2 / 10000.0
}
