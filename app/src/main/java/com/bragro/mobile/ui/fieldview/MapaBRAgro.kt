package com.bragro.mobile.ui.fieldview

import com.bragro.mobile.BuildConfig
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay

/**
 * v77 -- mapa de ruas pelo servidor do BRAgro (/api/public/mapa/z/x/y.png),
 * que guarda cada pedaço em cache e pede ao OpenStreetMap identificado,
 * como a política de uso deles exige -- em vez de cada celular pedir direto
 * ao tile.openstreetmap.org (o MAPNIK padrão do osmdroid).
 * Crédito: © OpenStreetMap contributors.
 */
object MapaBRAgro {
    val fonte: OnlineTileSourceBase = XYTileSource(
        "BRAgroOSM", 0, 19, 256, ".png",
        arrayOf("${BuildConfig.API_BASE_URL}/api/public/mapa/"),
        "© OpenStreetMap contributors",
    )

    /** v1.6.4 (mockup v105, item 8) -- satélite (Esri World Imagery) pelo
     * servidor do BRAgro, igual à plataforma (/api/public/satelite/img). */
    val satelite: OnlineTileSourceBase = XYTileSource(
        "BRAgroSat", 0, 19, 256, ".jpg",
        arrayOf("${BuildConfig.API_BASE_URL}/api/public/satelite/img/"),
        "Imagem © Esri, Maxar, Earthstar Geographics",
    )
    private val rotulos: OnlineTileSourceBase = XYTileSource(
        "BRAgroRotulos", 0, 19, 256, ".png",
        arrayOf("${BuildConfig.API_BASE_URL}/api/public/satelite/rotulos/"),
        "",
    )

    /** Satélite + camada de nomes por cima (cidades, divisas). */
    fun aplicarSatelite(mapa: MapView) {
        mapa.setTileSource(satelite)
        if (mapa.overlays.none { it is org.osmdroid.views.overlay.TilesOverlay }) {
            val provider = org.osmdroid.tileprovider.MapTileProviderBasic(mapa.context, rotulos)
            val camada = org.osmdroid.views.overlay.TilesOverlay(provider, mapa.context).apply {
                loadingBackgroundColor = android.graphics.Color.TRANSPARENT
                loadingLineColor = android.graphics.Color.TRANSPARENT
            }
            mapa.overlays.add(0, camada)
        }
    }

    /** Crédito obrigatório da licença do OpenStreetMap ("© OpenStreetMap
     * contributors"), em texto pequeno no canto de baixo do mapa. Chamar
     * depois de desenhar os talhões, para o texto ficar por cima deles. */
    fun credito(mapa: MapView) {
        val atual = mapa.overlays.firstOrNull { it is CopyrightOverlay }
            ?: CopyrightOverlay(mapa.context).apply {
                setTextSize(10)
                setAlignBottom(true)
                setAlignRight(true)
            }
        mapa.overlays.remove(atual)
        mapa.overlays.add(atual)
    }
}
