/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.ratelimiter.interceptor

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.slf4j.LoggerFactory
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE

class ProjectWhitelistRateLimiterInterceptorAdaptor(
    private val rateLimiterProperties: RateLimiterProperties,
) : RateLimiterInterceptorAdapter() {

    override fun beforeLimitCheck(resource: String, resourceLimit: ResourceLimit) {
        if (!rateLimiterProperties.projectWhiteListEnabled) {
            return
        }

        try {
            val request = (
                RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request ?: return

            // 检查url是否在忽略列表中
            if (rateLimiterProperties.specialUrlsIgnoreProjectWhiteList.contains(
                    request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE)
                )) {
                logger.debug("Request url is in ignore url list, skipping rate limit")
                throw InvalidResourceException("Request url is in ignore url list, skipping rate limit")
            }

            // 提取项目ID并检查项目白名单
            val projectId = extractProjectId(resource)
            if (projectId != null && isInWhiteList(projectId)) {
                logger.debug("Project [$projectId] is in whitelist, skipping rate limit")
                throw InvalidResourceException("Project [$projectId] is in whitelist, skipping rate limit")
            }

            throw OverloadException("Project [$projectId] is not in whitelist!")
        } catch (e: InvalidResourceException) {
            throw e
        } catch (e: OverloadException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to check project whitelist: ${e.message}")
        }
    }

    private fun extractProjectId(resource: String): String? {
        return resource.trim('/')
            .takeIf { it.isNotEmpty() }
            ?.split('/')
            ?.firstOrNull()
    }

    private fun isInWhiteList(projectId: String): Boolean {
        return rateLimiterProperties.projectWhiteList.any { pattern ->
            when {
                isPotentialRegex(pattern) -> {
                    try {
                        val regex = Regex(pattern.replace("*", ".*"))
                        regex.matches(projectId)
                    } catch (e: Exception) {
                        logger.warn("Invalid regex pattern [$pattern] in project whitelist")
                        false
                    }
                }
                else -> pattern == projectId
            }
        }
    }

    private fun isPotentialRegex(pattern: String): Boolean {
        val regexChars = setOf('*', '$', '^', '+', '?', '.', '|', '(', ')', '[', ']', '{', '}')
        return pattern.any { it in regexChars }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectWhitelistRateLimiterInterceptorAdaptor::class.java)
    }
}