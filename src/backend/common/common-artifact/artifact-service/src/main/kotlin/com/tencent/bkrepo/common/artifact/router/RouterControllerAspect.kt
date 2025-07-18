/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.router

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.properties.RouterControllerProperties
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.router.api.RouterControllerClient
import com.tencent.bkrepo.router.pojo.RouterPolicy
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.Scheduled

/**
 * 路由控制切面
 * 负责对GET请求根据相关策略进行转发。
 * 只有GET方法的下载制品构件请求才会转发，策略每3s更新一次。
 * */
@Aspect
@ConditionalOnProperty("router.controller.enabled", havingValue = "true")
open class RouterControllerAspect(
    private val properties: RouterControllerProperties,
    private val routerControllerClient: RouterControllerClient,
) {

    /**
     * 当前服务名
     * */
    @Value("\${spring.application.name}")
    var serviceName: String = DUMMY_SERVICE

    /**
     * 路由策略缓存
     * */
    private var routerPolicyCache: List<RouterPolicy> = emptyList()

    /**
     * 对下载构件请求进行拦截。
     * 只有GET方法的下载请求，和参数ArtifactInfo放在方法首位的方法才会被拦截。
     * */
    @Around("@annotation(com.tencent.bkrepo.common.artifact.router.Router)")
    fun interceptorDownloadArtifactInfoRequest(proceedingJoinPoint: ProceedingJoinPoint): Any? {
        if (!properties.supportServices.contains(serviceName)) {
            return proceedingJoinPoint.proceed()
        }
        val artifactInfo = proceedingJoinPoint.args.first()
        require(artifactInfo is ArtifactInfo)
        val user = SecurityUtils.getUserId()
        val request = HttpContextHolder.getRequest()
        val response = HttpContextHolder.getResponse()
        /*
        * 只重定向get请求，如果项目或者用户没有指定路由策略，则不进行重定向。
        * */
        if (request.method != HttpMethod.GET.name() || !hasPolicy(user, artifactInfo.projectId)) {
            return proceedingJoinPoint.proceed()
        }
        with(artifactInfo) {
            val originUrl = "${request.requestURL}?${request.queryString}"
            val targetUrl = routerControllerClient.getRedirectUrl(
                projectId = projectId,
                repoName = repoName,
                fullPath = getArtifactFullPath(),
                originUrl = originUrl,
                serviceName = serviceName,
            ).data ?: return proceedingJoinPoint.proceed()
            if (logger.isDebugEnabled) {
                logger.debug("Redirect $originUrl --> $targetUrl")
            }
            response.sendRedirect(targetUrl)
            return null
        }
    }

    /**
     * 是否有路由策略
     * @return 有则返回true，否则返回false
     * */
    private fun hasPolicy(user: String, projectId: String): Boolean {
        return routerPolicyCache.any { it.users.contains(user) || it.projectIds.contains(projectId) }
    }

    /**
     * 定时刷新路由策略
     * */
    @Scheduled(fixedDelay = POLICY_REFRESH_PERIOD)
    open fun refreshRouterPolicyCache() {
        routerPolicyCache = routerControllerClient.listRouterPolicies().data.orEmpty()
    }

    companion object {
        private const val DUMMY_SERVICE = "NONE"
        private const val POLICY_REFRESH_PERIOD = 3000L
        private val logger = LoggerFactory.getLogger(RouterControllerAspect::class.java)
    }
}
