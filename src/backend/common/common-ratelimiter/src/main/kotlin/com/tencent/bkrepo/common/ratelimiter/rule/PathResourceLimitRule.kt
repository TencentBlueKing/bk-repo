/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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
 */
open class PathResourceLimitRule(
    private val root: PathNode = PathNode("/"),
    private val pathLengthCheck: Boolean = false
) {

    fun isEmpty(): Boolean {
        return root.getEdges().isEmpty() && root.getResourceLimit() == null
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

    fun addPathResourceLimits(resourceLimits: List<ResourceLimit>, limits: List<String>) {
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
        var currentLimit: ResourceLimit? = null
        if (p.getResourceLimit() != null) {
            currentLimit = p.getResourceLimit()
        }
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
            if (matchedNode.getResourceLimit() != null) {
                currentLimit = matchedNode.getResourceLimit()
            }
        }
        if (pathLengthCheck) {
            return if (pathLengthCheck(currentLimit, pathDirs.size)) {
                currentLimit
            } else {
                null
            }
        }
        return currentLimit
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
                pathDir == "*" || pathDir == "**"
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
            patternBuilder.append(PATH_REGEX)
        }
        return patternBuilder.toString()
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(PathResourceLimitRule::class.java)
        private const val PATH_REGEX = "(^[a-zA-Z0-9\\-\\.\\/_]+\$)"
    }
}
