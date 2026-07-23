package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.RuleRoutingState
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * routing-effective-at 延迟生效：Consul 已写 ROUTED，到点前行为仍等同 DUAL_WRITE，避免 Pod 刷新不同步分裂。
 */
object RoutingEffectiveState {

    private val logger = LoggerFactory.getLogger(RoutingEffectiveState::class.java)
    private val missingEffectiveAtLogged = ConcurrentHashMap.newKeySet<String>()

    /** 切流 SOP 默认缓冲（秒）；须留足全集群 Consul 传播时间 */
    const val DEFAULT_ROUTING_EFFECTIVE_DELAY_SECONDS: Long = 45L

    fun effectiveRoutingState(
        rule: MongoMultiInstanceProperties.RoutingRule,
        clock: Clock = Clock.systemUTC(),
        ruleName: String? = null,
    ): RuleRoutingState {
        if (rule.routingState == RuleRoutingState.OFF) {
            return RuleRoutingState.OFF
        }
        if (!isRoutingActivationPending(rule, clock, ruleName)) {
            return rule.routingState
        }
        return when (rule.routingState) {
            RuleRoutingState.ROUTED -> RuleRoutingState.DUAL_WRITE
            else -> rule.routingState
        }
    }

    fun isRoutingActivationPending(
        rule: MongoMultiInstanceProperties.RoutingRule,
        clock: Clock = Clock.systemUTC(),
        ruleName: String? = null,
    ): Boolean {
        if (rule.routingState != RuleRoutingState.ROUTED) return false
        val effectiveAt = rule.routingEffectiveAt
        if (effectiveAt == null) {
            logMissingEffectiveAt(ruleName)
            return true
        }
        return clock.instant().isBefore(effectiveAt)
    }

    fun suggestRoutingEffectiveAt(
        delaySeconds: Long = DEFAULT_ROUTING_EFFECTIVE_DELAY_SECONDS,
        clock: Clock = Clock.systemUTC(),
    ): Instant = clock.instant().plusSeconds(delaySeconds)

    internal fun resetMissingEffectiveAtLoggedForTest() {
        missingEffectiveAtLogged.clear()
    }

    private fun logMissingEffectiveAt(ruleName: String?) {
        val key = ruleName ?: "_unknown_"
        if (missingEffectiveAtLogged.add(key)) {
            logger.error(
                "routing-state=ROUTED but routing-effective-at is missing for rule [{}]; " +
                    "keeping DUAL_WRITE until routing-effective-at is set in Consul",
                key,
            )
        }
    }
}
