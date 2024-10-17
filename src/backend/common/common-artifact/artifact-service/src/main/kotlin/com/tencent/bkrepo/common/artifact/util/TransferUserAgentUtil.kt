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

package com.tencent.bkrepo.common.artifact.util

import com.tencent.bkrepo.common.metadata.interceptor.DownloadInterceptorFactory
import com.tencent.bkrepo.common.artifact.metrics.TransferUserAgent
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder

object TransferUserAgentUtil {

    fun getUserAgent(
        webPlatformId: String,
        host: String,
        clientAgentList: List<String>,
        builderAgentList: List<String>,
    ): TransferUserAgent {
        val userAgent = HttpContextHolder.getUserAgent()
        val platformId = SecurityUtils.getPlatformId()
        val referer = HttpContextHolder.getReferer()
        return when {
            isClient(userAgent, platformId, referer, webPlatformId, clientAgentList) -> TransferUserAgent.BK_CLIENT
            isBuilder(userAgent, builderAgentList) -> TransferUserAgent.BUILDER
            isWeb(platformId, referer, webPlatformId, host) -> TransferUserAgent.BK_WEB
            else -> TransferUserAgent.OPENAPI
        }
    }

    private fun isClient(
        userAgent: String,
        platformId: String?,
        referer: String?,
        webPlatformId: String,
        clientAgentList: List<String>,
        ): Boolean {
        val android = userAgent.contains(DownloadInterceptorFactory.ANDROID_APP_USER_AGENT)
            || userAgent.contains(DownloadInterceptorFactory.ANDROID_APP_USER_AGENT_NEW)
        val ios = userAgent.contains(DownloadInterceptorFactory.IOS_APP_USER_AGENT)
        val otherClient = clientAgentList.firstOrNull { userAgent.startsWith(it) } != null
        val desktopClient = (platformId == webPlatformId) && referer.isNullOrBlank()
        return android || ios || otherClient || desktopClient
    }

    private fun isBuilder(userAgent: String, builderAgentList: List<String>): Boolean {
        return builderAgentList.firstOrNull { userAgent.startsWith(it) } != null
    }

    private fun isWeb(
        platformId: String?,
        referer: String?,
        webPlatformId: String,
        host: String,
    ): Boolean {
        if (referer.isNullOrBlank()) return false
        return platformId == webPlatformId && referer.contains(host)
    }
}
