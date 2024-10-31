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

package com.tencent.bkrepo.common.metadata.permission

import com.tencent.bkrepo.auth.api.ServiceExternalPermissionClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.api.proxy.ProxyPermissionClient
import com.tencent.bkrepo.auth.api.proxy.ProxyUserClient
import com.tencent.bkrepo.auth.pojo.externalPermission.ExternalPermission
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.http.core.HttpAuthProperties
import com.tencent.bkrepo.common.security.manager.PrincipalManager
import com.tencent.bkrepo.common.service.proxy.ProxyFeignClientFactory
import com.tencent.bkrepo.repository.api.proxy.ProxyRepositoryClient
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

class ProxyPermissionManager(
    projectService: ProjectService,
    repositoryService: RepositoryService,
    permissionResource: ServicePermissionClient,
    externalPermissionResource: ServiceExternalPermissionClient,
    userResource: ServiceUserClient,
    nodeService: NodeService,
    httpAuthProperties: HttpAuthProperties,
    principalManager: PrincipalManager
) : PermissionManager(
    projectService,
    repositoryService,
    permissionResource,
    externalPermissionResource,
    userResource,
    nodeService,
    httpAuthProperties,
    principalManager
) {

    private val proxyPermissionClient: ProxyPermissionClient by lazy { ProxyFeignClientFactory.create("auth") }

    private val proxyUserClient: ProxyUserClient by lazy { ProxyFeignClientFactory.create("auth") }

    private val proxyRepositoryClient: ProxyRepositoryClient by lazy { ProxyFeignClientFactory.create("repository") }

    override fun checkPermissionFromAuthService(request: CheckPermissionRequest): Boolean? {
        return proxyPermissionClient.checkPermission(request).data
    }

    override fun isAdminUser(userId: String): Boolean {
        return proxyUserClient.userInfoById(userId).data?.admin == true
    }

    override fun queryRepositoryInfo(projectId: String, repoName: String): RepositoryInfo {
        return proxyRepositoryClient.getRepoInfo(projectId, repoName).data ?: throw RepoNotFoundException(repoName)
    }

    override fun getExternalPermission(projectId: String, repoName: String?): ExternalPermission? {
        return null
    }
}
