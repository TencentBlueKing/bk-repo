package com.tencent.bkrepo.common.artifact.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransferSpeedSloTest {

    @Test
    fun `BOUNDARIES_MIB_PER_SEC has expected size and endpoints`() {
        val boundaries = TransferSpeedSlo.BOUNDARIES_MIB_PER_SEC
        assertEquals(151, boundaries.size)
        assertEquals(1.0, boundaries.first())
        assertEquals(296.0, boundaries[59])
        assertEquals(300.0, boundaries[60])
        assertEquals(1200.0, boundaries.last())
    }

    @Test
    fun `BOUNDARIES_MIB_PER_SEC is strictly increasing without duplicates`() {
        val boundaries = TransferSpeedSlo.BOUNDARIES_MIB_PER_SEC
        for (i in 1 until boundaries.size) {
            assertTrue(boundaries[i] > boundaries[i - 1])
        }
    }

    @Test
    fun `BOUNDARIES_BPS converts MiB per sec to bytes per sec`() {
        val mib = TransferSpeedSlo.BOUNDARIES_MIB_PER_SEC
        val bps = TransferSpeedSlo.BOUNDARIES_BPS
        assertEquals(mib.size, bps.size)
        for (i in mib.indices) {
            assertEquals(mib[i] * TransferSpeedSlo.MIB_PER_SEC_TO_BPS, bps[i], 0.001)
        }
    }
}
