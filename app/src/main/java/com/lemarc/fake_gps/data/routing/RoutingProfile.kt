package com.lemarc.fake_gps.data.routing

/** Mode de tracé : ligne droite, ou itinéraire calculé sur le réseau routier par OSRM. */
enum class RoutingProfile(val osrmPath: String?) {
    STRAIGHT(null),
    CAR("routed-car"),
    BIKE("routed-bike"),
    FOOT("routed-foot");

    companion object {
        fun fromName(name: String?): RoutingProfile = entries.firstOrNull { it.name == name } ?: STRAIGHT
    }
}
