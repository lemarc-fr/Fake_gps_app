package com.lemarc.fake_gps.map

import com.lemarc.fake_gps.model.LatLon
import org.osmdroid.api.IGeoPoint
import org.osmdroid.util.GeoPoint

/** Conversions entre le modèle de l'appli et les coordonnées d'osmdroid. */
fun IGeoPoint.toLatLon() = LatLon(latitude, longitude)

fun LatLon.toGeoPoint() = GeoPoint(lat, lon)
