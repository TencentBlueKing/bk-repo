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

package com.tencent.bkrepo.agent.identity

import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.http.core.HttpAuthProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Agent 入口权限：复用现有项目 READ（project_view）校验，不新增 IAM 动作。
 */
@Service
class AgentAuthorizationService(
    private val permissionClient: ServicePermissionClient,
    private val httpAuthProperties: HttpAuthProperties,
) {

    fun ensureProjectPermission(userId: String, projectId: String) {
        if (!httpAuthProperties.enabled) {
            return
        }
        val request = CheckPermissionRequest(
            uid = userId,
            resourceType = ResourceType.PROJECT.toString(),
            action = PermissionAction.READ.toString(),
            projectId = projectId,
        )
        if (permissionClient.checkPermission(request).data != true) {
            logger.info("agent project permission denied for user[$userId] project[$projectId]")
            throw PermissionException("User[$userId] does not have project read permission in project[$projectId]")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentAuthorizationService::class.java)
    }
}
