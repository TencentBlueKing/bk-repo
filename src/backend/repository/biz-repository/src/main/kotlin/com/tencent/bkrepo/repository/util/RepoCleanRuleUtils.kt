package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.pojo.configuration.clean.RepositoryCleanStrategy
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 *
 */

/*
新版规则配置如下所示：
"cleanStrategy":{
    "autoClean":"true",
    "rule":{
        "relation": "AND",
        "rules":[
            {"field" : "projectId","value" : "test","operation" : "EQ"},
            {"field" : "repoName","value" : "rrreert","operation" : "EQ"},
            {
                "rules" : [
                    {
                        "rules": [
                            {
                                "field": "reserveDays",
                                "value": 30,
                                "operation": "LTE"
                            },
                            {
                                "field": "path",
                                "value": "/",
                                "operation": "REGEX"
                            },
                            {
                                "rules": [
                                    {
                                        "field": "name",
                                        "value": "bbbb",
                                        "operation": "MATCH"
                                    }
                                ],
                                "relation": "OR"
                            }
                        ],
                        "relation" : "AND"
                    }
                ],
                "relation" : "OR"
            }
        ]
    }
}

在新版规则配置起作用的配置提取如下：
{
    "rules" : [
        {
            "rules": [
                {
                    "field": "reserveDays",
                    "value": 30,
                    "operation": "LTE"
                },
                {
                    "field": "path",
                    "value": "/",
                    "operation": "REGEX"
                },
                {
                    "rules": [
                        {
                            "field": "name",
                            "value": "bbbb",
                            "operation": "MATCH"
                        }
                    ],
                    "relation": "OR"
                }
            ],
            "relation" : "AND"
        }
    ],
    "relation" : "OR"
}
如上所示，数据类型为
// 第一级
Rule.NestedRule(
    listOf(
        // 第二级
        Rule.NestedRule(
            listOf(
                pathQueryRule,
                reserveDaysQueryRule,
                // 第三级
                Rule.NestedRule(listOf(Rule.QueryRule), OperationType.OR)
            ),OperationType.AND
         )
     )
    OperationType.OR
)
解释如下：
在新版中永远存在 `/` 根目录的规则，所以所有节点必然会有一个二级目录的规则匹配
第一级：
第二级：路径匹配
第三级：文件名，元数据等匹配
 */

object RepoCleanRuleUtils {

    private val logger: Logger = LoggerFactory.getLogger(RepoCleanRuleUtils::class.java)
    private const val daySeconds = 24 * 60 * 60L
    fun flattenRule(cleanStrategy: RepositoryCleanStrategy): Map<String, Rule.NestedRule>? {
        val rule = cleanStrategy.rule ?: return null
        val reverseRule = (rule as Rule.NestedRule).rules.filterIsInstance<Rule.NestedRule>().firstOrNull()
            ?: return null
        val pathRules = reverseRule.rules.filterIsInstance<Rule.NestedRule>()
        val flattenMap = pathRules.associateBy {
            val eachRules = it.rules.filterIsInstance<Rule.QueryRule>()
            val path = (eachRules.firstOrNull { eachRule -> eachRule.field == "path" }!!.value) as String
            val pathRegexStr = path.removeSuffix("/") + "/"
            pathRegexStr
        }
        return flattenMap.toSortedMap(compareByDescending<String> { it.length }.thenBy { it })
    }

    /**
     * 提取仓库清理的规则
     */
    fun extractRule(cleanStrategy: RepositoryCleanStrategy): Rule.NestedRule? {
        val rule = cleanStrategy.rule ?: return null
        return (rule as Rule.NestedRule).rules.filterIsInstance<Rule.NestedRule>().firstOrNull()
    }

    fun replaceRule(cleanStrategy: RepositoryCleanStrategy, rule: Rule.NestedRule): RepositoryCleanStrategy {
        cleanStrategy.rule ?: return cleanStrategy
        (cleanStrategy.rule as Rule.NestedRule).rules.apply {
            removeIf { it is Rule.NestedRule }
            add(rule)
        }
        return cleanStrategy
    }

    fun needReserveWrapper(nodeInfo: NodeInfo, flattenRules: Map<String, Rule.NestedRule>): Boolean {
        return try {
            needReserve(nodeInfo, flattenRules)
        } catch (e: Exception) {
            logger.error("needReserve error: [$nodeInfo]", e)
            true
        }
    }

