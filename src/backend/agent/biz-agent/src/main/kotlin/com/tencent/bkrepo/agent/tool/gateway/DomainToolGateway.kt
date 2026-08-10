/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
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
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.agent.tool.gateway

import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_PROJECT_ID
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.http.core.HttpAuthProperties
import io.agentscope.core.agent.RuntimeContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 领域工具统一网关：从 [RuntimeContext] 取真实 userId，走原有 IAM 鉴权。
 *
 * 本地客户端工具暂不经过资源 IAM；后续 bk-repo 只读/写工具在此扩展。
 */
@Component
class DomainToolGateway(
    private val permissionClient: ServicePermissionClient,
    private val httpAuthProperties: HttpAuthProperties,
) {

    fun requireResourcePermission(
        runtimeContext: RuntimeContext,
        resourceType: ResourceType,
        action: PermissionAction,
        repoName: String? = null,
        path: String? = null,
    ) {
        if (!httpAuthProperties.enabled) {
            return
        }
        val userId = runtimeContext.userId
            ?: throw PermissionException("Missing authenticated user in RuntimeContext")
        val projectId = runtimeContext.get(RUNTIME_CONTEXT_PROJECT_ID, String::class.java)
            ?: throw PermissionException("Missing authenticated project in RuntimeContext")
        val request = CheckPermissionRequest(
            uid = userId,
            resourceType = resourceType.toString(),
            action = action.toString(),
            projectId = projectId,
            repoName = repoName,
            path = path,
        )
        if (permissionClient.checkPermission(request).data != true) {
            logger.info(
                "domain tool iam denied user[$userId] type[$resourceType] action[$action] " +
                    "project[$projectId] repo[$repoName] path[$path]",
            )
            throw PermissionException("User[$userId] does not have $action permission on $resourceType")
        }
    }

    fun currentUserId(runtimeContext: RuntimeContext): String {
        return runtimeContext.userId
            ?: throw PermissionException("Missing authenticated user in RuntimeContext")
    }

    fun currentProjectId(runtimeContext: RuntimeContext): String {
        return runtimeContext.get(RUNTIME_CONTEXT_PROJECT_ID, String::class.java)
            ?: throw PermissionException("Missing authenticated project in RuntimeContext")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DomainToolGateway::class.java)
    }
}
