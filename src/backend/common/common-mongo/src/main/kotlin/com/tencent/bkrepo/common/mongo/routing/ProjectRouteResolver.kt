package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.RuleRoutingState
import java.time.Clock

/** 模式二（PROJECT/COLLECTION）per-project 双写/切流判定；热路径唯一来源。 */
object ProjectRouteResolver {

    fun isProjectInDualWrite(
        rule: MongoMultiInstanceProperties.RoutingRule,
        projectId: String,
        clock: Clock = Clock.systemUTC(),
    ): Boolean {
        if (!isProjectTypeRule(rule)) return false
        if (rule.routingState == RuleRoutingState.OFF) return false
        if (projectId !in rule.projectRouting) return false
        val migrating = rule.dualWriteProjects ?: return true
        if (projectId in migrating) return true
        val effectiveAt = rule.projectEffectiveAt[projectId] ?: return true
        return clock.instant().isBefore(effectiveAt)
    }

    fun isProjectOnHeavy(
        rule: MongoMultiInstanceProperties.RoutingRule,
        projectId: String,
        clock: Clock = Clock.systemUTC(),
    ): Boolean {
        if (!isProjectTypeRule(rule)) return false
        if (rule.routingState == RuleRoutingState.OFF) return false
        if (projectId !in rule.projectRouting) return false
        return !isProjectInDualWrite(rule, projectId, clock)
    }

    fun isProjectTypeRule(rule: MongoMultiInstanceProperties.RoutingRule): Boolean =
        rule.routingType == MongoMultiInstanceProperties.RoutingType.PROJECT ||
            rule.routingType == MongoMultiInstanceProperties.RoutingType.COLLECTION
}
