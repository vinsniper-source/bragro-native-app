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
