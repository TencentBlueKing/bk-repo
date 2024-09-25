/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.REPOSITORY_NOT_FOUND
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.CompositeConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.ProxyChannelSetting
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.ProxyConfiguration
import com.tencent.bkrepo.common.metadata.client.RAuthClient
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.dao.repo.RRepositoryDao
import com.tencent.bkrepo.common.metadata.listener.RResourcePermissionListener
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.metadata.service.project.RProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RProxyChannelService
import com.tencent.bkrepo.common.metadata.service.repo.RRepositoryService
import com.tencent.bkrepo.common.metadata.service.repo.RStorageCredentialService
import com.tencent.bkrepo.common.metadata.service.repo.ResourceClearService
import com.tencent.bkrepo.common.metadata.util.RepoEventFactory.buildCreatedEvent
import com.tencent.bkrepo.common.metadata.util.RepoEventFactory.buildDeletedEvent
import com.tencent.bkrepo.common.metadata.util.RepoEventFactory.buildUpdatedEvent
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.REPO_DESC_MAX_LENGTH
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.REPO_NAME_PATTERN
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.buildChangeList
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.buildListPermissionRepoQuery
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.buildListQuery
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.buildProxyChannelCreateRequest
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.buildProxyChannelDeleteRequest
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.buildProxyChannelUpdateRequest
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.buildRangeQuery
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.buildRepoConfiguration
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.buildSingleQuery
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.buildTRepository
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.buildTypeQuery
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.checkCategory
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.checkConfigType
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.checkInterceptorConfig
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.convertProxyToProxyChannelSetting
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.convertToDetail
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.convertToInfo
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.cryptoConfigurationPwd
import com.tencent.bkrepo.common.metadata.util.RepositoryServiceHelper.Companion.determineStorageKey
import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao.Companion.ID
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.project.RepoRangeQueryRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoListOption
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 仓库服务实现类
 */
