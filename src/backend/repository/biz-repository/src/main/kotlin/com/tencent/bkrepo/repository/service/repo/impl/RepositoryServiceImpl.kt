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

package com.tencent.bkrepo.repository.service.repo.impl

import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.interceptor.DownloadInterceptorFactory
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.REPOSITORY_NOT_FOUND
import com.tencent.bkrepo.common.artifact.path.PathUtils.ROOT
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.CompositeConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.ProxyChannelSetting
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.ProxyConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.virtual.VirtualConfiguration
import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao.Companion.ID
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.RsaUtils
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.DefaultCondition
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.repository.config.RepositoryProperties
import com.tencent.bkrepo.repository.dao.RepositoryDao
import com.tencent.bkrepo.repository.dao.repository.ProjectMetricsRepository
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.project.RepoRangeQueryRequest
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelCreateRequest
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelDeleteRequest
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelInfo
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoListOption
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.repository.service.node.NodeService
import com.tencent.bkrepo.repository.service.repo.ProjectService
import com.tencent.bkrepo.repository.service.repo.ProxyChannelService
import com.tencent.bkrepo.repository.service.repo.RepositoryService
import com.tencent.bkrepo.repository.service.repo.StorageCredentialService
import com.tencent.bkrepo.repository.util.RepoEventFactory.buildCreatedEvent
import com.tencent.bkrepo.repository.util.RepoEventFactory.buildDeletedEvent
import com.tencent.bkrepo.repository.util.RepoEventFactory.buildUpdatedEvent
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
import java.time.format.DateTimeFormatter

/**
 * 仓库服务实现类
 */
