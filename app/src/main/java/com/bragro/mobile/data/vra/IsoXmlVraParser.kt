package com.bragro.mobile.data.vra

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.xmlpull.v1.XmlPullParser

/**
 * Parser nativo de ISO-XML (ISO 11783-10) por zonas de tratamento (<TZN>) --
 * pedido do usuário ("crie no native como foi criado na plataforma"),
 * réplica 1:1 de parseIsoXmlZonas() em lib/geo-vra.ts (site), reescrito com
 * o parser XML embutido do Android (android.util.Xml/XmlPullParser), mesmo
 * critério já usado em KmlParser.kt -- sem nenhuma biblioteca nova.
 *
 * IMPORTANTE (mesmo limite de escopo do site, ver geo-vra.ts): só o formato
 * de zonas por POLÍGONO (<TZN>/<PLN>/<LSG>/<PNT>) é suportado. Mapas de
 * GRADE/RASTER (<GRD>) não são lidos -- exigiriam decodificar o layout
 * binário da grade e a tabela oficial de DDIs da norma ISO 11783-11, fora
 * do escopo desta rodada (o site tem a mesma limitação).
 *
 * Importação de .SHP (shapefile binário + DBF) continua exclusiva do site
 * (shpjs no navegador) -- reescrever esse parser em Kotlin sem uma
 * biblioteca equivalente disponível ficou fora do escopo desta rodada,
 * mesmo critério das integrações "aguardando parceria comercial"
 * (FieldView/Drone).
 */

class IsoXmlSemZonasException(message: String) : Exception(message)

/** Uma zona de tratamento lida do ISO-XML -- "rings" já em ordem GeoJSON
 * [lon,lat] (primeiro anel = externo, os demais = buracos), pronta pra
 * virar geometry.coordinates de uma Feature Polygon. */
data class ZonaTaxaVra(
    val nome: String?,
    val taxa: Double?,
    val ddiRef: String?,
    val rings: List<List<Pair<Double, Double>>>,
)

/** Lê um TASKDATA.XML (ISO 11783-10) escolhido pelo usuário (Storage Access
 * Framework, content:// uri) e devolve as zonas de tratamento encontradas.
 * Lança IsoXmlSemZonasException se o arquivo não tiver nenhuma zona por
 * polígono (provavelmente um mapa de grade/raster, não suportado) --
 * mesmo critério do site. */
fun parseIsoXmlZonas(context: Context, uri: Uri): List<ZonaTaxaVra> {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IsoXmlSemZonasException("Não foi possível ler o arquivo selecionado.")
    return parseIsoXmlBytes(bytes)
}

private fun parseIsoXmlBytes(bytes: ByteArray): List<ZonaTaxaVra> {
    val parser: XmlPullParser = Xml.newPullParser()
    parser.setInput(bytes.inputStream(), null)

    val zonas = mutableListOf<ZonaTaxaVra>()
    var temGrd = false

    var inTzn = false
    var tznNome: String? = null
    var tznDdiRef: String? = null
    var tznTaxa: Double? = null
    var tznTemPdv = false
    var tznRings = mutableListOf<List<Pair<Double, Double>>>()

    var inLsg = false
    var currentRing: MutableList<Pair<Double, Double>>? = null

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> {
                when (parser.name) {
                    "GRD" -> temGrd = true
                    "TZN" -> {
                        inTzn = true
                        tznNome = parser.getAttributeValue(null, "B")
                        tznDdiRef = null
                        tznTaxa = null
                        tznTemPdv = false
                        tznRings = mutableListOf()
                    }
                    // LSG (LineString) só nos interessa dentro de um PLN
                    // (Polygon) -- mesma restrição implícita do site
                    // (plnToRings só é chamado com os LSG de um PLN).
                    "LSG" -> if (inTzn) {
                        inLsg = true
                        currentRing = mutableListOf()
                    }
                    "PNT" -> if (inLsg) {
                        // PNT: C = latitude (PointNorth), D = longitude
                        // (PointEast) -- mesma leitura de plnToRings().
                        val lat = parser.getAttributeValue(null, "C")?.toDoubleOrNull()
                        val lon = parser.getAttributeValue(null, "D")?.toDoubleOrNull()
                        if (lat != null && lon != null) currentRing?.add(lat to lon)
                    }
                    // Primeiro <PDV> encontrado dentro da TZN (em qualquer
                    // profundidade) -- mesmo critério de
                    // tzn.getElementsByTagName("PDV")[0] no site.
                    "PDV" -> if (inTzn && !tznTemPdv) {
                        tznTemPdv = true
                        tznDdiRef = parser.getAttributeValue(null, "A")
                        tznTaxa = parser.getAttributeValue(null, "B")?.toDoubleOrNull()
                    }
                }
            }
            XmlPullParser.END_TAG -> {
                when (parser.name) {
                    "LSG" -> {
                        inLsg = false
                        // ring.size >= 3 -- mesmo filtro de plnToRings().
                        currentRing?.let { if (it.size >= 3) tznRings.add(it) }
                        currentRing = null
                    }
                    "TZN" -> {
                        inTzn = false
                        if (tznRings.isNotEmpty()) {
                            zonas.add(ZonaTaxaVra(tznNome, tznTaxa, tznDdiRef, tznRings.toList()))
                        }
                    }
                }
            }
        }
        eventType = parser.next()
    }

    if (zonas.isEmpty()) {
        throw IsoXmlSemZonasException(
            if (temGrd)
                "Este ISO-XML usa mapa de grade/raster (<GRD>), que ainda não é suportado -- só arquivos com zonas de tratamento por polígono (<TZN>)."
            else
                "Nenhuma zona de tratamento (<TZN>) encontrada neste arquivo."
        )
    }
    return zonas
}

/** Converte uma zona lida em uma GeoJSON Feature (Polygon + properties.taxa)
 * -- mesmo formato que savePrescricaoAction (site) espera em "features".
 * Ordem [lon,lat] em cada ponto (GeoJSON), invertida da leitura (lat,lon)
 * -- mesmo cuidado documentado em polygonToGeoJson() (KmlParser.kt). */
fun zonaParaFeatureJson(zona: ZonaTaxaVra): JsonObject {
    val rings = JsonArray(
        zona.rings.map { ring ->
            JsonArray(ring.map { (lat, lon) -> JsonArray(listOf(JsonPrimitive(lon), JsonPrimitive(lat))) })
        }
    )
    val geometry = JsonObject(mapOf("type" to JsonPrimitive("Polygon"), "coordinates" to rings))
    val properties = JsonObject(
        mapOf(
            "taxa" to (zona.taxa?.let { JsonPrimitive(it) } ?: JsonNull),
            "nome" to (zona.nome?.let { JsonPrimitive(it) } ?: JsonNull),
            "ddiRef" to (zona.ddiRef?.let { JsonPrimitive(it) } ?: JsonNull),
        )
    )
    return JsonObject(
        mapOf(
            "type" to JsonPrimitive("Feature"),
            "geometry" to (geometry as JsonElement),
            "properties" to (properties as JsonElement),
        )
    )
}
