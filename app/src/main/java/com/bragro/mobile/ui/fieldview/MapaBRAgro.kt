package com.bragro.mobile.ui.fieldview

import com.bragro.mobile.BuildConfig
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource

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
}
