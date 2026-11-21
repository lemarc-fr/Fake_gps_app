package com.lemarc.fake_gps.map

import androidx.annotation.StringRes
import com.lemarc.fake_gps.R

/**
 * Fonds de carte disponibles (tous gratuits et sans clé d'API), en tuiles raster de 256 px.
 *
 * @property tiles modèles d'URL ({z}/{x}/{y}) ; plusieurs entrées répartissent la charge entre serveurs.
 * @property maxZoom dernier niveau servi : au-delà, MapLibre agrandit les tuiles de ce niveau.
 * @property attribution mention obligatoire du fournisseur, affichée sur la carte.
 */
enum class MapStyle(
    @get:StringRes val labelRes: Int,
    val tiles: List<String>,
    val maxZoom: Int,
    val attribution: String,
) {
    IGN_PLAN(
        R.string.map_ign_plan,
        listOf(ignWmts(layer = "GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2", format = "image/png")),
        maxZoom = 19,
        attribution = IGN_ATTRIBUTION,
    ),
    IGN_PHOTO(
        R.string.map_ign_photo,
        listOf(ignWmts(layer = "ORTHOIMAGERY.ORTHOPHOTOS", format = "image/jpeg")),
        maxZoom = 20,
        attribution = IGN_ATTRIBUTION,
    ),
    OSM(
        R.string.map_osm,
        listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
        maxZoom = 19,
        attribution = "© OpenStreetMap contributors",
    ),
    TOPO(
        R.string.map_topo,
        listOf("a", "b", "c").map { "https://$it.tile.opentopomap.org/{z}/{x}/{y}.png" },
        maxZoom = 17,
        attribution = "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)",
    );

    companion object {
        val DEFAULT = IGN_PLAN

        /** Taille des tuiles servies, en pixels. */
        const val TILE_SIZE = 256

        fun fromName(name: String?): MapStyle = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

private const val IGN_ATTRIBUTION = "© IGN – Géoplateforme"

/** Tuiles WMTS de la Géoplateforme IGN (grille PM = Web Mercator, mêmes indices que {z}/{x}/{y}). */
private fun ignWmts(layer: String, format: String): String =
    "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
        "&LAYER=$layer&STYLE=normal&TILEMATRIXSET=PM&FORMAT=$format" +
        "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"
