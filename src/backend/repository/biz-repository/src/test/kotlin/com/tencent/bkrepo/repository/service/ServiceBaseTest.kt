/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.auth.api.ServiceBkiamV3ResourceClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.api.ServiceRoleClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.permission.ListPathResult
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.project.ProjectCreatedEvent
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.artifact.properties.EnableMultiTenantProperties
import com.tencent.bkrepo.common.artifact.properties.RouterControllerProperties
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.dao.project.ProjectDao
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.listener.ResourcePermissionListener
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.service.repo.ResourceClearService
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper
import com.tencent.bkrepo.common.metadata.util.StorageCredentialHelper
import com.tencent.bkrepo.common.security.http.core.HttpAuthProperties
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.repository.UT_PROJECT_ID
import com.tencent.bkrepo.repository.UT_REPO_DESC
import com.tencent.bkrepo.repository.UT_REPO_DISPLAY
import com.tencent.bkrepo.repository.UT_REPO_NAME
import com.tencent.bkrepo.repository.UT_USER
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.router.api.RouterControllerClient
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.otel.bridge.OtelTracer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean

@Import(
    ClusterProperties::class,
    StorageProperties::class,
    RepositoryProperties::class,
    ProjectDao::class,
    RepositoryDao::class,
    HttpAuthProperties::class,
    SpringContextUtils::class,
    NodeDao::class,
    RouterControllerProperties::class,
    RepositoryProperties::class,
    EnableMultiTenantProperties::class,
)
@ComponentScan(value = ["com.tencent.bkrepo.repository.service", "com.tencent.bkrepo.common.metadata"])
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties", "classpath:center-ut.properties"])
open class ServiceBaseTest {

    @MockitoBean
    lateinit var storageService: StorageService

    @MockitoBean
    lateinit var roleResource: ServiceRoleClient

    @MockitoBean
    lateinit var userResource: ServiceUserClient

    @MockitoBean
    lateinit var servicePermissionClient: ServicePermissionClient

    @MockitoBean
    lateinit var serviceBkiamV3ResourceClient: ServiceBkiamV3ResourceClient

    @MockitoBean
    lateinit var permissionManager: PermissionManager

    @MockitoBean
    lateinit var ciPermissionManager: CIPermissionManager

    @MockitoBean
    lateinit var messageSupplier: MessageSupplier

    @MockitoBean
    lateinit var resourcePermissionListener: ResourcePermissionListener

    @MockitoBean
    lateinit var routerControllerClient: RouterControllerClient

    @Autowired
    lateinit var springContextUtils: SpringContextUtils

    @MockitoBean
    lateinit var archiveClient: ArchiveClient

    @Autowired
    lateinit var storageCredentialHelper: StorageCredentialHelper

    @MockitoBean
    lateinit var resourceClearService: ResourceClearService

    @Autowired
    lateinit var repositoryServiceHelper: RepositoryServiceHelper

    @MockitoBean
    lateinit var operateLogService: OperateLogService

    fun initMock() {
        val tracer = mockk<OtelTracer>()
        mockkObject(SpringContextUtils.Companion)
        every { SpringContextUtils.getBean<Tracer>() } returns tracer
        every { tracer.currentSpan() } returns null

        Mockito.`when`(roleResource.createRepoManage(anyString(), anyString())).then {
            ResponseBuilder.success(UT_USER)
        }

        Mockito.`when`(roleResource.createProjectManage(anyString())).thenReturn(
            ResponseBuilder.success(UT_USER)
        )

        Mockito.`when`(userResource.addUserRole(anyString(), anyString())).thenReturn(
            ResponseBuilder.success()
        )

        whenever(servicePermissionClient.listPermissionProject(anyString())).thenReturn(
            ResponseBuilder.success()
        )

        whenever(servicePermissionClient.checkPermission(any())).thenReturn(
            ResponseBuilder.success()
        )
        whenever(servicePermissionClient.listPermissionRepo(anyString(), anyString(), anyString())).thenReturn(
            ResponseBuilder.success()
        )
        whenever(servicePermissionClient.listPermissionPath(anyString(), anyString(), anyString())).thenReturn(
            ResponseBuilder.success(ListPathResult(status = false, path = emptyMap()))
        )
        whenever(messageSupplier.delegateToSupplier(any<ArtifactEvent>(), anyOrNull(), anyString(), anyOrNull(), any()))
            .then {}
        whenever(resourcePermissionListener.handle(any<ProjectCreatedEvent>())).then {}
    }

    fun initRepoForUnitTest(
        projectService: ProjectService,
        repositoryService: RepositoryService,
        credentialsKey: String? = null
    ) {
        if (!projectService.checkExist(UT_PROJECT_ID)) {
            val projectCreateRequest = ProjectCreateRequest(
                name = UT_PROJECT_ID,
                displayName = UT_REPO_NAME,
                description = UT_REPO_DISPLAY,
                createPermission = true,
                operator = UT_USER
            )
            projectService.createProject(projectCreateRequest)
        }
        if (!repositoryService.checkExist(UT_PROJECT_ID, UT_REPO_NAME)) {
            val repoCreateRequest = RepoCreateRequest(
                projectId = UT_PROJECT_ID,
                name = UT_REPO_NAME,
                type = RepositoryType.GENERIC,
                category = RepositoryCategory.LOCAL,
                public = false,
                description = UT_REPO_DESC,
                configuration = LocalConfiguration(),
                operator = UT_USER,
                storageCredentialsKey = credentialsKey
            )
            repositoryService.createRepo(repoCreateRequest)
        }
    }

    fun createProject(
        projectService: ProjectService,
        projectId: String = UT_PROJECT_ID
    ): ProjectInfo {
        val projectCreateRequest = ProjectCreateRequest(projectId, UT_REPO_NAME, UT_REPO_DISPLAY, true, UT_USER)
        return projectService.createProject(projectCreateRequest)
    }

    fun createRepository(
        repositoryService: RepositoryService,
        repoName: String = UT_REPO_NAME,
        projectId: String = UT_PROJECT_ID,
        credentialsKey: String? = null
    ): RepositoryDetail {
        val repoCreateRequest = RepoCreateRequest(
            projectId = projectId,
            name = repoName,
            type = RepositoryType.GENERIC,
            category = RepositoryCategory.LOCAL,
            public = false,
            description = UT_REPO_DESC,
            configuration = LocalConfiguration(),
            operator = UT_USER,
            storageCredentialsKey = credentialsKey
        )
        return repositoryService.createRepo(repoCreateRequest)
    }
}
