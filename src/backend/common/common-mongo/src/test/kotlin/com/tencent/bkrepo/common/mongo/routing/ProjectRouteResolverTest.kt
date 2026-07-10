package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.RuleRoutingState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ProjectRouteResolverTest {

    private val fixedNow = Instant.parse("2026-07-21T10:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    @Test
    fun `dual write when in dual-write-projects`() {
        val rule = projectRule(dualWriteProjects = setOf("p1"))
        assertTrue(ProjectRouteResolver.isProjectInDualWrite(rule, "p1", clock))
        assertFalse(ProjectRouteResolver.isProjectOnHeavy(rule, "p1", clock))
    }

    @Test
    fun `dual write before project-effective-at`() {
        val rule = projectRule(projectEffectiveAt = mapOf("p1" to fixedNow.plusSeconds(45)))
        assertTrue(ProjectRouteResolver.isProjectInDualWrite(rule, "p1", clock))
        assertFalse(ProjectRouteResolver.isProjectOnHeavy(rule, "p1", clock))
    }

    @Test
    fun `on heavy after project-effective-at`() {
        val rule = projectRule(projectEffectiveAt = mapOf("p1" to fixedNow.minusSeconds(1)))
        assertFalse(ProjectRouteResolver.isProjectInDualWrite(rule, "p1", clock))
        assertTrue(ProjectRouteResolver.isProjectOnHeavy(rule, "p1", clock))
    }

    @Test
    fun `fail-safe dual write when project-effective-at missing`() {
        val rule = projectRule()
        assertTrue(ProjectRouteResolver.isProjectInDualWrite(rule, "p1", clock))
        assertFalse(ProjectRouteResolver.isProjectOnHeavy(rule, "p1", clock))
    }

    @Test
    fun `parallel migration A on heavy while B dual-writes`() {
        val rule = MongoMultiInstanceProperties.RoutingRule(
            routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
            routingState = RuleRoutingState.ROUTED,
            dualWriteProjects = setOf("projectB"),
            projectRouting = mapOf("projectA" to "heavy1", "projectB" to "heavy1"),
            projectEffectiveAt = mapOf("projectA" to fixedNow.minusSeconds(1)),
        )
        assertFalse(ProjectRouteResolver.isProjectInDualWrite(rule, "projectA", clock))
        assertTrue(ProjectRouteResolver.isProjectOnHeavy(rule, "projectA", clock))
        assertTrue(ProjectRouteResolver.isProjectInDualWrite(rule, "projectB", clock))
        assertFalse(ProjectRouteResolver.isProjectOnHeavy(rule, "projectB", clock))
    }

    @Test
    fun `routing-state OFF disables all project routing`() {
        val rule = projectRule(dualWriteProjects = setOf("p1"), routingState = RuleRoutingState.OFF)
        assertFalse(ProjectRouteResolver.isProjectInDualWrite(rule, "p1", clock))
        assertFalse(ProjectRouteResolver.isProjectOnHeavy(rule, "p1", clock))
    }

    private fun projectRule(
        dualWriteProjects: Set<String> = emptySet(),
        projectEffectiveAt: Map<String, Instant> = emptyMap(),
        routingState: RuleRoutingState = RuleRoutingState.ROUTED,
    ) = MongoMultiInstanceProperties.RoutingRule(
        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
        routingState = routingState,
        dualWriteProjects = dualWriteProjects,
        projectRouting = mapOf("p1" to "heavy1"),
        projectEffectiveAt = projectEffectiveAt,
    )
}