@Service
@Conditional(DefaultCondition::class)
@Suppress("TooManyFunctions")
class RepositoryServiceImpl(
    val repositoryDao: RepositoryDao,
    val nodeService: NodeService,
    private val projectService: ProjectService,
    private val storageCredentialService: StorageCredentialService,
    private val proxyChannelService: ProxyChannelService,
    private val repositoryProperties: RepositoryProperties,
    private val messageSupplier: MessageSupplier,
    private val servicePermissionClient: ServicePermissionClient,
    private val projectMetricsRepository: ProjectMetricsRepository,
) : RepositoryService {

    init {
        Companion.repositoryProperties = repositoryProperties
    }

    override fun getRepoInfo(projectId: String, name: String, type: String?): RepositoryInfo? {
        val tRepository = repositoryDao.findByNameAndType(projectId, name, type)
        return convertToInfo(tRepository)
    }

    override fun getRepoDetail(projectId: String, name: String, type: String?): RepositoryDetail? {
        val tRepository = repositoryDao.findByNameAndType(projectId, name, type)
        val storageCredentials = tRepository?.credentialsKey?.let { storageCredentialService.findByKey(it) }
        return convertToDetail(tRepository, storageCredentials)
    }

    override fun updateStorageCredentialsKey(projectId: String, repoName: String, storageCredentialsKey: String) {
        repositoryDao.findByNameAndType(projectId, repoName, null)?.run {
            oldCredentialsKey = credentialsKey
            credentialsKey = storageCredentialsKey
            repositoryDao.save(this)
        }
    }

    override fun listRepo(projectId: String, name: String?, type: String?, display: Boolean?): List<RepositoryInfo> {
        val query = buildListQuery(projectId, name, type, display)
        return repositoryDao.find(query).map { convertToInfo(it)!! }
    }

    override fun listRepoPage(
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

    override fun listPermissionRepo(
        userId: String,
        projectId: String,
        option: RepoListOption,
    ): List<RepositoryInfo> {
        var names = servicePermissionClient.listPermissionRepo(
            projectId = projectId,
            userId = userId,
            appId = SecurityUtils.getPlatformId(),
        ).data.orEmpty()
        if (!option.name.isNullOrBlank()) {
            names = names.filter { it.startsWith(option.name.orEmpty(), true) }
        }
        val criteria = where(TRepository::projectId).isEqualTo(projectId)
            .and(TRepository::name).inValues(names)
            .and(TRepository::deleted).isEqualTo(null).apply {
                if (option.display == true) {
                    and(TRepository::display).ne(false)
                } else if (option.display != null) {
                    and(TRepository::display).isEqualTo(option.display)
                }
            }
        option.type?.takeIf { it.isNotBlank() }?.apply { criteria.and(TRepository::type).isEqualTo(this.toUpperCase()) }
        option.category?.takeIf { it.isNotBlank() }?.apply {
            criteria.and(TRepository::category).isEqualTo(this.toUpperCase())
        }
        val query = Query(criteria).with(Sort.by(Sort.Direction.DESC, TRepository::createdDate.name))
        return repositoryDao.find(query).map { convertToInfo(it)!! }
    }

    override fun listPermissionRepoPage(
        userId: String,
        projectId: String,
        pageNumber: Int,
        pageSize: Int,
        option: RepoListOption,
    ): Page<RepositoryInfo> {
        val allRepos = listPermissionRepo(userId, projectId, option)
        return Pages.buildPage(allRepos, pageNumber, pageSize)
    }

    override fun rangeQuery(request: RepoRangeQueryRequest): Page<RepositoryInfo?> {
        val limit = request.limit
        val skip = request.offset
        val projectId = request.projectId

        val criteria = if (request.repoNames.isEmpty()) {
            where(TRepository::projectId).isEqualTo(projectId)
        } else {
            where(TRepository::projectId).isEqualTo(projectId).and(TRepository::name).inValues(request.repoNames)
        }
        criteria.and(TRepository::deleted).isEqualTo(null)
        val totalCount = repositoryDao.count(Query(criteria))
        val records = repositoryDao.find(Query(criteria).limit(limit).skip(skip))
            .map { convertToInfo(it) }
        return Page(0, limit, totalCount, records)
    }

    override fun checkExist(projectId: String, name: String, type: String?): Boolean {
        return repositoryDao.findByNameAndType(projectId, name, type) != null
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun createRepo(repoCreateRequest: RepoCreateRequest): RepositoryDetail {
        with(repoCreateRequest) {
            Preconditions.matchPattern(name, REPO_NAME_PATTERN, this::name.name)
            Preconditions.checkArgument((description?.length ?: 0) <= REPO_DESC_MAX_LENGTH, this::description.name)
            Preconditions.checkArgument(checkCategory(category, configuration), this::configuration.name)
            Preconditions.checkArgument(checkInterceptorConfig(configuration), this::configuration.name)
            // 确保项目一定存在
            if (!projectService.checkExist(projectId)) {
                throw ErrorCodeException(ArtifactMessageCode.PROJECT_NOT_FOUND, projectId)
            }
            // 确保同名仓库不存在
            if (checkExist(projectId, name)) {
                throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_EXISTED, name)
            }
            // 解析存储凭证
            val credentialsKey = determineStorageKey(this)
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
                publishEvent(event)
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

    fun buildTRepository(
        request: RepoCreateRequest,
        repoConfiguration: RepositoryConfiguration,
        credentialsKey: String?,
    ): TRepository {
        with(request) {
            return TRepository(
                name = name,
                type = type,
                category = category,
                public = public,
                description = description,
                configuration = repoConfiguration.toJsonString(),
                credentialsKey = credentialsKey,
                projectId = projectId,
                createdBy = operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now(),
                quota = quota,
                used = 0,
                display = display,
            )
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun updateRepo(repoUpdateRequest: RepoUpdateRequest) {
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
        publishEvent(event)
        messageSupplier.delegateToSupplier(
            data = event,
            topic = event.topic,
            key = event.getFullResourceKey(),
        )
        logger.info("Update repository[$repoUpdateRequest] success.")
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun deleteRepo(repoDeleteRequest: RepoDeleteRequest) {
        repoDeleteRequest.apply {
            val repository = checkRepository(projectId, name)
            if (repoDeleteRequest.forced) {
                nodeService.deleteByPath(projectId, name, ROOT, operator)
            } else {
                val artifactInfo = DefaultArtifactInfo(projectId, name, ROOT)
                nodeService.countFileNode(artifactInfo).takeIf { it == 0L } ?: throw ErrorCodeException(
                    ArtifactMessageCode.REPOSITORY_CONTAINS_FILE,
                )
                nodeService.deleteByPath(projectId, name, ROOT, operator)
            }

            // 为避免仓库被删除后节点无法被自动清理的问题，对仓库实行假删除
            if (!repository.id.isNullOrBlank()) {
                repositoryDao.updateFirst(
                    Query(Criteria.where(ID).isEqualTo(repository.id)),
                    Update().set(TRepository::deleted.name, LocalDateTime.now()),
                )
            }

            // 删除关联的库
            if (repository.category == RepositoryCategory.COMPOSITE) {
                val configuration = repository.configuration.readJsonString<CompositeConfiguration>()
                configuration.proxy.channelList.forEach {
                    deleteProxyRepo(repository, it)
                }
            }
        }
        publishEvent(buildDeletedEvent(repoDeleteRequest))
        logger.info("Delete repository [$repoDeleteRequest] success.")
    }

    override fun allRepos(projectId: String?, repoName: String?, repoType: RepositoryType?): List<RepositoryInfo?> {
        val criteria = where(TRepository::deleted).isEqualTo(null)
        projectId?.let { criteria.and(TRepository::projectId.name).`is`(projectId) }
        repoName?.let { criteria.and(TRepository::name.name).`is`(repoName) }
        repoType?.let { criteria.and(TRepository::type.name).`is`(repoType) }
        val result = repositoryDao.find(Query(criteria))
        return result.map { convertToInfo(it) }
    }

    override fun statRepo(projectId: String, repoName: String): NodeSizeInfo {
        val projectMetrics = projectMetricsRepository.findFirstByProjectIdOrderByCreatedDateDesc(projectId)
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
    private fun queryCompositeConfiguration(
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
    fun checkRepository(projectId: String, repoName: String, repoType: String? = null): TRepository {
        return repositoryDao.findByNameAndType(projectId, repoName, repoType)
            ?: throw ErrorCodeException(REPOSITORY_NOT_FOUND, repoName)
    }

    /**
     * 构造list查询条件
     */
    private fun buildListQuery(
        projectId: String,
        repoName: String? = null,
        repoType: String? = null,
        display: Boolean? = null,
    ): Query {
        val criteria = where(TRepository::projectId).isEqualTo(projectId)
        if (display == true) {
            criteria.and(TRepository::display).ne(false)
        }
        criteria.and(TRepository::deleted).isEqualTo(null)
        repoName?.takeIf { it.isNotBlank() }?.apply { criteria.and(TRepository::name).regex("^$this") }
        repoType?.takeIf { it.isNotBlank() }?.apply { criteria.and(TRepository::type).isEqualTo(this.toUpperCase()) }
        return Query(criteria).with(Sort.by(Sort.Direction.DESC, TRepository::createdDate.name))
    }

    /**
     * 构造仓库初始化配置
     */
    private fun buildRepoConfiguration(request: RepoCreateRequest): RepositoryConfiguration {
        return when (request.category) {
            RepositoryCategory.LOCAL -> LocalConfiguration()
            RepositoryCategory.REMOTE -> RemoteConfiguration()
            RepositoryCategory.VIRTUAL -> VirtualConfiguration()
            RepositoryCategory.COMPOSITE -> CompositeConfiguration()
            RepositoryCategory.PROXY -> com.tencent.bkrepo.common.artifact.pojo.configuration.proxy.ProxyConfiguration()
        }
    }

    /**
     * 更新仓库配置
     */
    private fun updateRepoConfiguration(
        new: RepositoryConfiguration,
        old: RepositoryConfiguration,
        repository: TRepository,
        operator: String,
    ) {
        val newType = new::class.simpleName
        val oldType = old::class.simpleName
        if (newType != oldType) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "configuration type")
        }
        if (new is CompositeConfiguration && old is CompositeConfiguration) {
            updateCompositeConfiguration(new, old, repository, operator)
        }
    }

    /**
     * 更新Composite类型仓库配置
     */
    private fun updateCompositeConfiguration(
        new: CompositeConfiguration,
        old: CompositeConfiguration? = null,
        repository: TRepository,
        operator: String,
    ) {
        // 校验
        new.proxy.channelList.forEach {
            Preconditions.checkNotBlank(it.name, "name")
            Preconditions.checkNotBlank(it.url, "url")
        }
        val newProxyProxyRepos = new.proxy.channelList
        val existProxyProxyRepos = old?.proxy?.channelList ?: emptyList()

        val newProxyRepoMap = newProxyProxyRepos.associateBy { it.name }
        val existProxyRepoMap = existProxyProxyRepos.associateBy { it.name }
        Preconditions.checkArgument(newProxyRepoMap.size == newProxyProxyRepos.size, "channelList")

        val toCreateList = mutableListOf<ProxyChannelSetting>()
        val toDeleteList = mutableListOf<ProxyChannelSetting>()
        val toUpdateList = mutableListOf<ProxyChannelSetting>()

        // 查找要添加的代理库
        newProxyRepoMap.forEach { (name, channel) ->
            existProxyRepoMap[name]?.let {
                // 查找要更新的代理库
                if (channel != it) {
                    toUpdateList.add(channel)
                }
            } ?: run { toCreateList.add(channel) }
        }
        // 查找要删除的代理库
        existProxyRepoMap.forEach { (name, channel) ->
            if (!newProxyRepoMap.containsKey(name)) {
                toDeleteList.add(channel)
            }
        }
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
    fun deleteProxyRepo(repository: TRepository, proxy: ProxyChannelSetting) {
        val proxyRepository = ProxyChannelDeleteRequest(
            repoType = repository.type,
            projectId = repository.projectId,
            repoName = repository.name,
            name = proxy.name,
        )
        proxyChannelService.deleteProxy(proxyRepository)
        logger.info(
            "Success to delete private proxy channel [${proxy.name}]" +
                " in repo[${repository.projectId}|${repository.name}]",
        )
    }

    private fun createProxyRepo(repository: TRepository, proxy: ProxyChannelSetting, operator: String) {
        // 创建代理仓库
        val proxyRepository = ProxyChannelCreateRequest(
            repoType = repository.type,
            projectId = repository.projectId,
            repoName = repository.name,
            url = proxy.url,
            name = proxy.name,
            username = proxy.username,
            password = proxy.password,
            public = proxy.public,
            credentialKey = proxy.credentialKey,
        )
        proxyChannelService.createProxy(operator, proxyRepository)
        logger.info("Success to create private proxy repository[$proxyRepository]")
    }

    private fun updateProxyRepo(repository: TRepository, proxy: ProxyChannelSetting, operator: String) {
        // 更新代理仓库
        val proxyRepository = ProxyChannelUpdateRequest(
            repoType = repository.type,
            projectId = repository.projectId,
            repoName = repository.name,
            url = proxy.url,
            name = proxy.name,
            username = proxy.username,
            password = proxy.password,
            public = proxy.public,
            credentialKey = proxy.credentialKey,
        )
        proxyChannelService.updateProxy(operator, proxyRepository)
        logger.info("Success to update private proxy repository[$proxyRepository]")
    }

    override fun listRepoPageByType(type: String, pageNumber: Int, pageSize: Int): Page<RepositoryDetail> {
        val query = Query(TRepository::type.isEqualTo(type))
            .addCriteria(TRepository::deleted.isEqualTo(null))
            .with(Sort.by(TRepository::name.name))
        val count = repositoryDao.count(query)
        val pageQuery = query.with(PageRequest.of(pageNumber, pageSize))
        val data = repositoryDao.find(pageQuery).map {
            val storageCredentials = it.credentialsKey?.let { key -> storageCredentialService.findByKey(key) }
            convertToDetail(it, storageCredentials)!!
        }

        return Page(pageNumber, pageSize, count, data)
    }

    /**
     * 解析存储凭证key
     * 规则：
     * 1. 如果请求指定了storageCredentialsKey，则使用指定的
     * 2. 如果没有指定，则根据仓库名称进行匹配storageCredentialsKey
     * 3. 如果配有匹配到，则根据仓库类型进行匹配storageCredentialsKey
     * 3. 如果以上都没匹配，则使用全局默认storageCredentialsKey
     */
    fun determineStorageKey(request: RepoCreateRequest): String? {
        with(repositoryProperties) {
            return if (!request.storageCredentialsKey.isNullOrBlank()) {
                request.storageCredentialsKey
            } else if (repoStorageMapping.names.containsKey(request.name)) {
                repoStorageMapping.names[request.name]
            } else if (repoStorageMapping.types.containsKey(request.type)) {
                repoStorageMapping.types[request.type]
            } else {
                defaultStorageCredentialsKey
            }
        }
    }

    override fun getArchivableSize(projectId: String, repoName: String?, days: Int, size: Long?): Long {
        val cutoffTime = LocalDateTime.now().minus(Duration.ofDays(days.toLong()))
        val criteria = where(TNode::folder).isEqualTo(false)
            .and(TNode::deleted).isEqualTo(null)
            .and(TNode::sha256).ne(FAKE_SHA256)
            .and(TNode::archived).ne(true)
            .and(TNode::projectId).isEqualTo(projectId)
            .orOperator(
                where(TNode::lastAccessDate).isEqualTo(null),
                where(TNode::lastAccessDate).lt(cutoffTime),
            ).apply {
                repoName?.let { and(TNode::repoName).isEqualTo(it) }
                size?.let { and(TNode::size).gt(it) }
            }
        return nodeService.aggregateComputeSize(criteria)
    }

    /**
     * 检查下载拦截器配置
     *
     */
    private fun checkInterceptorConfig(configuration: RepositoryConfiguration?): Boolean {
        val settings = configuration?.settings
        settings?.let {
            val interceptors = DownloadInterceptorFactory.buildInterceptors(settings)
            interceptors.forEach {
                try {
                    it.parseRule()
                } catch (ignore: UnsupportedOperationException) {
                    return@forEach
                } catch (exception: Exception) {
                    return false
                }
            }
        }

        return true
    }

    /**
     * 检查仓库类型是否一致
     */
    private fun checkCategory(category: RepositoryCategory, configuration: RepositoryConfiguration?): Boolean {
        if (configuration == null) {
            return true
        }
        return when(configuration) {
            is com.tencent.bkrepo.common.artifact.pojo.configuration.proxy.ProxyConfiguration ->
                category == RepositoryCategory.PROXY
            is CompositeConfiguration -> category == RepositoryCategory.COMPOSITE
            is LocalConfiguration -> category == RepositoryCategory.LOCAL
            is RemoteConfiguration -> category == RepositoryCategory.REMOTE
            is VirtualConfiguration -> category == RepositoryCategory.VIRTUAL
            else -> false
        }
    }

    /**
     * 查找是否存在已被逻辑删除的仓库，如果存在且存储凭证相同，则删除旧仓库再插入新数据；如果存在且存储凭证不同，则禁止创建仓库
     */
    private fun checkAndRemoveDeletedRepo(projectId: String, repoName: String, credentialsKey: String?) {
        val query = Query(
            where(TRepository::projectId).isEqualTo(projectId)
                .and(TRepository::name).isEqualTo(repoName)
                .and(TRepository::deleted).ne(null),
        )
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
        private val logger = LoggerFactory.getLogger(RepositoryServiceImpl::class.java)
        private const val REPO_NAME_PATTERN = "[a-zA-Z_][a-zA-Z0-9\\.\\-_]{1,63}"
        private const val REPO_DESC_MAX_LENGTH = 200
        private const val SETTING_CLIENT_URL = "clientUrl"
        private lateinit var repositoryProperties: RepositoryProperties

        fun convertToDetail(
            tRepository: TRepository?,
            storageCredentials: StorageCredentials? = null,
        ): RepositoryDetail? {
            return tRepository?.let {
                handlerConfiguration(it)
                RepositoryDetail(
                    name = it.name,
                    type = it.type,
                    category = it.category,
                    public = it.public,
                    description = it.description,
                    configuration = cryptoConfigurationPwd(it.configuration.readJsonString()),
                    storageCredentials = storageCredentials,
                    projectId = it.projectId,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    quota = it.quota,
                    used = it.used,
                    oldCredentialsKey = it.oldCredentialsKey,
                )
            }
        }

        private fun convertToInfo(tRepository: TRepository?): RepositoryInfo? {
            return tRepository?.let {
                handlerConfiguration(it)
                RepositoryInfo(
                    id = it.id,
                    name = it.name,
                    type = it.type,
                    category = it.category,
                    public = it.public,
                    description = it.description,
                    configuration = cryptoConfigurationPwd(it.configuration.readJsonString()),
                    storageCredentialsKey = it.credentialsKey,
                    projectId = it.projectId,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    quota = it.quota,
                    used = it.used,
                    display = it.display,
                )
            }
        }

        private fun handlerConfiguration(repository: TRepository) {
            with(repository) {
                val config = configuration.readJsonString<RepositoryConfiguration>()
                if (config is com.tencent.bkrepo.common.artifact.pojo.configuration.proxy.ProxyConfiguration &&
                    type == RepositoryType.GIT
                ) {
                    config.url = "${repositoryProperties.gitUrl}/$projectId/$name.git"
                    config.settings[SETTING_CLIENT_URL] = config.url!!
                } else if (config is com.tencent.bkrepo.common.artifact.pojo.configuration.proxy.ProxyConfiguration &&
                    type == RepositoryType.SVN &&
                    repositoryProperties.svnUrl.isNotEmpty()
                ) {
                    config.url = "${repositoryProperties.svnUrl}/$projectId/$name"
                    config.settings[SETTING_CLIENT_URL] = config.url!!
                } else if (config is RemoteConfiguration && type == RepositoryType.LFS) {
                    config.settings[SETTING_CLIENT_URL] = "${repositoryProperties.gitUrl}/lfs/$projectId/$name/"
                }
                configuration = config.toJsonString()
            }
        }

        private fun convertProxyToProxyChannelSetting(proxy: ProxyChannelInfo): ProxyChannelSetting {
            with(proxy) {
                return ProxyChannelSetting(
                    public = public,
                    name = name,
                    url = url,
                    credentialKey = credentialKey,
                    username = username,
                    password = password,
                )
            }
        }

        /**
         * 加/解密密码
         */
        fun cryptoConfigurationPwd(
            repoConfiguration: RepositoryConfiguration,
            decrypt: Boolean = true,
        ): RepositoryConfiguration {
            if (repoConfiguration is CompositeConfiguration) {
                repoConfiguration.proxy.channelList.forEach {
                    it.password?.let { pw ->
                        it.password = crypto(pw, decrypt)
                    }
                }
            }
            if (repoConfiguration is RemoteConfiguration) {
                repoConfiguration.credentials.password?.let {
                    repoConfiguration.credentials.password = crypto(it, decrypt)
                }
            }
            return repoConfiguration
        }

        private fun crypto(pw: String, decrypt: Boolean): String {
            return if (!decrypt) {
                RsaUtils.encrypt(pw)
            } else {
                try {
                    RsaUtils.decrypt(pw)
                } catch (e: Exception) {
                    pw
                }
            }
        }
    }
}
