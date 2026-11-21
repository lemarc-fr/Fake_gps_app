package com.lemarc.fake_gps.map

import androidx.annotation.StringRes
import com.lemarc.fake_gps.R
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

/** Fonds de carte disponibles (tous gratuits et sans clé d'API). */
enum class MapStyle(@get:StringRes val labelRes: Int) {
    IGN_PLAN(R.string.map_ign_plan),
    IGN_PHOTO(R.string.map_ign_photo),
    OSM(R.string.map_osm),
    TOPO(R.string.map_topo);

    val tileSource: ITileSource by lazy { createSource(this) }

    companion object {
        val DEFAULT = IGN_PLAN

        fun fromName(name: String?): MapStyle = entries.firstOrNull { it.name == name } ?: DEFAULT

        private fun createSource(style: MapStyle): ITileSource = when (style) {
            IGN_PLAN -> IgnWmtsTileSource(
                name = "IGN-Plan",
                layer = "GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2",
                format = "image/png",
                extension = ".png",
                maxZoom = 19,
            )
            IGN_PHOTO -> IgnWmtsTileSource(
                name = "IGN-Ortho",
                layer = "ORTHOIMAGERY.ORTHOPHOTOS",
                format = "image/jpeg",
                extension = ".jpg",
                maxZoom = 20,
            )
            OSM -> TileSourceFactory.MAPNIK
            TOPO -> TileSourceFactory.OpenTopo
        }
    }
}