    private fun needReserve(
        nodeInfo: NodeInfo,
        flattenRules: Map<String, Rule.NestedRule>,
    ): Boolean {
        // 找到离节点最近的规则
        var matchRule: Rule.NestedRule? = null
        for (pathRule in flattenRules) {
            if (nodeInfo.fullPath.matches(Regex(pathRule.key + ".*"))) {
                matchRule = pathRule.value
                break
            }
        }
        // 取最新时间
        if (logger.isDebugEnabled) {
            logger.debug("nodeInfo:[$nodeInfo]")
            logger.debug("matchRule:[${matchRule?.toJsonString()}]")
        }
        val lastModifiedTimeDate = mutableListOf(
            LocalDateTime.parse(nodeInfo.lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME),
            LocalDateTime.parse(nodeInfo.createdDate, DateTimeFormatter.ISO_DATE_TIME)
        ).apply {
            nodeInfo.recentlyUseDate?.let { this.add(LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)) }
        }.maxOf { it }
        val seconds = Duration.between(lastModifiedTimeDate, LocalDateTime.now()).seconds
        logger.debug("lastModifiedTimeDate:[$lastModifiedTimeDate], seconds:[$seconds]")
        // todo matchRule 是否可能为空，考虑如果为空附默认值
        val rules = matchRule!!.rules
            .filterIsInstance<Rule.NestedRule>().first().rules
            .filterIsInstance<Rule.QueryRule>()
        logger.debug("rules: [${rules.toJsonString()}]")
        // reserveDays
        val reverseDays = matchRule.rules.filterIsInstance<Rule.QueryRule>()
            .firstOrNull { it.field == "reserveDays" }?.value as? Int ?: 30
        logger.debug("reverseDays:[$reverseDays]")
        // 有设置规则 尝试匹配规则
        if (!rules.isNullOrEmpty()) {
            rules.forEach { queryRule ->
                if (queryRule.field == "id") {
                    if (logger.isDebugEnabled) {
                        logger.debug("match by id: [${queryRule.value}]")
                    }
                    return true
                }
                if (queryRule.field == "name") {
                    val ruleValue = queryRule.value as String
                    val type = queryRule.operation
                    checkReverseRule(nodeInfo.name.trim(), ruleValue.trim(), type).apply {
                        if (this) {
                            if (logger.isDebugEnabled) {
                                logger.debug("match by name: [${queryRule.value}]")
                            }
                            return true
                        }
                    }
                }
                if (queryRule.field.startsWith("metadata.")) {
                    val key = queryRule.field.removePrefix("metadata.")
                    val ruleValue = if (queryRule.value is String) {
                        queryRule.value as String
                    } else {
                        logger.info("metadata value is not string, skip")
                        return@forEach
                    }
                    val type = queryRule.operation
                    nodeInfo.nodeMetadata?.firstOrNull { it.key == key }?.let { metadata ->
                        logger.debug("metadata: [${metadata.toJsonString()}]")
                        val metadataValue = if (metadata.value is String) {
                            logger.debug("metadata value is string: [${metadata.value}]")
                            metadata.value as String
                        } else {
                            logger.debug("metadataValue is not String: [${metadata.toJsonString()}]")
                            null
                        }
                        logger.debug("metadataValue:[$metadataValue]")
                        metadataValue?.let {
                            checkReverseRule(it.trim(), ruleValue.trim(), type).apply {
                                if (this) {
                                    if (logger.isDebugEnabled) {
                                        logger.debug(
                                            "match by metadata: " +
                                                "[query: ${queryRule.toJsonString()}, " +
                                                "metadata: ${metadata.toJsonString()}]"
                                        )
                                    }
                                    return true
                                }
                            }
                        }
                    }
                }
            }
            logger.info("not match any rule")
        }
        return reverseDays * daySeconds >= seconds
    }

    fun checkReverseRule(nodeValue: String, ruleValue: String, type: OperationType): Boolean {
        logger.info("nodeValue:[$nodeValue], ruleValue:[$ruleValue], type:[$type]")
        return try {
            when (type) {
                OperationType.EQ -> {
                    nodeValue == ruleValue
                }

                OperationType.MATCH -> {
                    nodeValue.contains(ruleValue.removeSuffix("*").removePrefix("*"))
                }

                OperationType.REGEX -> {
                    nodeValue.matches(ruleValue.toRegex())
                }

                else -> false
            }
        } catch (e: Exception) {
            logger.error("checkReverseRule error: [${e.message}]", e)
            false
        }
    }
}
