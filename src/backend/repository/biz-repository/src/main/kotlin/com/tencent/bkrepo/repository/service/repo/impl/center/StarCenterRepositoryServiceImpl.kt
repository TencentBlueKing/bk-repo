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

package com.tencent.bkrepo.repository.service.repo.impl.center

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.CompositeConfiguration
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.StarCenterCondition
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.repository.config.RepositoryProperties
import com.tencent.bkrepo.repository.dao.RepositoryDao
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.service.node.NodeService
import com.tencent.bkrepo.repository.service.repo.ProjectService
import com.tencent.bkrepo.repository.service.repo.ProxyChannelService
import com.tencent.bkrepo.repository.service.repo.StorageCredentialService
import com.tencent.bkrepo.repository.util.RepoEventFactory
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

@Service
@Conditional(StarCenterCondition::class)
class StarCenterRepositoryServiceImpl(
    repositoryDao: RepositoryDao,
    nodeService: NodeService,
    projectService: ProjectService,
    storageCredentialService: StorageCredentialService,
    proxyChannelService: ProxyChannelService,
    private val repositoryProperties: RepositoryProperties,
    messageSupplier: MessageSupplier,
    servicePermissionResource: ServicePermissionResource,
    private val clusterProperties: ClusterProperties
) : CenterRepositoryServiceImpl(
    repositoryDao,
    nodeService,
    projectService,
    storageCredentialService,
    proxyChannelService,
    repositoryProperties,
    messageSupplier,
    servicePermissionResource
) {

    override fun determineStorageKey(request: RepoCreateRequest): String? {
        return repositoryProperties.defaultStorageCredentialsKey
    }

    override fun buildTRepository(
        request: RepoCreateRequest,
        repoConfiguration: RepositoryConfiguration,
        credentialsKey: String?
    ): TRepository {
        val repo = super.buildTRepository(request, repoConfiguration, credentialsKey)
        repo.regions = setOf(SecurityUtils.getRegion() ?: clusterProperties.region.toString())
        return repo
    }

    override fun checkExist(projectId: String, name: String, type: String?): Boolean {
        val exitRepo = repositoryDao.findByNameAndType(projectId, name, type)
        return exitRepo != null && exitRepo.containsSrcRegion()
    }

    override fun createRepo(repoCreateRequest: RepoCreateRequest): RepositoryDetail {
        with(repoCreateRequest) {
            val exitRepo = repositoryDao.findByNameAndType(projectId, name, type.name)
            if (exitRepo != null && exitRepo.containsSrcRegion()) {
                throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_EXISTED, name)
            }

            if (exitRepo == null) {
                return super.createRepo(repoCreateRequest)
            }

            val query = repositoryDao.buildSingleQuery(projectId, name, type.name)
            val regions = exitRepo.regions.orEmpty().toMutableSet()
            if (regions.isEmpty()) {
                regions.add(clusterProperties.region.toString())
            }
            regions.add(SecurityUtils.getRegion() ?: clusterProperties.region.toString())
            val update = Update().addToSet(TRepository::regions.name).each(regions)
            exitRepo.regions = regions
            repositoryDao.updateFirst(query, update)
            return convertToDetail(exitRepo)!!
        }
    }

    override fun deleteRepo(repoDeleteRequest: RepoDeleteRequest) {
        repoDeleteRequest.apply {
            val repository = checkRepository(projectId, name)
            if (repoDeleteRequest.forced) {
                nodeService.deleteByPath(projectId, name, PathUtils.ROOT, operator)
            } else {
                val artifactInfo = DefaultArtifactInfo(projectId, name, PathUtils.ROOT)
                nodeService.countFileNode(artifactInfo).takeIf { it == 0L } ?: throw ErrorCodeException(
                    ArtifactMessageCode.REPOSITORY_CONTAINS_FILE
                )
                nodeService.deleteByPath(projectId, name, PathUtils.ROOT, operator)
            }
            val regions = repository.regions.orEmpty().toMutableSet()
            regions.remove(SecurityUtils.getRegion() ?: clusterProperties.region)
            if (regions.isEmpty()) {
                repositoryDao.deleteById(repository.id)
            } else {
                repository.regions = regions
                repositoryDao.save(repository)
            }

            // 删除关联的库
            if (repository.category == RepositoryCategory.COMPOSITE) {
                val configuration = repository.configuration.readJsonString<CompositeConfiguration>()
                configuration.proxy.channelList.forEach {
                    deleteProxyRepo(repository, it)
                }
            }
        }
        SpringContextUtils.publishEvent(RepoEventFactory.buildDeletedEvent(repoDeleteRequest))
        logger.info("Delete repository [$repoDeleteRequest] success.")
    }

    override fun checkRepository(projectId: String, repoName: String, repoType: String?): TRepository {
        val exitRepo = repositoryDao.findByNameAndType(projectId, repoName, repoType)
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
        if (SecurityUtils.getRegion() == null && exitRepo.isSrcRegion()) {
            return exitRepo
        }

        if (SecurityUtils.getRegion() != null && exitRepo.containsSrcRegion()) {
            return exitRepo
        }

        throw ErrorCodeException(CommonMessageCode.OPERATION_CROSS_REGION_NOT_ALLOWED)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StarCenterRepositoryServiceImpl::class.java)
    }
}
