package com.lemarc.fake_gps

/** Signaux de vitesse préenregistrés (expressions v(t) en km/h). */
object SpeedPresets {

    data class Preset(val name: String, val expression: String)

    val ALL: List<Preset> = listOf(
        Preset("Marche", "5"),
        Preset("Vélo", "18"),
        Preset("Voiture en ville", "50"),
        Preset("Autoroute", "130"),
        Preset("Sinusoïde (période 60 s)", "50 + 20*sin(2*pi*t/60)"),
        Preset("Carré : 30 km/h / arrêt (2 min)", "15 + 15*square(2*pi*t/120)"),
        Preset("Triangle (période 90 s)", "50 + 30*tri(2*pi*t/90)"),
        Preset("Dent de scie (période 60 s)", "60 + 40*saw(2*pi*t/60)"),
        Preset("Rampe jusqu'à 130 km/h", "min(2*t; 130)"),
        Preset("Démarrage progressif", "90*(1 - exp(-t/20))"),
        Preset("Feux rouges (stop-and-go)", "if(mod(t; 90) < 30; 0; 50)"),
        Preset("Embouteillage", "abs(30*sin(t/40)) * (0.7 + 0.3*sin(t/7))"),
        Preset("Marche irrégulière", "5 + sin(t/7) + 0.5*sin(t/3)"),
        Preset("Jogging fractionné", "if(mod(t; 240) < 60; 14; 8)"),
        Preset("Aléatoire 40–60 km/h", "rand(40; 60)"),
        Preset("Accélère avec la distance", "20 + d/100"),
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
