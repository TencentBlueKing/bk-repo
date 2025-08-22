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

package com.tencent.bkrepo.common.artifact.audit

import com.tencent.bk.audit.AuditRequestProvider
import com.tencent.bk.audit.constants.AccessTypeEnum
import com.tencent.bk.audit.constants.UserIdentifyTypeEnum
import com.tencent.bk.audit.exception.AuditException
import com.tencent.bk.audit.model.AuditHttpRequest
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetricsProperties
import com.tencent.bkrepo.common.artifact.metrics.TransferUserAgent
import com.tencent.bkrepo.common.artifact.util.TransferUserAgentUtil
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

class BkAuditRequestProvider(
    private val artifactMetricsProperties: ArtifactMetricsProperties,
) : AuditRequestProvider {

    override fun getRequest(): AuditHttpRequest {
        val httpServletRequest = HttpContextHolder.getRequest()
        return AuditHttpRequest(httpServletRequest)
    }

    private fun getHttpServletRequest(): HttpServletRequest {
        val requestAttributes = RequestContextHolder.getRequestAttributes()
        if (requestAttributes == null) {
            logger.error("Could not get RequestAttributes from RequestContext!")
            throw AuditException("Parse http request error")
        }
        return (requestAttributes as ServletRequestAttributes).request
    }

    override fun getUsername(): String {
        return SecurityUtils.getUserId()
    }

    override fun getUserIdentifyType(): UserIdentifyTypeEnum {
        return UserIdentifyTypeEnum.PERSONAL
    }

    override fun getUserIdentifyTenantId(): String? {
        val httpServletRequest = getHttpServletRequest()
        return httpServletRequest.getHeader(HEADER_USER_IDENTIFY_TENANT_ID)
    }

    override fun getAccessType(): AccessTypeEnum {
        val agent = TransferUserAgentUtil.getUserAgent(
            webPlatformId = artifactMetricsProperties.webPlatformId,
            host = artifactMetricsProperties.host,
            builderAgentList = artifactMetricsProperties.builderAgentList,
            clientAgentList = artifactMetricsProperties.clientAgentList
        )
        return when (agent) {
            TransferUserAgent.BK_WEB -> AccessTypeEnum.WEB
            TransferUserAgent.BUILDER,
            TransferUserAgent.BK_CLIENT -> AccessTypeEnum.CONSOLE

            TransferUserAgent.OPENAPI -> AccessTypeEnum.API
            else -> AccessTypeEnum.OTHER
        }
    }

    override fun getRequestId(): String? {
        return SpringContextUtils.getTraceId()
    }

    override fun getClientIp(): String {
        return HttpContextHolder.getClientAddress()
    }

    override fun getUserAgent(): String? {
        return HttpContextHolder.getUserAgent()
    }

    companion object {
        private const val HEADER_USER_IDENTIFY_TENANT_ID = "X-User-Identify-Tenant-Id"
        private val logger = LoggerFactory.getLogger(BkAuditRequestProvider::class.java)
    }
}
