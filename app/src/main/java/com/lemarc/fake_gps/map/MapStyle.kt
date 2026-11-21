package com.lemarc.fake_gps.map

import androidx.annotation.StringRes
import com.lemarc.fake_gps.R

/**
 * Fonds de carte disponibles (tous gratuits et sans clé d'API).
 *
 * @property source d'où viennent les tuiles : style vectoriel publié, ou images raster.
 * @property attribution mention obligatoire du fournisseur, affichée sur la carte.
 */
enum class MapStyle(
    @get:StringRes val labelRes: Int,
    val source: MapSource,
    val attribution: String,
) {
    IGN_PLAN(
        R.string.map_ign_plan,
        MapSource.Vector("https://data.geopf.fr/annexes/ressources/vectorTiles/styles/PLAN.IGN/standard.json"),
        attribution = IGN_ATTRIBUTION,
    ),
    IGN_PHOTO(
        R.string.map_ign_photo,
        MapSource.Raster(listOf(ignWmts(layer = "ORTHOIMAGERY.ORTHOPHOTOS", format = "image/jpeg")), maxZoom = 20),
        attribution = IGN_ATTRIBUTION,
    ),
    OSM(
        R.string.map_osm,
        MapSource.Vector("https://tiles.openfreemap.org/styles/liberty"),
        attribution = "© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors",
    ),
    TOPO(
        R.string.map_topo,
        MapSource.Raster(listOf("a", "b", "c").map { "https://$it.tile.opentopomap.org/{z}/{x}/{y}.png" }, maxZoom = 17),
        attribution = "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)",
    );

    companion object {
        val DEFAULT = IGN_PLAN

        fun fromName(name: String?): MapStyle = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Origine des tuiles d'un fond de carte. */
sealed interface MapSource {

    /**
     * Tuiles vectorielles dessinées par MapLibre selon un style publié (sources, polices, icônes) :
     * nettes à tout zoom, libellés toujours droits.
     */
    data class Vector(val styleUrl: String) : MapSource

    /**
     * Images de 256 px déjà dessinées : seul format possible pour les photos aériennes.
     *
     * @property tiles modèles d'URL ({z}/{x}/{y}) ; plusieurs entrées répartissent la charge entre serveurs.
     * @property maxZoom dernier niveau servi : au-delà, MapLibre agrandit les tuiles de ce niveau.
     */
    data class Raster(val tiles: List<String>, val maxZoom: Int) : MapSource {
        companion object {
            const val TILE_SIZE = 256
        }
    }
}

private const val IGN_ATTRIBUTION = "© IGN – Géoplateforme"

/** Tuiles WMTS de la Géoplateforme IGN (grille PM = Web Mercator, mêmes indices que {z}/{x}/{y}). */
private fun ignWmts(layer: String, format: String): String =
    "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
        "&LAYER=$layer&STYLE=normal&TILEMATRIXSET=PM&FORMAT=$format" +
        "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"
