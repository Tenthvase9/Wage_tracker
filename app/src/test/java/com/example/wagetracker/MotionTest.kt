package com.example.wagetracker

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {
    @Test fun `lerp clamps t`() {
        assertEquals(10.0, Motion.lerp(10.0, 20.0, -1f), 0.001)
        assertEquals(20.0, Motion.lerp(10.0, 20.0, 2f), 0.001)
    }

    @Test fun `lerp midpoint`() {
        assertEquals(15.0, Motion.lerp(10.0, 20.0, 0.5f), 0.001)
    }
}