@Service
@Conditional(ReactiveCondition::class)
@Suppress("TooManyFunctions")
class RRepositoryServiceImpl(
    val repositoryDao: RRepositoryDao,
    private val projectService: RProjectService,
    private val storageCredentialService: RStorageCredentialService,
    private val proxyChannelService: RProxyChannelService,
    private val messageSupplier: MessageSupplier,
    private val rAuthClient: RAuthClient,
    private val resourceClearService: ObjectProvider<ResourceClearService>,
    private val resourcePermissionListener: RResourcePermissionListener
) : RRepositoryService {

    override suspend fun getRepoInfo(projectId: String, name: String, type: String?): RepositoryInfo? {
        val tRepository = repositoryDao.findByNameAndType(projectId, name, type)
        return convertToInfo(tRepository)
    }

    override suspend fun getRepoDetail(projectId: String, name: String, type: String?): RepositoryDetail? {
        val tRepository = repositoryDao.findByNameAndType(projectId, name, type)
        val storageCredentials = tRepository?.credentialsKey?.let { storageCredentialService.findByKey(it) }
        return convertToDetail(tRepository, storageCredentials)
    }

    override suspend fun updateStorageCredentialsKey(
        projectId: String,
        repoName: String,
        storageCredentialsKey: String?
    ) {
        val repo = checkRepository(projectId, repoName)
        if (repo.credentialsKey != storageCredentialsKey) {
            repo.oldCredentialsKey = repo.credentialsKey
            repo.credentialsKey = storageCredentialsKey
            repositoryDao.save(repo)
        }
    }

    override suspend fun unsetOldStorageCredentialsKey(projectId: String, repoName: String) {
        repositoryDao.unsetOldCredentialsKey(projectId, repoName)
    }

    override suspend fun listRepo(
        projectId: String,
        name: String?,
        type: String?,
        display: Boolean?
    ): List<RepositoryInfo> {
        val query = buildListQuery(projectId, name, type, display)
        return repositoryDao.find(query).map { convertToInfo(it)!! }
    }

    override suspend fun listRepoPage(
        projectId: String,
        pageNumber: Int,
        pageSize: Int,
        name: String?,
        type: String?,
    ): Page<RepositoryInfo> {
        val query = buildListQuery(projectId, name, type)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val totalRecords = repositoryDao.count(query)
        val records = repositoryDao.find(query.with(pageRequest)).map { convertToInfo(it)!! }
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    override suspend fun listPermissionRepo(
        userId: String,
        projectId: String,
        option: RepoListOption,
    ): List<RepositoryInfo> {
        var names = rAuthClient.listPermissionRepo(
            projectId = projectId,
            userId = userId,
            appId = SecurityUtils.getPlatformId(),
        ).awaitSingle().data.orEmpty()
        if (!option.name.isNullOrBlank()) {
            names = names.filter { it.startsWith(option.name.orEmpty(), true) }
        }
        val query = buildListPermissionRepoQuery(projectId, names, option)
        val originResults = repositoryDao.find(query).map { convertToInfo(it)!! }
        val originNames = originResults.map { it.name }.toSet()
        var includeResults = emptyList<RepositoryInfo>()
        if (names.isNotEmpty() && option.include != null) {
            val inValues = names.intersect(setOf(option.include!!)).minus(originNames)
            val includeCriteria = where(TRepository::projectId).isEqualTo(projectId)
                .and(TRepository::name).inValues(inValues)
            includeResults = repositoryDao.find(Query(includeCriteria)).map { convertToInfo(it)!! }
        }
        return originResults + includeResults
    }

    override suspend fun listPermissionRepoPage(
        userId: String,
        projectId: String,
        pageNumber: Int,
        pageSize: Int,
        option: RepoListOption,
    ): Page<RepositoryInfo> {
        val allRepos = listPermissionRepo(userId, projectId, option)
        return Pages.buildPage(allRepos, pageNumber, pageSize)
    }

    override suspend fun rangeQuery(request: RepoRangeQueryRequest): Page<RepositoryInfo?> {
        val limit = request.limit
        val skip = request.offset
        val query = buildRangeQuery(request)
        val totalCount = repositoryDao.count(query)
        val records = repositoryDao.find(query.limit(limit).skip(skip))
            .map { convertToInfo(it) }
        return Page(0, limit, totalCount, records)
    }

    override suspend fun checkExist(projectId: String, name: String, type: String?): Boolean {
        return repositoryDao.findByNameAndType(projectId, name, type) != null
    }

    @Transactional(rollbackFor = [Throwable::class])
    override suspend fun createRepo(repoCreateRequest: RepoCreateRequest): RepositoryDetail {
        with(repoCreateRequest) {
            Preconditions.matchPattern(name, REPO_NAME_PATTERN, this::name.name)
            Preconditions.checkArgument((description?.length ?: 0) <= REPO_DESC_MAX_LENGTH, this::description.name)
            Preconditions.checkArgument(checkCategory(category, configuration), this::configuration.name)
            Preconditions.checkArgument(checkInterceptorConfig(configuration), this::configuration.name)
            // 确保项目一定存在
            val project = projectService.getProjectInfo(projectId)
                ?: throw ErrorCodeException(ArtifactMessageCode.PROJECT_NOT_FOUND, projectId)
            // 确保同名仓库不存在
            if (checkExist(projectId, name)) {
                throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_EXISTED, name)
            }
            // 解析存储凭证
            val credentialsKey = determineStorageKey(this, project.credentialsKey)
            // 确保存储凭证Key一定存在
            credentialsKey?.takeIf { it.isNotBlank() }?.let {
                storageCredentialService.findByKey(it) ?: throw ErrorCodeException(
                    CommonMessageCode.RESOURCE_NOT_FOUND,
                    it,
                )
            }
            // 初始化仓库配置
            val repoConfiguration = configuration ?: buildRepoConfiguration(this)
            // 创建仓库
            val repository = buildTRepository(this, repoConfiguration, credentialsKey)
            return try {
                if (repoConfiguration is CompositeConfiguration) {
                    val old = queryCompositeConfiguration(projectId, name, type)
                    updateCompositeConfiguration(repoConfiguration, old, repository, operator)
                }
                repository.configuration = cryptoConfigurationPwd(repoConfiguration, false).toJsonString()
                checkAndRemoveDeletedRepo(projectId, name, credentialsKey)
                repositoryDao.insert(repository)
                val event = buildCreatedEvent(repoCreateRequest)
                resourcePermissionListener.handle(event)
                messageSupplier.delegateToSupplier(
                    data = event,
                    topic = event.topic,
                    key = event.getFullResourceKey(),
                )
                logger.info("Create repository [$repoCreateRequest] success.")
                convertToDetail(repository)!!
            } catch (exception: DuplicateKeyException) {
                logger.warn("Insert repository[$projectId/$name] error: [${exception.message}]")
                getRepoDetail(projectId, name, type.name)!!
            }
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override suspend fun updateRepo(repoUpdateRequest: RepoUpdateRequest) {
        repoUpdateRequest.apply {
            Preconditions.checkArgument((description?.length ?: 0) < REPO_DESC_MAX_LENGTH, this::description.name)
            Preconditions.checkArgument(checkInterceptorConfig(configuration), this::configuration.name)
            val repository = checkRepository(projectId, name)
            quota?.let {
                Preconditions.checkArgument(it >= (repository.used ?: 0), this::quota.name)
                repository.quota = it
            }
            val oldConfiguration = repository.configuration.readJsonString<RepositoryConfiguration>()
            repository.public = public ?: repository.public
            repository.description = description ?: repository.description
            repository.lastModifiedBy = operator
            repository.lastModifiedDate = LocalDateTime.now()
            configuration?.let {
                updateRepoConfiguration(it, cryptoConfigurationPwd(oldConfiguration), repository, operator)
                repository.configuration = cryptoConfigurationPwd(it, false).toJsonString()
            }
            repository.display = display
            repositoryDao.save(repository)
        }
        val event = buildUpdatedEvent(repoUpdateRequest)
        resourcePermissionListener.handle(event)
        messageSupplier.delegateToSupplier(
            data = event,
            topic = event.topic,
            key = event.getFullResourceKey(),
        )
        logger.info("Update repository[$repoUpdateRequest] success.")
    }

    @Transactional(rollbackFor = [Throwable::class])
    override suspend fun deleteRepo(repoDeleteRequest: RepoDeleteRequest) {
        repoDeleteRequest.apply {
            val repository = checkRepository(projectId, name)
            resourceClearService.ifAvailable?.clearRepo(repository, forced, operator)
            // 为避免仓库被删除后节点无法被自动清理的问题，对仓库实行假删除
            repositoryDao.updateFirst(
                Query(Criteria.where(ID).isEqualTo(repository.id!!)),
                Update().set(TRepository::deleted.name, LocalDateTime.now())
            )
            // 删除关联的库
            if (repository.category == RepositoryCategory.COMPOSITE) {
                val configuration = repository.configuration.readJsonString<CompositeConfiguration>()
                configuration.proxy.channelList.forEach {
                    deleteProxyRepo(repository, it)
                }
            }
        }
        resourcePermissionListener.handle(buildDeletedEvent(repoDeleteRequest))
        logger.info("Delete repository [$repoDeleteRequest] success.")
    }

    override suspend fun statRepo(projectId: String, repoName: String): NodeSizeInfo {
        val projectMetrics = projectService.getProjectMetricsInfo(projectId)
        val repoMetrics = projectMetrics?.repoMetrics?.firstOrNull { it.repoName == repoName }
        return NodeSizeInfo(
            subNodeCount = repoMetrics?.num ?: 0,
            subNodeWithoutFolderCount = repoMetrics?.num ?: 0,
            size = repoMetrics?.size ?: 0,
        )
    }

    /**
     * 获取仓库下的代理地址信息
     */
    private suspend fun queryCompositeConfiguration(
        projectId: String,
        repoName: String,
        repoType: RepositoryType,
    ): CompositeConfiguration? {
        val proxyList = proxyChannelService.listProxyChannel(projectId, repoName, repoType)
        if (proxyList.isEmpty()) return null
        val proxy = ProxyConfiguration(proxyList.map { convertProxyToProxyChannelSetting(it) })
        return CompositeConfiguration(proxy)
    }

    /**
     * 检查仓库是否存在，不存在则抛异常
     */
    suspend fun checkRepository(projectId: String, repoName: String, repoType: String? = null): TRepository {
        return repositoryDao.findByNameAndType(projectId, repoName, repoType)
            ?: throw ErrorCodeException(REPOSITORY_NOT_FOUND, repoName)
    }

    /**
     * 更新仓库配置
     */
    private suspend fun updateRepoConfiguration(
        new: RepositoryConfiguration,
        old: RepositoryConfiguration,
        repository: TRepository,
        operator: String,
    ) {
        checkConfigType(new, old)
        if (new is CompositeConfiguration && old is CompositeConfiguration) {
            updateCompositeConfiguration(new, old, repository, operator)
        }
    }

    /**
     * 更新Composite类型仓库配置
     */
    private suspend fun updateCompositeConfiguration(
        new: CompositeConfiguration,
        old: CompositeConfiguration? = null,
        repository: TRepository,
        operator: String,
    ) {
        val (toCreateList, toDeleteList, toUpdateList) = buildChangeList(new, old)
        // 创建新的代理库
        toCreateList.forEach {
            try {
                createProxyRepo(repository, it, operator)
            } catch (e: DuplicateKeyException) {
                logger.warn("[${it.name}] exist in project[${repository.projectId}], skip creating proxy repo.")
            }
        }
        // 删除旧的代理库
        toDeleteList.forEach {
            deleteProxyRepo(repository, it)
        }
        // 更新旧的代理库
        toUpdateList.forEach {
            updateProxyRepo(repository, it, operator)
        }
    }

    /**
     * 删除关联的代理仓库
     */
    suspend fun deleteProxyRepo(repository: TRepository, proxy: ProxyChannelSetting) {
        val proxyRepository = buildProxyChannelDeleteRequest(repository, proxy)
        proxyChannelService.deleteProxy(proxyRepository)
        logger.info(
            "Success to delete private proxy channel [${proxy.name}]" +
                " in repo[${repository.projectId}|${repository.name}]",
        )
    }

    private suspend fun createProxyRepo(repository: TRepository, proxy: ProxyChannelSetting, operator: String) {
        // 创建代理仓库
        val proxyRepository = buildProxyChannelCreateRequest(repository, proxy)
        proxyChannelService.createProxy(operator, proxyRepository)
        logger.info("Success to create private proxy repository[$proxyRepository]")
    }

    private suspend fun updateProxyRepo(repository: TRepository, proxy: ProxyChannelSetting, operator: String) {
        // 更新代理仓库
        val proxyRepository = buildProxyChannelUpdateRequest(repository, proxy)
        proxyChannelService.updateProxy(operator, proxyRepository)
        logger.info("Success to update private proxy repository[$proxyRepository]")
    }

    override suspend fun listRepoPageByType(type: String, pageNumber: Int, pageSize: Int): Page<RepositoryDetail> {
        val query = buildTypeQuery(type)
        val count = repositoryDao.count(query)
        val pageQuery = query.with(PageRequest.of(pageNumber, pageSize))
        val data = repositoryDao.find(pageQuery).map {
            val storageCredentials = it.credentialsKey?.let { key -> storageCredentialService.findByKey(key) }
            convertToDetail(it, storageCredentials)!!
        }

        return Page(pageNumber, pageSize, count, data)
    }

    /**
     * 查找是否存在已被逻辑删除的仓库，如果存在且存储凭证相同，则删除旧仓库再插入新数据；如果存在且存储凭证不同，则禁止创建仓库
     */
    private suspend fun checkAndRemoveDeletedRepo(projectId: String, repoName: String, credentialsKey: String?) {
        val query = buildSingleQuery(projectId, repoName)
        repositoryDao.findOne(query)?.let {
            if (credentialsKey == it.credentialsKey) {
                repositoryDao.remove(query)
                logger.info("Retrieved deleted record of Repository[$projectId/$repoName] before creating")
            } else {
                throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_EXISTED, repoName)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RRepositoryServiceImpl::class.java)
    }
}
