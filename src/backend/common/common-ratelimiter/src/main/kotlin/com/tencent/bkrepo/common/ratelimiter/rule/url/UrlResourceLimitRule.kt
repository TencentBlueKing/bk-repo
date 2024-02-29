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

package com.tencent.bkrepo.common.ratelimiter.rule.url

import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.utils.UrlUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

class UrlResourceLimitRule(
    private val root: UrlNode = UrlNode("/")
) {

    fun addUrlResourceLimit(resourceLimit: ResourceLimit) {
        if (resourceLimit.limitDimension != LimitDimension.URL
            && resourceLimit.limitDimension != LimitDimension.URL_TEMPLATE) {
            return
        }
        val urlPath = resourceLimit.resource
        if (!urlPath.startsWith("/")) {
            throw InvalidResourceException(urlPath)
        }

        if (urlPath == "/") {
            root.setResourceLimit(resourceLimit)
            return
        }

        val pathDirs = UrlUtils.tokenizeUrlPath(urlPath)
        if (pathDirs.isNullOrEmpty()) {
            logger.warn("config url $urlPath is empty!")
            return
        }

        var p = root
        pathDirs.forEach {
            val children = p.getEdges()
            var pathDirPattern = it
            var isPattern = false
            if (isUrlTemplateVariable(it)) {
                pathDirPattern = getPathDirPatten(it)
                isPattern = true
            }
            val newNode = UrlNode(pathDirPattern, isPattern)
            p = children.putIfAbsent(pathDirPattern, newNode) ?: newNode
        }
        p.setResourceLimit(resourceLimit)
        logger.debug("$urlPath set limit info $resourceLimit")
    }

    fun addUrlResourceLimits(resourceLimits: List<ResourceLimit>) {
        resourceLimits.forEach {
            addUrlResourceLimit(it)
        }
    }

    fun getUrlResourceLimit(urlPath: String): ResourceLimit? {
        if (urlPath.isBlank()) {
            return null
        }
        if (urlPath == "/") {
            return root.getResourceLimit()
        }
        val pathDirs = UrlUtils.tokenizeUrlPath(urlPath)
        if (pathDirs.isNullOrEmpty()) {
            logger.warn("config url $urlPath is empty!")
            return null
        }

        var p = root
        var currentLimit: ResourceLimit? = null
        if (p.getResourceLimit() != null) {
            currentLimit = p.getResourceLimit()
        }
        for(path in pathDirs) {
            val children = p.getEdges()
            var matchedNode= children[path]
            if (matchedNode == null) {
                children.entries.forEach {  entry ->
                    val n = entry.value
                    if (n.isPattern) {
                        if (Pattern.matches(n.pathDir, path)) {
                            matchedNode = n
                        }
                    }
                }
            }

            if (matchedNode != null) {
                p = matchedNode!!
                if (matchedNode!!.getResourceLimit() != null) {
                    currentLimit = matchedNode!!.getResourceLimit()
                }
            } else {
                break
            }
        }
        return currentLimit
    }

    private fun isUrlTemplateVariable(pathDir: String): Boolean {
        return pathDir.startsWith("{") && pathDir.endsWith("}")
    }

    private fun getPathDirPatten(pathDir: String): String {
        val patternBuilder = StringBuilder()
        val colonIdx = pathDir.indexOf(':')
        if (colonIdx == -1) {
            patternBuilder.append(PATH_REGEX)
        } else {
            val variablePattern = pathDir.substring(colonIdx + 1, pathDir.length - 1)
            patternBuilder.append('(')
            patternBuilder.append(variablePattern)
            patternBuilder.append(')')
        }
        return patternBuilder.toString()
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UrlResourceLimitRule::class.java)
        private const val PATH_REGEX = "(^[a-zA-Z0-9\\-\\.\\/_]+\$)"
    }
}
