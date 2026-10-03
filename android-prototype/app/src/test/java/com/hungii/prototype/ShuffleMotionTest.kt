package com.hungii.prototype

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ShuffleMotionTest {
    @Test fun mixSettlesWithoutASnapOrResidualBlur() {
        for (index in 0..2) {
            val start = shufflePose(0f, index, 110f)
            val end = shufflePose(1f, index, 110f)
            assertEquals(start, end)
            val approaching = shufflePose(.999f, index, 110f)
            assertTrue(abs(approaching.x - end.x) < .01f)
            assertTrue(abs(approaching.y - end.y) < .01f)
            assertTrue(abs(approaching.tilt - end.tilt) < .01f)
            assertEquals(0f, end.blur, 0f)
        }
    }
    @Test fun outerCardsStayInsideTheStageAndActuallyCross() {
        for (index in 0..2) {
            val poses = (0..1000).map { shufflePose(it / 1000f, index, 110f) }
            assertTrue(poses.all { abs(it.x) < 110f && abs(it.y) <= 18f })
            if (index == 0) assertTrue(poses.all { it.x >= 0f })
            if (index == 2) assertTrue(poses.all { it.x <= 0f })
            assertTrue(poses.any { abs(it.x) > 20f })
        }
        // Neighbouring cards exchange screen order during the mix.
        assertTrue((0..1000).any {
            val t = it / 1000f
            shufflePose(t, 0, 110f).x > 110f + shufflePose(t, 1, 110f).x
        })
    }
}
