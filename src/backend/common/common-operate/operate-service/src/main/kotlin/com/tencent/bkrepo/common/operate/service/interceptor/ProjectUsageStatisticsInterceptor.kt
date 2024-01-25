/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.operate.service.interceptor

import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.operate.api.ProjectUsageStatisticsService
import com.tencent.bkrepo.common.operate.service.config.ProjectUsageStatisticsProperties
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.springframework.http.HttpMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

open class ProjectUsageStatisticsInterceptor(
    private val properties: ProjectUsageStatisticsProperties,
    private val service: ProjectUsageStatisticsService,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val enabled = properties.enabled && properties.enableReqCountStatistic
        if (!enabled || SecurityUtils.getUserId() == SYSTEM_USER || request.method == HttpMethod.HEAD.name) {
            return true
        }

        getProjectId(request)?.let { service.inc(projectId = it, reqCount = 1) }
        return true
    }

    private fun getProjectId(request: HttpServletRequest): String? {
        val uriAttribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) ?: return null
        require(uriAttribute is Map<*, *>)
        return uriAttribute[PROJECT_ID]?.toString()
    }
}
