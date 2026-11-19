package com.lemarc.fake_gps

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/** Fonds de carte disponibles (tous gratuits et sans clé d'API). */
enum class MapStyle(val label: String) {
    IGN_PLAN("Plan IGN"),
    IGN_PHOTO("Photo aérienne IGN"),
    CARTO_VOYAGER("CARTO Voyager"),
    CARTO_DARK("CARTO sombre"),
    OSM("OpenStreetMap"),
    TOPO("OpenTopoMap");

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
            CARTO_VOYAGER -> carto("CARTO-Voyager", "rastertiles/voyager")
            CARTO_DARK -> carto("CARTO-Dark", "dark_all")
            OSM -> TileSourceFactory.MAPNIK
            TOPO -> TileSourceFactory.OpenTopo
        }

        private fun carto(name: String, path: String) = XYTileSource(
            name, 0, 20, 256, ".png",
            arrayOf(
                "https://a.basemaps.cartocdn.com/$path/",
                "https://b.basemaps.cartocdn.com/$path/",
                "https://c.basemaps.cartocdn.com/$path/",
                "https://d.basemaps.cartocdn.com/$path/",
            ),
            "© OpenStreetMap contributors © CARTO",
            TileSourcePolicy(
                2,
                TileSourcePolicy.FLAG_NO_BULK or TileSourcePolicy.FLAG_NO_PREVENTIVE or
                    TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
            ),
        )
    }
}

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
