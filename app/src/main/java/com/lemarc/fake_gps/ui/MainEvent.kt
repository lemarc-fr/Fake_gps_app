package com.lemarc.fake_gps.ui

import androidx.annotation.StringRes

/** Événements ponctuels envoyés par [MainViewModel] à l'écran (livrés une seule fois). */
sealed interface MainEvent {

    /** Message court ; [args] remplissent les paramètres de la chaîne [messageRes]. */
    data class Info(@get:StringRes val messageRes: Int, val args: List<Any> = emptyList()) : MainEvent

    /** Échec d'une opération ; [messageRes] reçoit en paramètre la description de [error]. */
    data class Failure(@get:StringRes val messageRes: Int, val error: Throwable) : MainEvent

    /** Un trajet de [pointCount] points a été chargé : recadrer la carte dessus. */
    data class RouteLoaded(val pointCount: Int) : MainEvent
}
