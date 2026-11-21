package com.lemarc.fake_gps.engine

import androidx.annotation.StringRes
import com.lemarc.fake_gps.R

/** Signaux de vitesse préenregistrés (expressions v(t) en km/h). */
object SpeedPresets {

    /** [nameRes] : libellé affiché ; [expression] : expression v(t), indépendante de la langue. */
    data class Preset(@get:StringRes val nameRes: Int, val expression: String)

    val ALL: List<Preset> = listOf(
        Preset(R.string.preset_walk, "5"),
        Preset(R.string.preset_bike, "18"),
        Preset(R.string.preset_city, "50"),
        Preset(R.string.preset_highway, "130"),
        Preset(R.string.preset_sine, "50 + 20*sin(2*pi*t/60)"),
        Preset(R.string.preset_square, "15 + 15*square(2*pi*t/120)"),
        Preset(R.string.preset_triangle, "50 + 30*tri(2*pi*t/90)"),
        Preset(R.string.preset_sawtooth, "60 + 40*saw(2*pi*t/60)"),
        Preset(R.string.preset_ramp, "min(2*t; 130)"),
        Preset(R.string.preset_soft_start, "90*(1 - exp(-t/20))"),
        Preset(R.string.preset_traffic_lights, "if(mod(t; 90) < 30; 0; 50)"),
        Preset(R.string.preset_traffic_jam, "abs(30*sin(t/40)) * (0.7 + 0.3*sin(t/7))"),
        Preset(R.string.preset_irregular_walk, "5 + sin(t/7) + 0.5*sin(t/3)"),
        Preset(R.string.preset_intervals, "if(mod(t; 240) < 60; 14; 8)"),
        Preset(R.string.preset_random, "rand(40; 60)"),
        Preset(R.string.preset_distance_accel, "20 + d/100"),
    )

    /**
     * Échantillonne v(t) sur [0 ; durationS] pour l'aperçu, en intégrant la distance
     * parcourue (variable d) au fil du temps.
     */
    fun sample(expr: SpeedExpression, durationS: Double = 300.0, samples: Int = 150): DoubleArray {
        val out = DoubleArray(samples)
        val dt = durationS / (samples - 1)
        var d = 0.0
        for (i in 0 until samples) {
            val v = expr.speedKmh(i * dt, d)
            out[i] = if (v.isInfinite()) Double.MAX_VALUE else v
            d += v / 3.6 * dt
        }
        return out
    }
}
