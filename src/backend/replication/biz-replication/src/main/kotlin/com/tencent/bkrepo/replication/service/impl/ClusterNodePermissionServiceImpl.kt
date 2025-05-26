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

package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.request.CheckPermissionRequest
import com.tencent.bkrepo.replication.service.ClusterNodePermissionService
import com.tencent.bkrepo.common.metadata.constant.SYSTEM_USER
import feign.FeignException
import org.springframework.stereotype.Service

@Service
class ClusterNodePermissionServiceImpl : ClusterNodePermissionService {
    override fun checkRepoPermission(
        clusterNodeInfo: ClusterNodeInfo,
        remoteUsername: String?,
        remotePassword: String?,
        remoteProjectId: String?,
        remoteRepoName: String?
    ) {
        with(clusterNodeInfo) {
            if (!shouldCheck(clusterNodeInfo, remoteUsername, remotePassword, remoteProjectId, remoteRepoName)) {
                return
            }

            val cluster = ClusterInfo(
                name = name,
                url = url,
                username = username,
                password = password,
                certificate = certificate,
                appId = appId,
                accessKey = accessKey,
                secretKey = secretKey
            )
            val request = CheckPermissionRequest(
                username = remoteUsername!!,
                password = remotePassword!!,
                projectId = remoteProjectId!!,
                repoName = remoteRepoName!!,
                resourceType = ResourceType.REPO.name,
                action = PermissionAction.WRITE.name,
            )
            val userId = SecurityUtils.getUserId()
            HttpContextHolder.getRequestOrNull()?.setAttribute(USER_KEY, SYSTEM_USER)
            val client = FeignClientFactory.create<ArtifactReplicaClient>(cluster)
            try {
                if (client.checkRepoPermission(request).data != true) {
                    throw PermissionException(
                        "user[$remoteUsername] does not have write permission in [$remoteProjectId/$remoteRepoName]"
                    )
                }
            } catch (e: FeignException.NotFound) {
                throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, "$remoteProjectId/$remoteRepoName")
            } finally {
                HttpContextHolder.getRequestOrNull()?.setAttribute(USER_KEY, userId)
            }
        }
    }

    private fun shouldCheck(
        clusterNodeInfo: ClusterNodeInfo,
        remoteUsername: String?,
        remotePassword: String?,
        remoteProjectId: String?,
        remoteRepoName: String?
    ): Boolean {
        // TODO 临时处理，为了兼容旧接口，允许username与password为null时通过鉴权
        return clusterNodeInfo.type == ClusterNodeType.STANDALONE &&
            !remoteUsername.isNullOrEmpty() &&
            !remotePassword.isNullOrEmpty() &&
            !remoteProjectId.isNullOrEmpty() &&
            !remoteRepoName.isNullOrEmpty()
    }
}
