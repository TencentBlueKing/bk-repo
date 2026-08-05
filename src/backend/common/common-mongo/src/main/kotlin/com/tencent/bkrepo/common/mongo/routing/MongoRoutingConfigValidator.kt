package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.RuleRoutingState
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils

/** sync / reactive 共用启动校验，避免 reactive-only 部署漏掉 fail-fast。 */
object MongoRoutingConfigValidator {

    private const val MAX_HEAVY_INSTANCES = 10
    private const val NODE_RULE = "node"
    private const val BLOCK_NODE_RULE = "block-node"

    fun validate(properties: MongoMultiInstanceProperties) {
        val heavyCount = properties.rules.values
            .filter {
                it.routingState != RuleRoutingState.OFF &&
                    it.routingType == MongoMultiInstanceProperties.RoutingType.PROJECT
            }
            .sumOf { it.instances.size }
        check(heavyCount <= MAX_HEAVY_INSTANCES) {
            "Heavy instance count $heavyCount exceeds limit $MAX_HEAVY_INSTANCES (§4.1)"
        }
        validateCollectionPrefixExclusivity(properties)
        validateNodeBlockNodeBindingConsistency(properties)
        properties.rules.forEach { (ruleName, rule) ->
            val knownInstances = rule.instances.keys
            rule.projectRouting.forEach { (projectId, instanceName) ->
                check(instanceName in knownInstances) {
                    "Rule '$ruleName': project '$projectId' → instance '$instanceName' " +
                        "not found in instances $knownInstances"
                }
                if (rule.routingType == MongoMultiInstanceProperties.RoutingType.PROJECT &&
                    rule.collectionPrefix.isNotBlank()
                ) {
                    val shardIdx = HashShardingUtils.shardingSequenceFor(
                        projectId,
                        rule.shardingCount,
                    )
                    val collectionName = "${rule.collectionPrefix}$shardIdx"
                    check(collectionName !in rule.shardRouting) {
                        "Rule '$ruleName': project '$projectId' hashes to collection '$collectionName' " +
                            "which is also configured in shard-routing (§13.3 mutual exclusion). " +
                            "Remove shard-routing entry or project-routing for partial migration."
                    }
                }
            }
            rule.shardRouting.forEach { (collectionName, instanceName) ->
                check(instanceName in knownInstances) {
                    "Rule '$ruleName': shard '$collectionName' → instance '$instanceName' " +
                        "not found in instances $knownInstances"
                }
            }
            validateProjectRuleConfig(ruleName, rule)
        }
    }

    private fun validateProjectRuleConfig(ruleName: String, rule: MongoMultiInstanceProperties.RoutingRule) {
        if (!ProjectRouteResolver.isProjectTypeRule(rule)) return
        check(rule.routingState != RuleRoutingState.DUAL_WRITE) {
            "Rule '$ruleName': PROJECT rules must not use routing-state=DUAL_WRITE; " +
                "use dual-write-projects instead"
        }
        if (rule.routingState == RuleRoutingState.OFF) return
        check(rule.dualWriteProjects != null) {
            "Rule '$ruleName': dual-write-projects must be configured when routing-state is ROUTED"
        }
        rule.dualWriteProjects.orEmpty().forEach { projectId ->
            check(projectId in rule.projectRouting) {
                "Rule '$ruleName': dual-write-projects contains '$projectId' " +
                    "but project-routing does not"
            }
        }
        rule.projectEffectiveAt.keys.forEach { projectId ->
            check(projectId in rule.projectRouting) {
                "Rule '$ruleName': project-effective-at contains '$projectId' " +
                    "but project-routing does not"
            }
        }
    }

    private fun validateCollectionPrefixExclusivity(properties: MongoMultiInstanceProperties) {
        val prefixes = properties.rules.entries
            .filter { it.value.collectionPrefix.isNotBlank() }
            .map { it.key to it.value.collectionPrefix }
        for (i in prefixes.indices) {
            for (j in i + 1 until prefixes.size) {
                val (nameA, prefixA) = prefixes[i]
                val (nameB, prefixB) = prefixes[j]
                check(!(prefixA.startsWith(prefixB) || prefixB.startsWith(prefixA))) {
                    "collection-prefix conflict: rule '$nameA' ($prefixA) vs '$nameB' ($prefixB)"
                }
            }
        }
    }

    private fun validateNodeBlockNodeBindingConsistency(properties: MongoMultiInstanceProperties) {
        val nodeRule = properties.rules[NODE_RULE] ?: return
        val blockNodeRule = properties.rules[BLOCK_NODE_RULE] ?: return
        if (nodeRule.routingState == RuleRoutingState.OFF &&
            blockNodeRule.routingState == RuleRoutingState.OFF
        ) {
            return
        }
        nodeRule.projectRouting.forEach { (projectId, nodeInstance) ->
            val blockInstance = blockNodeRule.projectRouting[projectId]
            check(blockInstance == nodeInstance) {
                "G-39: project '$projectId' maps to '$nodeInstance' in rule '$NODE_RULE' " +
                    "but ${blockInstance?.let { "'$it'" } ?: "is missing"} in rule '$BLOCK_NODE_RULE'"
            }
        }
        blockNodeRule.projectRouting.forEach { (projectId, blockInstance) ->
            val nodeInstance = nodeRule.projectRouting[projectId]
            check(nodeInstance == blockInstance) {
                "G-39: project '$projectId' maps to '$blockInstance' in rule '$BLOCK_NODE_RULE' " +
                    "but ${nodeInstance?.let { "'$it'" } ?: "is missing"} in rule '$NODE_RULE'"
            }
        }
        check(nodeRule.dualWriteProjects == blockNodeRule.dualWriteProjects) {
            "G-39: dual-write-projects mismatch between '$NODE_RULE' and '$BLOCK_NODE_RULE'"
        }
        check(nodeRule.projectEffectiveAt == blockNodeRule.projectEffectiveAt) {
            "G-39: project-effective-at mismatch between '$NODE_RULE' and '$BLOCK_NODE_RULE'"
        }
    }
}
