package com.lemarc.fake_gps.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/** Tuiles WMTS de la Géoplateforme IGN (data.geopf.fr), en projection Web Mercator (matrice « PM »). */
class IgnWmtsTileSource(
    name: String,
    private val layer: String,
    private val format: String,
    extension: String,
    maxZoom: Int,
) : OnlineTileSourceBase(
    name, 0, maxZoom, 256, extension,
    arrayOf("https://data.geopf.fr/wmts"),
    "© IGN – Géoplateforme",
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl +
            "?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
            "&LAYER=$layer&STYLE=normal&TILEMATRIXSET=PM&FORMAT=$format" +
            "&TILEMATRIX=${MapTileIndex.getZoom(pMapTileIndex)}" +
            "&TILEROW=${MapTileIndex.getY(pMapTileIndex)}" +
            "&TILECOL=${MapTileIndex.getX(pMapTileIndex)}"
}
