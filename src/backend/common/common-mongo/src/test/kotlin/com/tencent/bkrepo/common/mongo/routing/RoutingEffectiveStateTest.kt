package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.RuleRoutingState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RoutingEffectiveStateTest {

    private val fixedNow = Instant.parse("2026-07-21T10:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    @Test
    fun `ROUTED before effective-at behaves as DUAL_WRITE`() {
        val rule = MongoMultiInstanceProperties.RoutingRule(
            routingState = RuleRoutingState.ROUTED,
            routingEffectiveAt = fixedNow.plusSeconds(45),
        )
        assertTrue(RoutingEffectiveState.isRoutingActivationPending(rule, clock))
        assertEquals(RuleRoutingState.DUAL_WRITE, RoutingEffectiveState.effectiveRoutingState(rule, clock))
    }

    @Test
    fun `ROUTED after effective-at stays ROUTED`() {
        val rule = MongoMultiInstanceProperties.RoutingRule(
            routingState = RuleRoutingState.ROUTED,
            routingEffectiveAt = fixedNow.minusSeconds(1),
        )
        assertFalse(RoutingEffectiveState.isRoutingActivationPending(rule, clock))
        assertEquals(RuleRoutingState.ROUTED, RoutingEffectiveState.effectiveRoutingState(rule, clock))
    }

    @Test
    fun `ROUTED without effective-at keeps DUAL_WRITE`() {
        RoutingEffectiveState.resetMissingEffectiveAtLoggedForTest()
        val rule = MongoMultiInstanceProperties.RoutingRule(routingState = RuleRoutingState.ROUTED)
        assertTrue(RoutingEffectiveState.isRoutingActivationPending(rule, clock, "node"))
        assertEquals(RuleRoutingState.DUAL_WRITE, RoutingEffectiveState.effectiveRoutingState(rule, clock, "node"))
    }
}
