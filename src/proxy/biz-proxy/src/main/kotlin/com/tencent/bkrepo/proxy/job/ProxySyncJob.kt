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

package com.tencent.bkrepo.proxy.job

import com.tencent.bkrepo.auth.api.proxy.ProxyProxyClient
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.service.proxy.ProxyEnv
import com.tencent.bkrepo.common.service.proxy.ProxyFeignClientFactory
import com.tencent.bkrepo.proxy.artifact.storage.ProxyStorageService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Proxy同步数据任务
 */
@Component
class ProxySyncJob(
    private val storageService: ProxyStorageService
) {

    private val proxyProxyClient: ProxyProxyClient by lazy {
        ProxyFeignClientFactory.create("auth")
    }

    @Scheduled(initialDelay = 10000, fixedRate = 1800000)
    fun sync() {
        val projectId = ProxyEnv.getProjectId()
        val name = ProxyEnv.getName()
        val proxyInfo = proxyProxyClient.info(projectId, name).data!!
        val (startHour, endHour) = proxyInfo.syncTimeRange.split(StringPool.DASH).map { it.toInt() }
        val currentHour = LocalDateTime.now().hour
        if (currentHour < startHour || currentHour > endHour) {
            return
        }
        storageService.sync(proxyInfo.syncRateLimit, proxyInfo.cacheExpireDays)
    }
}
