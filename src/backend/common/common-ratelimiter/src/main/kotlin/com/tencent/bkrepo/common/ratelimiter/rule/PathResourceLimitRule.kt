/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.ratelimiter.rule

import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.common.PathNode
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.utils.ResourcePathUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * path资源规则类
 * pathLengthCheck : 当为false时，不校验resource根据/分割后的字符个数与查出的队友匹配规则的resource按照/分割后的字符个数是否相等
 * 如果是url 类型的路径， 如果配置resource为/project1/, 则其子目录都遵循该规则, 此时pathLengthCheck设置为false
 * 如果是project/repo类型的路径，如何配置resource为/project1/,则只有当查询资源为/project1/才能够匹配。 此时pathLengthCheck应该设置为 true
 *
 * 规则选取优先级说明：
 * - priority 越高的规则越优先命中。
 * - priority 相同时，路径越深（越具体）的规则越优先，浅层通配规则会被深层精确规则覆盖。
 * - 若需要浅层通配规则作为兜底默认值，请为其配置比精确规则更低的 priority 值。
 */
open class PathResourceLimitRule(
    private val root: PathNode = PathNode("/"),
    private val pathLengthCheck: Boolean = false
) : RateLimitRule {

    override fun isEmpty(): Boolean {
        return root.getEdges().isEmpty() && root.getResourceLimit() == null
    }

    override fun getRateLimitRule(resInfo: ResInfo): ResLimitInfo? {
        val resourceLimit = getPathResourceLimit(resInfo.resource) ?: return null
        return ResLimitInfo(resInfo.resource, resourceLimit)
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        val resourcePath = resourceLimit.resource
        if (!resourcePath.startsWith("/")) {
            throw InvalidResourceException(resourcePath)
        }
        addPathNode(resourcePath, resourceLimit)
    }

    override fun addRateLimitRules(resourceLimit: List<ResourceLimit>) {
        resourceLimit.forEach { addRateLimitRule(it) }
    }

    override fun filterResourceLimit(resourceLimit: ResourceLimit) {
        // 默认不过滤
    }

    open fun ignore(resInfo: ResInfo): Boolean {
        return false
    }

    /**
     * 添加资源对应的规则
     */
    fun addPathResourceLimit(resourceLimit: ResourceLimit, limits: List<String>) {
        if (!limits.contains(resourceLimit.limitDimension)) {
            return
        }
        val resourcePath = resourceLimit.resource
        if (!resourcePath.startsWith("/")) {
            throw InvalidResourceException(resourcePath)
        }
        // TODO 配置/*/blueking/* 能识别/api/node/blueking/generic-local
        addPathNode(resourcePath, resourceLimit)
    }

    private fun addPathResourceLimits(resourceLimits: List<ResourceLimit>, limits: List<String>) {
        resourceLimits.forEach {
            addPathResourceLimit(it, limits)
        }
    }

    /**
     * 根据资源获取对应规则
     */
    open fun getPathResourceLimit(resource: String): ResourceLimit? {
        if (resource.isBlank()) {
            return null
        }
        if (resource == "/") {
            return root.getResourceLimit()
        }
        val pathDirs = ResourcePathUtils.tokenizeResourcePath(resource)
        if (pathDirs.isEmpty()) {
            logger.warn("config resource path $resource is empty!")
            return null
        }
        return findResourceLimit(pathDirs)
    }

    private fun findResourceLimit(pathDirs: List<String>): ResourceLimit? {
        var p = root
        // 收集遍历路径上所有命中的规则，包括根节点
        val matchedLimits = mutableListOf<ResourceLimit>()
        p.getResourceLimit()?.let { matchedLimits.add(it) }

        for (path in pathDirs) {
            val children = p.getEdges()
            var matchedNode = children[path]
            if (matchedNode == null) {
                val child = findInChildren(children, path)
                if (child != null) {
                    matchedNode = child
                }
            }
            if (matchedNode == null) {
                break
            }
            p = matchedNode
            matchedNode.getResourceLimit()?.let { matchedLimits.add(it) }
        }

        // 同优先级时越深（越晚加入列表）的规则越具体，用 >= 使后来者覆盖
        val selectByPriority: (List<ResourceLimit>) -> ResourceLimit? = { candidates ->
            candidates.reduceOrNull { acc, limit ->
                if (limit.priority >= acc.priority) limit else acc
            }
        }

        return if (pathLengthCheck) {
            // pathLengthCheck=true 时仅考虑路径深度与请求完全匹配的规则，再按 priority 选最优
            // 避免高 priority 浅层规则被选中后被 pathLengthCheck 拒绝导致规则完全失效
            selectByPriority(matchedLimits.filter { pathLengthCheck(it, pathDirs.size) })
        } else {
            selectByPriority(matchedLimits)
        }
    }

    private fun pathLengthCheck(currentLimit: ResourceLimit?, pathDirSize: Int): Boolean {
        val length = if (currentLimit?.resource.isNullOrEmpty()) {
            0
        } else {
            ResourcePathUtils.tokenizeResourcePath(currentLimit!!.resource).size
        }
        if (length == pathDirSize) {
            return true
        }
        return false
    }

    /**
     * 将资源路径按照/拆分，存对应每级对应规则
     */
    private fun addPathNode(
        resourcePath: String,
        resourceLimit: ResourceLimit
    ) {
        if (resourcePath == "/") {
            root.setResourceLimit(resourceLimit)
            return
        }

        val pathDirs = ResourcePathUtils.tokenizeResourcePath(resourcePath)
        if (pathDirs.isEmpty()) {
            logger.warn("config resource path $resourcePath is empty!")
            return
        }

        var p = root
        pathDirs.forEach {
            val children = p.getEdges()
            var pathDirPattern = it
            var isPattern = false
            if (isTemplateVariable(it)) {
                pathDirPattern = getPathDirPatten(it)
                isPattern = true
            }
            val newNode = PathNode(pathDirPattern, isPattern)
            p = children.putIfAbsent(pathDirPattern, newNode) ?: newNode
        }
        p.setResourceLimit(resourceLimit)
        logger.debug("$resourcePath set limit info $resourceLimit")
    }

    private fun findInChildren(
        children: ConcurrentHashMap<String, PathNode>,
        path: String,
    ): PathNode? {
        children.entries.forEach { entry ->
            val n = entry.value
            if (n.isPattern) {
                if (Pattern.matches(n.pathDir, path)) {
                    return n
                }
            }
        }
        return null
    }

    /**
     * 判断是否是模板
     */
    private fun isTemplateVariable(pathDir: String): Boolean {
        return pathDir.startsWith("{") && pathDir.endsWith("}") ||
                pathDir == "*" || pathDir == "**" || pathDir.contains('*')
    }

    /**
     * 如果模板自带正则表达式，则格式必须为{(^[a-zA-Z]*$)}, ()内味对应正则表达式
     */
    private fun getPathDirPatten(pathDir: String): String {
        val patternBuilder = StringBuilder()
        val isRegex = pathDir.contains("{(") && pathDir.contains(")}")
        if (isRegex) {
            val variablePattern = pathDir.substring(2, pathDir.length - 2)
            patternBuilder.append('(')
            patternBuilder.append(variablePattern)
            patternBuilder.append(')')
        } else {
            val normalizedPathDir = if (pathDir.contains("*")) {
                pathDir.replace("*", PATH_REGEX)
            } else {
                PATH_REGEX
            }
            patternBuilder.append(normalizedPathDir)
        }
        return patternBuilder.toString()
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(PathResourceLimitRule::class.java)
        private const val PATH_REGEX = ".*"
    }
}
