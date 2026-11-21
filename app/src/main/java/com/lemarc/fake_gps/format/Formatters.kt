package com.lemarc.fake_gps.format

import java.util.Locale
import kotlin.math.floor

/**
 * Mise en forme des nombres affichés (interface et notification), selon la langue de l'appareil.
 * Les unités sont des symboles SI, identiques dans toutes les langues.
 */
object Formatters {

    /** Vitesse en km/h, sans unité : entier si rond ou ≥ 100, sinon une décimale. */
    fun speed(kmh: Double, locale: Locale = Locale.getDefault()): String =
        if (kmh >= 100 || kmh == floor(kmh)) {
            String.format(locale, "%.0f", kmh)
        } else {
            String.format(locale, "%.1f", kmh)
        }

    /** Distance avec unité : mètres sous 1 km, puis kilomètres avec deux décimales. */
    fun distance(meters: Double, locale: Locale = Locale.getDefault()): String =
        if (meters < 1000) {
            String.format(locale, "%.0f m", meters)
        } else {
            String.format(locale, "%.2f km", meters / 1000)
        }
}
