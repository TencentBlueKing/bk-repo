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

package com.tencent.bkrepo.fs.server.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.oauth.AuthorizationGrantType
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.common.api.constant.DEVX_ACCESS_FROM_OFFICE
import com.tencent.bkrepo.common.metadata.client.RAuthClient
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.interceptor.devx.DevXProperties
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import kotlinx.coroutines.reactor.awaitSingle

/**
 * 权限服务
 * */
class PermissionService(
    private val rAuthClient: RAuthClient,
    private val devXProperties: DevXProperties
) {
    suspend fun checkPermission(projectId: String, repoName: String, action: PermissionAction, uid: String): Boolean {
        val checkRequest = CheckPermissionRequest(
            uid = uid,
            resourceType = ResourceType.REPO.toString(),
            action = action.toString(),
            projectId = projectId,
            repoName = repoName
        )

        if (devXProperties.enabled) {
            val headerValue = ReactiveRequestContextHolder.getRequest().headers[devXProperties.srcHeaderName!!]
            if (headerValue?.first() == devXProperties.srcHeaderValues[1]) {
                checkRequest.requestSource = DEVX_ACCESS_FROM_OFFICE
            }
        }

        return rAuthClient.checkPermission(checkRequest).awaitSingle().data ?: false
    }

    suspend fun checkPlatformAccount(accessKey: String, secretKey: String): String {
        val appId = rAuthClient.checkAccountCredential(
            accesskey = accessKey,
            secretkey = secretKey,
            authorizationGrantType = AuthorizationGrantType.PLATFORM
        ).awaitSingle().data
        return appId ?: throw AuthenticationException("AccessKey/SecretKey check failed.")
    }
}
