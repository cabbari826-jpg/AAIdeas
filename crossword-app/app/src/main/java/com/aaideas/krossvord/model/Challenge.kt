package com.aaideas.krossvord.model

import java.time.YearMonth

/** A stop on a month's route (lon/lat for the little map). */
data class Place(val name: String, val lon: Float, val lat: Float)

data class Route(val title: String, val places: List<Place>)

/**
 * "Ayın yolu": each month a road from Bakı to a corner of Azerbaijan. Every level solved this
 * month (for the first time) moves the car along; [GOAL] levels reach the last stop. Finishing
 * gives [REWARD_HINTS] free letter hints. The record is the day of the month the player finished.
 */
object Challenge {
    const val GOAL = 20
    const val REWARD_HINTS = 3

    private fun p(n: String, lon: Double, lat: Double) = Place(n, lon.toFloat(), lat.toFloat())
    private val BAKI = p("Bakı", 49.87, 40.41)

    val ROUTES = listOf(
        Route("Qərb yolu: Bakıdan Qazağa", listOf(BAKI, p("Şamaxı", 48.64, 40.63), p("Ağsu", 48.40, 40.57), p("Kürdəmir", 48.16, 40.34), p("Yevlax", 47.15, 40.62), p("Gəncə", 46.36, 40.68), p("Şəmkir", 46.02, 40.83), p("Tovuz", 45.63, 40.99), p("Qazax", 45.36, 41.09))),
        Route("Şimal yolu: Bakıdan Xınalığa", listOf(BAKI, p("Sumqayıt", 49.67, 40.59), p("Siyəzən", 49.11, 41.08), p("Xaçmaz", 48.80, 41.46), p("Quba", 48.51, 41.36), p("Qusar", 48.43, 41.43), p("Xınalıq", 48.13, 41.18))),
        Route("Cənub yolu: Bakıdan Astaraya", listOf(BAKI, p("Ələt", 49.41, 39.95), p("Şirvan", 48.93, 39.93), p("Salyan", 48.98, 39.60), p("Masallı", 48.67, 39.03), p("Lənkəran", 48.85, 38.75), p("Astara", 48.87, 38.46))),
        Route("Qarabağ yolu: Bakıdan Şuşaya", listOf(BAKI, p("Kürdəmir", 48.16, 40.34), p("Yevlax", 47.15, 40.62), p("Bərdə", 47.13, 40.37), p("Ağdam", 46.93, 39.99), p("Füzuli", 47.15, 39.60), p("Şuşa", 46.75, 39.76))),
        Route("Şəki yolu: Bakıdan Balakənə", listOf(BAKI, p("Şamaxı", 48.64, 40.63), p("İsmayıllı", 48.15, 40.79), p("Qəbələ", 47.85, 40.98), p("Şəki", 47.17, 41.19), p("Qax", 46.93, 41.42), p("Zaqatala", 46.65, 41.63), p("Balakən", 46.41, 41.73))),
    )

    fun routeFor(month: YearMonth): Route = ROUTES[Math.floorMod(month.year * 12 + month.monthValue, ROUTES.size)]

    /** Where the car is: 0 at Bakı, places.lastIndex at the end (fractions in between). */
    fun position(route: Route, levels: Int): Float =
        levels.coerceIn(0, GOAL).toFloat() / GOAL * route.places.lastIndex

    /** The last stop reached. */
    fun reached(route: Route, levels: Int): Place = route.places[position(route, levels).toInt()]

    /** Levels left to the next stop, or 0 at the end. */
    fun toNextStop(route: Route, levels: Int): Int {
        if (levels >= GOAL) return 0
        val legs = route.places.lastIndex
        val nextStop = position(route, levels).toInt() + 1
        val need = kotlin.math.ceil(nextStop.toDouble() * GOAL / legs).toInt()
        return (need - levels).coerceAtLeast(1)
    }
}
