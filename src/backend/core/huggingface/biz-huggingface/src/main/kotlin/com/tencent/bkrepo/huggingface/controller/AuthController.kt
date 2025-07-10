/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.huggingface.controller

import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.BEARER_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.huggingface.pojo.AccessToken
import com.tencent.bkrepo.huggingface.pojo.Auth
import com.tencent.bkrepo.huggingface.pojo.UserInfo
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/{projectId}/{repoName}/api")
class AuthController(
    private val serviceUserClient: ServiceUserClient,
    private val permissionManager: PermissionManager,
) {

    @GetMapping("/whoami-v2")
    fun whoami(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
    ): UserInfo {
        val userId = SecurityUtils.getUserId()
        val role = getRole(projectId, repoName)
        val token = HeaderUtils.getHeader(HttpHeaders.AUTHORIZATION)!!.removePrefix(BEARER_AUTH_PREFIX)
        val userInfo = serviceUserClient.userInfoByToken(token).data!!
        return UserInfo(
            name = userId,
            auth = Auth(
                accessToken = AccessToken(
                    displayName = userInfo.name,
                    role = role,
                    createdAt = userInfo.createdDate ?: LocalDateTime.now(),
                )
            ),
        )
    }

    fun getRole(projectId: String, repoName: String): String {
        try {
            permissionManager.checkRepoPermission(PermissionAction.WRITE, projectId, repoName)
            return PermissionAction.WRITE.name.lowercase()
        } catch (_: PermissionException) {

        }
        permissionManager.checkRepoPermission(PermissionAction.READ, projectId, repoName)
        return PermissionAction.READ.name.lowercase()
    }
}
