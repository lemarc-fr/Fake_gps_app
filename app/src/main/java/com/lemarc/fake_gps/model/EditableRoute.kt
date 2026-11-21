package com.lemarc.fake_gps.model

/**
 * Trajet en cours d'édition.
 *
 * Chaque touche sur la carte ajoute un tronçon (« leg ») : un seul point en ligne droite, ou tous les
 * points d'un itinéraire routier. [legSizes] garde la taille de chaque tronçon pour que « Annuler »
 * retire le tronçon entier. Invariant : `legSizes.sum() == points.size`.
 */
class EditableRoute private constructor(
    val points: List<LatLon>,
    val legSizes: List<Int>,
) {
    val isEmpty: Boolean get() = points.isEmpty()

    /** Nombre de points touchés par l'utilisateur (= nombre de tronçons). */
    val tappedCount: Int get() = legSizes.size

    /** Index, dans [points], du dernier point de chaque tronçon (les points touchés). */
    val anchorIndices: List<Int>
        get() {
            var acc = 0
            return legSizes.map { acc += it; acc - 1 }
        }

    /** Longueur totale, en mètres. */
    val lengthMeters: Double get() = Geo.length(points)

    fun plusLeg(leg: List<LatLon>): EditableRoute =
        if (leg.isEmpty()) this else EditableRoute(points + leg, legSizes + leg.size)

    fun minusLastLeg(): EditableRoute {
        if (isEmpty) return this
        val count = legSizes.last()
        return EditableRoute(points.dropLast(count), legSizes.dropLast(1))
    }

    override fun equals(other: Any?): Boolean =
        other is EditableRoute && points == other.points && legSizes == other.legSizes

    override fun hashCode(): Int = 31 * points.hashCode() + legSizes.hashCode()

    companion object {
        val EMPTY = EditableRoute(emptyList(), emptyList())

        /**
         * Construit un trajet ; si [legSizes] est absent ou incohérent avec [points]
         * (anciennes données, fichier GPX…), chaque point devient son propre tronçon.
         */
        fun of(points: List<LatLon>, legSizes: List<Int>? = null): EditableRoute {
            val valid = legSizes != null && legSizes.all { it > 0 } && legSizes.sum() == points.size
            return EditableRoute(points, if (valid) legSizes else List(points.size) { 1 })
        }
    }
}
