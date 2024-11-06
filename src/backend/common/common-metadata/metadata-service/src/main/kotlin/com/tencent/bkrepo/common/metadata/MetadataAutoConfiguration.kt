/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata

import com.tencent.bkrepo.auth.api.ServiceExternalPermissionClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.common.api.pojo.ClusterArchitecture
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.artifact.properties.ArtifactEventProperties
import com.tencent.bkrepo.common.artifact.properties.RouterControllerProperties
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.permission.EdgePermissionManager
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.permission.ProxyPermissionManager
import com.tencent.bkrepo.common.metadata.properties.OperateProperties
import com.tencent.bkrepo.common.metadata.properties.ProjectUsageStatisticsProperties
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.http.core.HttpAuthProperties
import com.tencent.bkrepo.common.security.manager.PrincipalManager
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.storage.config.StorageProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnWebApplication
@ComponentScan(basePackages = ["com.tencent.bkrepo.common.metadata"])
@EnableConfigurationProperties(
    StorageProperties::class,
    OperateProperties::class,
    ProjectUsageStatisticsProperties::class,
    RouterControllerProperties::class,
    ArtifactEventProperties::class,
    RepositoryProperties::class,
)
class MetadataAutoConfiguration {

    @Bean
    @Suppress("LongParameterList")
    @Conditional(SyncCondition::class)
    fun permissionManager(
        projectService: ProjectService,
        repositoryService: RepositoryService,
        permissionResource: ServicePermissionClient,
        externalPermissionResource: ServiceExternalPermissionClient,
        userResource: ServiceUserClient,
        nodeService: NodeService,
        clusterProperties: ClusterProperties,
        httpAuthProperties: HttpAuthProperties,
        principalManager: PrincipalManager
    ): PermissionManager {
        return if (clusterProperties.role == ClusterNodeType.EDGE
            && clusterProperties.architecture == ClusterArchitecture.COMMIT_EDGE
            && clusterProperties.commitEdge.auth.center
        ) {
            EdgePermissionManager(
                projectService = projectService,
                repositoryService = repositoryService,
                permissionResource = permissionResource,
                externalPermissionResource = externalPermissionResource,
                userResource = userResource,
                nodeService = nodeService,
                clusterProperties = clusterProperties,
                httpAuthProperties = httpAuthProperties,
                principalManager = principalManager
            )
        } else {
            PermissionManager(
                projectService = projectService,
                repositoryService = repositoryService,
                permissionResource = permissionResource,
                externalPermissionResource = externalPermissionResource,
                userResource = userResource,
                nodeService = nodeService,
                httpAuthProperties = httpAuthProperties,
                principalManager = principalManager
            )
        }
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(SyncCondition::class)
    fun proxyPermissionManager(
        projectService: ProjectService,
        repositoryService: RepositoryService,
        permissionResource: ServicePermissionClient,
        externalPermissionResource: ServiceExternalPermissionClient,
        userResource: ServiceUserClient,
        nodeService: NodeService,
        httpAuthProperties: HttpAuthProperties,
        principalManager: PrincipalManager
    ): ProxyPermissionManager {
        return ProxyPermissionManager(
            projectService = projectService,
            repositoryService = repositoryService,
            permissionResource = permissionResource,
            externalPermissionResource = externalPermissionResource,
            userResource = userResource,
            nodeService = nodeService,
            httpAuthProperties = httpAuthProperties,
            principalManager = principalManager
        )
    }
}
