package com.music.musicflame.together

import kotlin.math.roundToInt

/**
 * Reglas puras (sin Firebase ni Android) de la lista de espera de "En compañía".
 * Están aparte para poder probarlas con tests unitarios normales.
 */
object TogetherQueueLogic {

    /** Separación entre elementos al numerar la cola. */
    const val STEP = 1000.0

    /** Si dos posiciones vecinas quedan más juntas que esto, se renumera toda la cola. */
    const val MIN_GAP = 0.01

    /** Máximo de canciones esperando (evita que alguien llene la base de datos). */
    const val MAX_QUEUE = 30

    /** Posición para colocar algo entre [before] y [after] (null = no hay vecino de ese lado). */
    fun positionBetween(before: Double?, after: Double?): Double = when {
        before == null && after == null -> STEP
        before == null -> after!! - STEP
        after == null -> before + STEP
        else -> (before + after) / 2.0
    }

    /** ¿Las posiciones vecinas ya están tan juntas que conviene renumerar la cola entera? */
    fun needsRenumber(before: Double?, after: Double?): Boolean =
        before != null && after != null && (after - before) < MIN_GAP

    /** Posiciones limpias 1000, 2000, 3000… para una cola de [n] elementos. */
    fun renumbered(n: Int): List<Double> = List(n) { (it + 1) * STEP }

    /**
     * Posición nueva de un elemento que se arrastra de [from] a [to]
     * (índices sobre la lista ordenada [positions]).
     */
    fun newPositionForMove(positions: List<Double>, from: Int, to: Int): Double {
        val rest = positions.filterIndexed { i, _ -> i != from }
        return positionBetween(rest.getOrNull(to - 1), rest.getOrNull(to))
    }

    /** Índice de destino al arrastrar una fila [dyPx] píxeles; las filas miden [rowPx] de alto. */
    fun dragTarget(from: Int, dyPx: Float, rowPx: Float, size: Int): Int {
        if (size <= 0 || rowPx <= 0f) return from
        return (from + (dyPx / rowPx).roundToInt()).coerceIn(0, size - 1)
    }

    /** Más de la mitad de los propietarios conectados votó saltar. */
    fun skipPasses(votes: Int, ownersOnline: Int): Boolean =
        ownersOnline > 0 && votes * 2 > ownersOnline

    /**
     * Primer elemento de la cola cuyo autor está conectado (solo él tiene el archivo, así que
     * solo él puede hacerla sonar). Devuelve -1 si no hay ninguno.
     */
    fun firstPlayableIndex(addedBy: List<String>, online: Set<String>, exclude: String? = null): Int =
        addedBy.indexOfFirst { it in online && it != exclude }

    /** Una persona conectada en la sala (para el tope de miembros). */
    data class OnlineEntry(val uid: String, val joinedAt: Long, val isHost: Boolean)

    /**
     * Quiénes sobran si hay más de [max] personas conectadas: se quedan el anfitrión y los que
     * entraron primero; salen los últimos. Todos los teléfonos llegan al mismo resultado.
     */
    fun overflowUids(online: List<OnlineEntry>, max: Int): Set<String> =
        if (online.size <= max) emptySet()
        else online
            .sortedWith(compareBy<OnlineEntry>({ !it.isHost }, { it.joinedAt }, { it.uid }))
            .drop(max).map { it.uid }.toSet()

    /** Propietario conectado más antiguo (para el ascenso automático). Empata por uid. */
    fun oldestOnlineOwner(since: Map<String, Long>, online: Set<String>, exclude: String? = null): String? =
        since.entries
            .filter { it.key in online && it.key != exclude }
            .minWithOrNull(compareBy<Map.Entry<String, Long>>({ it.value }, { it.key }))
            ?.key
}
