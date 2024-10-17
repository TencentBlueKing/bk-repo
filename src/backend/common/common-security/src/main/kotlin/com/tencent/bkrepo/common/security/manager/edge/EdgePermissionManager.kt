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

package com.tencent.bkrepo.common.security.manager.edge

import com.tencent.bkrepo.auth.api.cluster.ClusterPermissionClient
import com.tencent.bkrepo.auth.api.cluster.ClusterUserClient
import com.tencent.bkrepo.auth.api.ServiceExternalPermissionClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.common.security.http.core.HttpAuthProperties
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient

class EdgePermissionManager(
    projectClient: ProjectClient,
    repositoryClient: RepositoryClient,
    permissionResource: ServicePermissionClient,
    externalPermissionResource: ServiceExternalPermissionClient,
    userResource: ServiceUserClient,
    nodeClient: NodeClient,
    clusterProperties: ClusterProperties,
    httpAuthProperties: HttpAuthProperties
) : PermissionManager(
    projectClient,
    repositoryClient,
    permissionResource,
    externalPermissionResource,
    userResource,
    nodeClient,
    httpAuthProperties
) {

    private val centerPermissionClient: ClusterPermissionClient
        by lazy { FeignClientFactory.create(clusterProperties.center, "auth", clusterProperties.self.name) }
    private val centerUserClient: ClusterUserClient
        by lazy { FeignClientFactory.create(clusterProperties.center, "auth", clusterProperties.self.name) }

    override fun checkPermissionFromAuthService(request: CheckPermissionRequest): Boolean? {
        return centerPermissionClient.checkPermission(request).data
    }

    override fun isAdminUser(userId: String): Boolean {
        return centerUserClient.info(userId).data?.admin == true
    }
}
