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

package com.tencent.bkrepo.common.metrics.push.custom.base

import io.prometheus.client.CollectorRegistry
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class PrometheusDrive(
    private val pushSources: List<PrometheusPushSource>,
    private var errMsg: String? = null
) {
    fun sources(): List<PrometheusPushSource> {
        return pushSources
    }

    fun push(source: PrometheusPushSource, registry: CollectorRegistry): Boolean {
        val bRet = source.push(registry)
        if (!bRet) {
            errMsg = source.errMsg
            logger.error("fail to push data to source [${source.name}], errmsg: $errMsg")
        }
        return bRet
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PrometheusDrive::class.java)
    }
}

class PrometheusPushSource(
    val name: String,
    private val pushDrive: PrometheusPush,
    private val metricIncludes: List<String> = emptyList(),
    val labelIncludes: Map<String, List<String>> = emptyMap(),
) {
    val errMsg: String?
        get() = pushDrive.errMsg

    private val patternCache: MutableMap<String, Regex> = ConcurrentHashMap()

    fun supports(metricName: String): Boolean {
        if (metricIncludes.isEmpty()) return true
        return metricIncludes.any { pattern ->
            when {
                pattern == "*" -> true
                '*' !in pattern -> pattern == metricName
                else -> {
                    patternCache.getOrPut(pattern) { compileGlob(pattern) }.matches(metricName)
                }
            }
        }
    }

    fun push(registry: CollectorRegistry): Boolean {
        return pushDrive.push(registry)
    }

    private fun compileGlob(pattern: String): Regex {
        val regex = pattern.split('*').joinToString(".*") { Regex.escape(it) }
        return Regex("^$regex$")
    }
}
