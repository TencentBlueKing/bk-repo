/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.auth.controller.service

import com.tencent.bkrepo.auth.api.ServiceProxyClient
import com.tencent.bkrepo.auth.pojo.proxy.ProxyCreateRequest
import com.tencent.bkrepo.auth.pojo.proxy.ProxyInfo
import com.tencent.bkrepo.auth.pojo.proxy.ProxyKey
import com.tencent.bkrepo.auth.pojo.proxy.ProxyListOption
import com.tencent.bkrepo.auth.pojo.proxy.ProxyUpdateRequest
import com.tencent.bkrepo.auth.service.ProxyService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.util.unit.DataSize
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceProxyController(
    private val proxyService: ProxyService
) : ServiceProxyClient {

    override fun getEncryptedKey(projectId: String, name: String): Response<ProxyKey> {
        return ResponseBuilder.success(proxyService.getEncryptedKey(projectId, name))
    }

    override fun listProxyByProject(projectId: String): Response<List<ProxyInfo>> {
        // pageSize 设为较大值用于联邦全量同步，实际单项目 proxy 数量应远小于此上限
        val page = proxyService.page(projectId, ProxyListOption(pageNumber = 0, pageSize = Int.MAX_VALUE))
        return ResponseBuilder.success(page.records)
    }

    override fun createProxyForFederation(proxyInfo: ProxyInfo): Response<Boolean> {
        val existing = runCatching { proxyService.getInfo(proxyInfo.projectId, proxyInfo.name) }.getOrNull()
        if (existing != null) return ResponseBuilder.success(false)
        proxyService.create(
            ProxyCreateRequest(
                projectId = proxyInfo.projectId,
                clusterName = proxyInfo.clusterName,
                displayName = proxyInfo.displayName,
                domain = proxyInfo.domain,
                syncRateLimit = DataSize.ofBytes(proxyInfo.syncRateLimit),
                syncTimeRange = proxyInfo.syncTimeRange,
                cacheExpireDays = proxyInfo.cacheExpireDays
            )
        )
        return ResponseBuilder.success(true)
    }

    override fun updateProxyForFederation(proxyInfo: ProxyInfo): Response<Boolean> {
        proxyService.update(
            ProxyUpdateRequest(
                projectId = proxyInfo.projectId,
                name = proxyInfo.name,
                displayName = proxyInfo.displayName,
                domain = proxyInfo.domain,
                syncRateLimit = DataSize.ofBytes(proxyInfo.syncRateLimit),
                syncTimeRange = proxyInfo.syncTimeRange,
                cacheExpiredDays = proxyInfo.cacheExpireDays
            )
        )
        return ResponseBuilder.success(true)
    }

    override fun deleteProxyForFederation(projectId: String, name: String): Response<Boolean> {
        proxyService.delete(projectId, name)
        return ResponseBuilder.success(true)
    }
}
