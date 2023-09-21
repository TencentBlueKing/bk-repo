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

package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.api.exception.TooManyRequestsException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.storage.core.StorageProperties
import org.springframework.http.HttpMethod
import org.springframework.util.unit.DataSize
import org.springframework.web.servlet.HandlerInterceptor
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 制品上传限速拦截器
 * */
class ArtifactFileReceiveLimiterInterceptor(private val storageProperties: StorageProperties) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (isRegistryUploadRequest(request)) {
            receiveRateLimitCheck()
        }
        return super.preHandle(request, response, handler)
    }


    /**
     * 当仓库配置上传限速小于等于最低限速时则直接将请求断开, 避免占用过多连接
     */
    private fun receiveRateLimitCheck() {
        val rateLimitOfRepo = ArtifactContextHolder.getRateLimitOfRepo()
        if (rateLimitOfRepo.receiveRateLimit != DataSize.ofBytes(-1) &&
            rateLimitOfRepo.receiveRateLimit <= storageProperties.receive.circuitBreakerThreshold) {
            throw TooManyRequestsException(
                "The circuit breaker is activated when too many upload requests are made to the service!"
            )
        }
    }

    /**
     * 判断是否为依赖源的上传请求。
     * */
    private fun isRegistryUploadRequest(request: HttpServletRequest): Boolean {
        val repositoryType = ArtifactContextHolder.getCurrentArtifactConfigurer().getRepositoryType()
        if (request.method == HttpMethod.GET.name ||
            request.method == HttpMethod.HEAD.name ||
            repositoryType == RepositoryType.NONE
        ) {
            return false
        }
        return true
    }
}
