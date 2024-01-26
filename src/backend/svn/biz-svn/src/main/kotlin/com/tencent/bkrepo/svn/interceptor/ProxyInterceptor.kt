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

package com.tencent.bkrepo.svn.interceptor

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.configuration.proxy.ProxyConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.service.util.proxy.HttpProxyUtil
import com.tencent.bkrepo.common.service.util.proxy.ProxyCallHandler
import com.tencent.bkrepo.svn.utils.SvnProxyHelper.getRepoId
import org.springframework.web.servlet.HandlerInterceptor
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ProxyInterceptor(private val proxyHandler: ProxyCallHandler) : HandlerInterceptor {
    private val client = HttpClientBuilderFactory.create()
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(15, TimeUnit.MINUTES)
        .build()
    private val httpProxyUtil = HttpProxyUtil(client)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val repositoryId = getRepoId(request) ?: return false
        val repo = ArtifactContextHolder.getRepoDetail(repositoryId)
        // 只有PROXY类型的仓库才进行拦截
        if (repo.category != RepositoryCategory.PROXY) {
            return true
        }

        val configuration = repo.configuration as ProxyConfiguration
        val proxyUrl = configuration.proxy.url
        val prefix = "/${repo.projectId}/${repo.name}"
        httpProxyUtil.proxy(
            HttpContextHolder.getRequest(),
            HttpContextHolder.getResponse(),
            proxyUrl,
            prefix,
            proxyHandler
        )
        return false
    }
}
