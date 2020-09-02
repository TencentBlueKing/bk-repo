package com.tencent.bkrepo.common.artifact.repository.composite

import com.tencent.bkrepo.common.artifact.constant.PRIVATE_PROXY_REPO_NAME
import com.tencent.bkrepo.common.artifact.constant.PUBLIC_PROXY_PROJECT
import com.tencent.bkrepo.common.artifact.constant.PUBLIC_PROXY_REPO_NAME
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.ProxyChannelSetting
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteProxyConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactMigrateContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.repository.api.ProxyChannelClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 组合仓库抽象逻辑
 */
@Component
class CompositeRepository(
    private val localRepository: LocalRepository,
    private val remoteRepository: RemoteRepository,
    private val repositoryClient: RepositoryClient,
    private val proxyChannelClient: ProxyChannelClient
) : AbstractArtifactRepository() {

    override fun onUpload(context: ArtifactUploadContext) {
        localRepository.onUpload(context)
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return localRepository.onDownload(context) ?: run {
            mapFirstProxyRepo(context) {
                remoteRepository.onDownload(it as ArtifactDownloadContext)
            }
        }
    }

    override fun remove(context: ArtifactRemoveContext) {
        return localRepository.remove(context)
    }

    override fun <E> search(context: ArtifactSearchContext): List<E> {
        val localResult = localRepository.search<E>(context)
        return mapEachProxyRepo(context) {
            remoteRepository.search<E>(it as ArtifactSearchContext)
        }.apply { add(localResult) }.flatten()
    }

    override fun list(context: ArtifactListContext): List<Any> {
        val localResult = localRepository.list(context)
        return mapEachProxyRepo(context) {
            remoteRepository.list(it as ArtifactListContext)
        }.apply { add(localResult) }.flatten()
    }

    override fun <R> migrate(context: ArtifactMigrateContext): R? {
        return localRepository.migrate<R>(context)
    }

    /**
     * 遍历代理仓库列表，执行[action]操作，当遇到代理仓库[action]操作返回非`null`时，立即返回结果[R]
     */
    private fun <R> mapFirstProxyRepo(context: ArtifactContext, action: (ArtifactContext) -> R?): R? {
        val proxyChannelList = getProxyChannelList(context)
        for (setting in proxyChannelList) {
            try {
                action(getContextFromProxyChannel(context, setting))?.let { return it }
            } catch (exception: Exception) {
                logger.warn("Failed to execute map with channel ${setting.name}", exception)
            }
        }
        return null
    }

    /**
     * 遍历代理仓库列表，执行[action]操作，并将结果聚合成[List]返回
     */
    private fun <R> mapEachProxyRepo(context: ArtifactContext, action: (ArtifactContext) -> R?): MutableList<R> {
        val proxyChannelList = getProxyChannelList(context)
        val mapResult = mutableListOf<R>()
        for (proxyChannel in proxyChannelList) {
            try {
                action(getContextFromProxyChannel(context, proxyChannel))?.let { mapResult.add(it) }
            } catch (exception: Exception) {
                logger.warn("Failed to execute map with channel ${proxyChannel.name}", exception)
            }
        }
        return mapResult
    }

    /**
     * 遍历代理仓库列表，执行[action]操作
     */
    private fun forEachProxyRepo(context: ArtifactContext, action: (ArtifactContext) -> Unit) {
        val proxyChannelList = getProxyChannelList(context)
        for (proxyChannel in proxyChannelList) {
            try {
                action(getContextFromProxyChannel(context, proxyChannel))
            } catch (exception: Exception) {
                logger.warn("Failed to execute action with channel ${proxyChannel.name}", exception)
            }
        }
    }

    private fun getProxyChannelList(context: ArtifactContext): List<ProxyChannelSetting> {
        return context.getCompositeConfiguration().proxy.channelList
    }

    private fun getContextFromProxyChannel(context: ArtifactContext, setting: ProxyChannelSetting): ArtifactContext {
        return if (setting.public) {
            getContextFromPublicProxyChannel(context, setting)
        } else {
            getContextFromPrivateProxyChannel(context, setting)
        } as ArtifactDownloadContext
    }

    private fun getContextFromPublicProxyChannel(context: ArtifactContext, setting: ProxyChannelSetting): ArtifactContext {
        // 查询公共源详情
        val proxyChannel = proxyChannelClient.getById(setting.channelId!!).data!!
        // 查询远程仓库
        val repoType = proxyChannel.repoType.name
        val projectId = PUBLIC_PROXY_PROJECT
        val repoName = PUBLIC_PROXY_REPO_NAME.format(repoType, proxyChannel.name)
        val remoteRepoDetail = repositoryClient.getRepoDetail(projectId, repoName, repoType).data!!
        // 构造proxyConfiguration
        val remoteConfiguration = remoteRepoDetail.configuration as RemoteConfiguration
        remoteConfiguration.proxy = RemoteProxyConfiguration(proxyChannel.url, proxyChannel.username, proxyChannel.password)

        return context.copy(remoteRepoDetail)
    }

    private fun getContextFromPrivateProxyChannel(context: ArtifactContext, setting: ProxyChannelSetting): ArtifactContext {
        // 查询远程仓库
        val projectId = context.repositoryDetail.projectId
        val repoType = context.repositoryDetail.type.name
        val repoName = PRIVATE_PROXY_REPO_NAME.format(context.repositoryDetail.name, setting.name)
        val remoteRepoDetail = repositoryClient.getRepoDetail(projectId, repoName, repoType).data!!
        // 构造proxyConfiguration
        val remoteConfiguration = remoteRepoDetail.configuration as RemoteConfiguration
        remoteConfiguration.proxy = RemoteProxyConfiguration(setting.url!!, setting.username, setting.password)

        return context.copy(remoteRepoDetail)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CompositeRepository::class.java)
    }
}
