package com.music.musicflame.data

import androidx.media3.common.Player

/**
 * Lógica ÚNICA del botón cíclico de modos de reproducción (mini-reproductor,
 * reproductor completo y notificación usan esto, para que nunca se desincronicen).
 *
 * Estados:
 *   0 NORMAL             - en orden, se detiene al final
 *   1 SHUFFLE            - aleatorio, se detiene al final
 *   2 REPEAT_ALL         - repite toda la lista (ic_autorenew)         [solo listas]
 *   3 REPEAT_ONE         - repite una canción (ic_autoplay)
 *   4 SHUFFLE_REPEAT_ALL - mezcla la lista y la repite sin detenerse   [solo listas]
 *                          (ic_autorenew_shuffle)
 *
 * "Solo listas": 2 y 4 únicamente existen cuando lo que suena viene de una lista con
 * identidad propia — una Playlist (incluidas Favoritos y las inteligentes), un Álbum,
 * un Artista o un Género. Si suena desde Canciones, Tu Mix, la IA, la Papelera u otro
 * lado, esos dos modos se saltan en el ciclo (no se muestran) y, si estaban activos al
 * cambiar de contexto, se bajan solos (ver [coerceForContext]).
 */
object PlaybackCycle {
    const val NORMAL = 0
    const val SHUFFLE = 1
    const val REPEAT_ALL = 2
    const val REPEAT_ONE = 3
    const val SHUFFLE_REPEAT_ALL = 4

    private val ORDER_LIST = intArrayOf(NORMAL, SHUFFLE, REPEAT_ALL, SHUFFLE_REPEAT_ALL, REPEAT_ONE)
    private val ORDER_FREE = intArrayOf(NORMAL, SHUFFLE, REPEAT_ONE)

    /** Estado del ciclo según lo que Media3 tiene puesto de verdad. */
    fun stateOf(shuffleEnabled: Boolean, repeatMode: Int): Int = when {
        shuffleEnabled && repeatMode == Player.REPEAT_MODE_ALL -> SHUFFLE_REPEAT_ALL
        shuffleEnabled -> SHUFFLE
        repeatMode == Player.REPEAT_MODE_ALL -> REPEAT_ALL
        repeatMode == Player.REPEAT_MODE_ONE -> REPEAT_ONE
        else -> NORMAL
    }

    fun stateOf(player: Player): Int = stateOf(player.shuffleModeEnabled, player.repeatMode)

    /** Fuera de una lista, "repetir todo" baja a Normal y "mezclar y repetir todo" a Aleatorio. */
    fun coerceForContext(state: Int, listContext: Boolean): Int =
        if (listContext) state else when (state) {
            REPEAT_ALL -> NORMAL
            SHUFFLE_REPEAT_ALL -> SHUFFLE
            else -> state
        }

    /** Siguiente estado del ciclo, saltándose los de solo-listas cuando no aplican. */
    fun next(state: Int, listContext: Boolean): Int {
        val order = if (listContext) ORDER_LIST else ORDER_FREE
        val current = coerceForContext(state, listContext)
        val index = order.indexOf(current).coerceAtLeast(0)
        return order[(index + 1) % order.size]
    }

    fun apply(player: Player, state: Int) {
        when (state) {
            SHUFFLE -> { player.shuffleModeEnabled = true; player.repeatMode = Player.REPEAT_MODE_OFF }
            REPEAT_ALL -> { player.shuffleModeEnabled = false; player.repeatMode = Player.REPEAT_MODE_ALL }
            REPEAT_ONE -> { player.shuffleModeEnabled = false; player.repeatMode = Player.REPEAT_MODE_ONE }
            SHUFFLE_REPEAT_ALL -> { player.shuffleModeEnabled = true; player.repeatMode = Player.REPEAT_MODE_ALL }
            else -> { player.shuffleModeEnabled = false; player.repeatMode = Player.REPEAT_MODE_OFF }
        }
    }

    /** Llamar tras armar una cola nueva: si el modo activo no aplica al contexto, lo baja. */
    fun coerceForContext(player: Player, listContext: Boolean) {
        val current = stateOf(player)
        val coerced = coerceForContext(current, listContext)
        if (coerced != current) apply(player, coerced)
    }
}
