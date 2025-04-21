package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.CompositeConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.ProxyChannelSetting
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.proxy.ProxyConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.virtual.VirtualConfiguration
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.interceptor.DownloadInterceptorFactory
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.security.util.RsaUtils
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.project.RepoRangeQueryRequest
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelCreateRequest
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelDeleteRequest
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelInfo
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoListOption
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Component
class RepositoryServiceHelper(
    repositoryProperties: RepositoryProperties
) {

    init {
        Companion.repositoryProperties = repositoryProperties
    }

    companion object {
        const val REPO_NAME_PATTERN = "[a-zA-Z_][a-zA-Z0-9\\.\\-_]{1,63}"
        const val REPO_DESC_MAX_LENGTH = 200
        private const val SETTING_CLIENT_URL = "clientUrl"
        private lateinit var repositoryProperties: RepositoryProperties
        private const val CLEAN_UP_STRATEGY = "cleanupStrategy"
        private const val TEN_YEARS = 365 * 10

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

        fun convertToInfo(tRepository: TRepository?): RepositoryInfo? {
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
                    display = it.display
                )
            }
        }

        fun handlerConfiguration(repository: TRepository) {
            with(repository) {
                val config = configuration.readJsonString<RepositoryConfiguration>()
                if (config is ProxyConfiguration &&
                    type == RepositoryType.GIT
                ) {
                    config.url = "${repositoryProperties.gitUrl}/$projectId/$name.git"
                    config.settings[SETTING_CLIENT_URL] = config.url!!
                } else if (config is ProxyConfiguration &&
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

        fun convertProxyToProxyChannelSetting(proxy: ProxyChannelInfo): ProxyChannelSetting {
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

        fun crypto(pw: String, decrypt: Boolean): String {
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

        /**
         * 解析存储凭证key
         * 规则：
         * 1. 如果请求指定了storageCredentialsKey，则使用指定的
         * 2. 如果没有指定，则根据仓库名称进行匹配storageCredentialsKey
         * 3. 如果配有匹配到，则根据仓库类型进行匹配storageCredentialsKey
         * 4. 如果项目配置了默认存储凭据，则使用项目指定的
         * 5. 如果以上都没匹配，则使用全局默认storageCredentialsKey
         */
        fun determineStorageKey(request: RepoCreateRequest, projectCredentialsKey: String? = null): String? {
            with(repositoryProperties) {
                return if (!request.storageCredentialsKey.isNullOrBlank()) {
                    request.storageCredentialsKey
                } else if (repoStorageMapping.names.containsKey(request.name)) {
                    repoStorageMapping.names[request.name]
                } else if (repoStorageMapping.types.containsKey(request.type)) {
                    repoStorageMapping.types[request.type]
                } else if (!projectCredentialsKey.isNullOrEmpty()) {
                    projectCredentialsKey
                } else {
                    defaultStorageCredentialsKey
                }
            }
        }

        /**
         * 构造list查询条件
         */
        fun buildListQuery(
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
            repoType?.takeIf { it.isNotBlank() }?.apply { criteria.and(TRepository::type).isEqualTo(this.uppercase(
                Locale.getDefault()
            )) }
            return Query(criteria).with(Sort.by(Sort.Direction.DESC, TRepository::createdDate.name))
        }

        fun buildTRepository(
            request: RepoCreateRequest,
            repoConfiguration: RepositoryConfiguration,
            credentialsKey: String?
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

        /**
         * 构造仓库初始化配置
         */
        fun buildRepoConfiguration(request: RepoCreateRequest): RepositoryConfiguration {
            return when (request.category) {
                RepositoryCategory.LOCAL -> LocalConfiguration()
                RepositoryCategory.REMOTE -> RemoteConfiguration()
                RepositoryCategory.VIRTUAL -> VirtualConfiguration()
                RepositoryCategory.COMPOSITE -> CompositeConfiguration()
                RepositoryCategory.PROXY -> ProxyConfiguration()
            }
        }

        /**
         * 检查下载拦截器配置
         *
         */
        fun checkInterceptorConfig(configuration: RepositoryConfiguration?): Boolean {
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
        fun checkCategory(category: RepositoryCategory, configuration: RepositoryConfiguration?): Boolean {
            if (configuration == null) {
                return true
            }
            return when (configuration) {
                is ProxyConfiguration -> category == RepositoryCategory.PROXY
                is CompositeConfiguration -> category == RepositoryCategory.COMPOSITE
                is LocalConfiguration -> category == RepositoryCategory.LOCAL
                is RemoteConfiguration -> category == RepositoryCategory.REMOTE
                is VirtualConfiguration -> category == RepositoryCategory.VIRTUAL
                else -> false
            }
        }

        /**
         * 检查清理策略的时间是否正常(保存时间day不可超过10年，不得小于0)
         */
        fun checkCleanStrategy(configuration: RepositoryConfiguration?): Boolean {
            if (configuration == null) {
                return true
            }
            val cleanupStrategyMap = configuration.getSetting<Map<String, Any>>(CLEAN_UP_STRATEGY)
            cleanupStrategyMap?.let {
                val cleanupType = it.get("cleanupType") as? String
                val cleanupValue = it.get("cleanupValue") as? String ?: return true
                val targetValue = cleanupValue.toInt()
                if (cleanupType.equals("retentionDays") && (targetValue > TEN_YEARS || targetValue < 0)) {
                    return false
                }
            }
            return true
        }

        fun buildListPermissionRepoQuery(
            projectId: String,
            names: List<String>,
            option: RepoListOption
        ): Query {
            val criteria = where(TRepository::projectId).isEqualTo(projectId)
                .and(TRepository::name).inValues(names)
                .and(TRepository::deleted).isEqualTo(null).apply {
                    if (option.display == true) {
                        and(TRepository::display).ne(false)
                    } else if (option.display != null) {
                        and(TRepository::display).isEqualTo(option.display)
                    }
                }
            option.type?.takeIf { it.isNotBlank() }?.apply { criteria.and(TRepository::type).isEqualTo(this.uppercase(
                Locale.getDefault()
            )) }
            option.category?.takeIf { it.isNotBlank() }?.apply {
                criteria.and(TRepository::category).isEqualTo(this.uppercase(Locale.getDefault()))
            }
            val query = Query(criteria).with(Sort.by(Sort.Direction.DESC, TRepository::createdDate.name))
            return query
        }

        fun buildRangeQuery(request: RepoRangeQueryRequest): Query {
            val projectId = request.projectId

            val criteria = if (request.repoNames.isEmpty()) {
                where(TRepository::projectId).isEqualTo(projectId)
            } else {
                where(TRepository::projectId).isEqualTo(projectId).and(TRepository::name).inValues(request.repoNames)
            }
            criteria.and(TRepository::deleted).isEqualTo(null)
            val query = Query(criteria)
            return query
        }

        fun checkConfigType(
            new: RepositoryConfiguration,
            old: RepositoryConfiguration
        ) {
            val newType = new::class.simpleName
            val oldType = old::class.simpleName
            if (newType != oldType) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "configuration type")
            }
        }

        fun buildChangeList(
            new: CompositeConfiguration,
            old: CompositeConfiguration?
        ): Triple<MutableList<ProxyChannelSetting>,
            MutableList<ProxyChannelSetting>,
            MutableList<ProxyChannelSetting>> {
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
            return Triple(toCreateList, toDeleteList, toUpdateList)
        }

        fun buildProxyChannelDeleteRequest(
            repository: TRepository,
            proxy: ProxyChannelSetting
        ) = ProxyChannelDeleteRequest(
            repoType = repository.type,
            projectId = repository.projectId,
            repoName = repository.name,
            name = proxy.name,
        )

        fun buildProxyChannelCreateRequest(
            repository: TRepository,
            proxy: ProxyChannelSetting
        ) = ProxyChannelCreateRequest(
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

        fun buildProxyChannelUpdateRequest(
            repository: TRepository,
            proxy: ProxyChannelSetting
        ) = ProxyChannelUpdateRequest(
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

        fun buildTypeQuery(type: String): Query {
            val query = Query(TRepository::type.isEqualTo(type))
                .addCriteria(TRepository::deleted.isEqualTo(null))
                .with(Sort.by(TRepository::name.name))
            return query
        }

        /**
         * 构造单个仓库查询条件
         */
        fun buildSingleQuery(projectId: String, repoName: String, repoType: String? = null): Query {
            return buildQuery(projectId, repoName, repoType, false)
        }

        fun buildDeletedQuery(projectId: String, repoName: String, repoType: String? = null): Query {
            return buildQuery(projectId, repoName, repoType, true)
        }


        fun buildQuery(projectId: String, repoName: String, repoType: String? = null, deleted: Boolean): Query {
            val criteria = where(TRepository::projectId).isEqualTo(projectId)
                .and(TRepository::name).isEqualTo(repoName)
            if (deleted) {
                criteria.and(TRepository::deleted).ne(null)
            } else {
                criteria.and(TRepository::deleted).isEqualTo(null)
            }
            if (repoType != null && repoType.uppercase(Locale.getDefault()) != RepositoryType.NONE.name) {
                criteria.and(TRepository::type).isEqualTo(repoType.uppercase(Locale.getDefault()))
            }
            return Query(criteria)
        }
    }
}
