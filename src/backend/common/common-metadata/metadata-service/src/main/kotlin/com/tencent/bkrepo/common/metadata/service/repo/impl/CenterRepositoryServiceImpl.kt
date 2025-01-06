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

package com.tencent.bkrepo.common.metadata.service.repo.impl

import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.CompositeConfiguration
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.ProxyChannelService
import com.tencent.bkrepo.common.metadata.service.repo.ResourceClearService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.metadata.util.ClusterUtils
import com.tencent.bkrepo.common.metadata.util.RepoEventFactory
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.convertToDetail
import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.condition.CommitEdgeCenterCondition
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@Conditional(SyncCondition::class, CommitEdgeCenterCondition::class)
class CenterRepositoryServiceImpl(
    repositoryDao: RepositoryDao,
    projectService: ProjectService,
    storageCredentialService: StorageCredentialService,
    proxyChannelService: ProxyChannelService,
    messageSupplier: MessageSupplier,
    servicePermissionClient: ServicePermissionClient,
    private val resourceClearService: ObjectProvider<ResourceClearService>,
    private val clusterProperties: ClusterProperties,
) : RepositoryServiceImpl(
    repositoryDao,
    projectService,
    storageCredentialService,
    proxyChannelService,
    messageSupplier,
    servicePermissionClient,
    resourceClearService
) {
    override fun buildTRepository(
        request: RepoCreateRequest,
        repoConfiguration: RepositoryConfiguration,
        credentialsKey: String?
    ): TRepository {
        val repo = super.buildTRepository(request, repoConfiguration, credentialsKey)
        val selfClusterName = clusterProperties.self.name.toString()
        val srcCluster = SecurityUtils.getClusterName() ?: selfClusterName
        repo.clusterNames = if (request.type == RepositoryType.GENERIC) {
            setOf(srcCluster, selfClusterName)
        } else {
            setOf(srcCluster)
        }
        return repo
    }

    override fun checkExist(projectId: String, name: String, type: String?): Boolean {
        val exitRepo = repositoryDao.findByNameAndType(projectId, name, type)
        return exitRepo != null && ClusterUtils.containsSrcCluster(exitRepo.clusterNames)
    }

    override fun createRepo(repoCreateRequest: RepoCreateRequest): RepositoryDetail {
        with(repoCreateRequest) {
            val exitRepo = repositoryDao.findByNameAndType(projectId, name, type.name)
            if (exitRepo != null && ClusterUtils.containsSrcCluster(exitRepo.clusterNames)) {
                throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_EXISTED, name)
            } else if (exitRepo != null && exitRepo.type != RepositoryType.GENERIC) {
                // 不允许非GENERIC仓库属于多个cluster
                throw ErrorCodeException(CommonMessageCode.OPERATION_CROSS_CLUSTER_NOT_ALLOWED)
            }

            if (exitRepo == null) {
                return super.createRepo(repoCreateRequest)
            }

            val query = RepositoryServiceHelper.buildSingleQuery(projectId, name, type.name)
            val clusterNames = exitRepo.clusterNames.orEmpty().toMutableSet()
            if (clusterNames.isEmpty()) {
                clusterNames.add(clusterProperties.self.name.toString())
            }
            clusterNames.add(SecurityUtils.getClusterName() ?: clusterProperties.self.name.toString())
            val update = Update().addToSet(TRepository::clusterNames.name).each(clusterNames)
            exitRepo.clusterNames = clusterNames
            repositoryDao.updateFirst(query, update)
            return convertToDetail(exitRepo)!!
        }
    }

    override fun deleteRepo(repoDeleteRequest: RepoDeleteRequest) {
        repoDeleteRequest.apply {
            val repository = checkRepository(projectId, name)
            resourceClearService.ifAvailable?.clearRepo(repository, forced, operator)
            val clusterNames = repository.clusterNames.orEmpty().toMutableSet()
            clusterNames.remove(SecurityUtils.getClusterName() ?: clusterProperties.self.name)
            if (clusterNames.isEmpty()) {
                repositoryDao.updateFirst(
                    Query(Criteria.where(AbstractMongoDao.ID).isEqualTo(repository.id!!)),
                    Update().set(TRepository::deleted.name, LocalDateTime.now()),
                )
            } else {
                repository.clusterNames = clusterNames
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
        if (SecurityUtils.getClusterName() == null && ClusterUtils.isUniqueSrcCluster(exitRepo.clusterNames)) {
            return exitRepo
        }

        if (SecurityUtils.getClusterName() != null && ClusterUtils.containsSrcCluster(exitRepo.clusterNames)) {
            return exitRepo
        }

        throw ErrorCodeException(CommonMessageCode.OPERATION_CROSS_CLUSTER_NOT_ALLOWED)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CenterRepositoryServiceImpl::class.java)
    }
}
