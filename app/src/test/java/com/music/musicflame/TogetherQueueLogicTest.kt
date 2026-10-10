package com.music.musicflame

import com.music.musicflame.together.TogetherQueueLogic as Q
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de la lista de espera de "En compañía": orden, arrastre, votos y ascenso. */
class TogetherQueueLogicTest {

    @Test
    fun positionBetweenHandlesEdgesAndMiddle() {
        assertEquals(Q.STEP, Q.positionBetween(null, null), 0.0)
        assertEquals(500.0, Q.positionBetween(null, 1500.0), 0.0)
        assertEquals(2000.0, Q.positionBetween(1000.0, null), 0.0)
        assertEquals(1500.0, Q.positionBetween(1000.0, 2000.0), 0.0)
    }

    @Test
    fun renumberOnlyWhenNeighboursAreTooClose() {
        assertFalse(Q.needsRenumber(1000.0, 2000.0))
        assertTrue(Q.needsRenumber(1000.0, 1000.001))
        assertFalse(Q.needsRenumber(null, 5.0))
        assertEquals(listOf(1000.0, 2000.0, 3000.0), Q.renumbered(3))
    }

    @Test
    fun movingAnItemKeepsTheExpectedOrder() {
        val pos = listOf(1000.0, 2000.0, 3000.0, 4000.0)
        // del último al primero
        assertTrue(Q.newPositionForMove(pos, 3, 0) < 1000.0)
        // del primero al último
        assertTrue(Q.newPositionForMove(pos, 0, 3) > 4000.0)
        // del primero al lugar 2 (queda entre 3000 y 4000)
        val mid = Q.newPositionForMove(pos, 0, 2)
        assertTrue(mid > 3000.0 && mid < 4000.0)
        // sobre sí mismo: queda entre sus vecinos
        val same = Q.newPositionForMove(pos, 1, 1)
        assertTrue(same > 1000.0 && same < 3000.0)
    }

    @Test
    fun dragTargetIsClampedInsideTheList() {
        assertEquals(2, Q.dragTarget(1, 70f, 64f, 5))
        assertEquals(0, Q.dragTarget(1, -500f, 64f, 5))
        assertEquals(4, Q.dragTarget(3, 900f, 64f, 5))
        assertEquals(3, Q.dragTarget(3, 10f, 64f, 5))
        assertEquals(0, Q.dragTarget(0, 100f, 0f, 5))
    }

    @Test
    fun skipNeedsMoreThanHalfOfTheOnlineOwners() {
        assertTrue(Q.skipPasses(1, 1))
        assertFalse(Q.skipPasses(1, 2))
        assertTrue(Q.skipPasses(2, 2))
        assertFalse(Q.skipPasses(1, 3))
        assertTrue(Q.skipPasses(2, 3))
        assertFalse(Q.skipPasses(2, 4))
        assertTrue(Q.skipPasses(3, 4))
        assertFalse(Q.skipPasses(0, 0))
    }

    @Test
    fun nextPlayableSkipsOfflineAuthorsAndTheExcludedOne() {
        val by = listOf("a", "b", "c")
        assertEquals(0, Q.firstPlayableIndex(by, setOf("a", "b", "c")))
        assertEquals(1, Q.firstPlayableIndex(by, setOf("b", "c")))
        assertEquals(1, Q.firstPlayableIndex(by, setOf("a", "b", "c"), exclude = "a"))
        assertEquals(-1, Q.firstPlayableIndex(by, emptySet()))
    }

    @Test
    fun oldestOnlineOwnerIsPromotedAndTiesAreStable() {
        val since = mapOf("host" to 1L, "b" to 5L, "c" to 3L, "d" to 3L)
        assertEquals("c", Q.oldestOnlineOwner(since, setOf("b", "c", "d")))
        assertEquals("b", Q.oldestOnlineOwner(since, setOf("b", "d"), exclude = "d"))
        assertEquals("host", Q.oldestOnlineOwner(since, setOf("host", "b")))
        assertNull(Q.oldestOnlineOwner(since, setOf("host"), exclude = "host"))
        assertNull(Q.oldestOnlineOwner(emptyMap(), emptySet()))
    }
}
