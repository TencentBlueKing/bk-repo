/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.repository.context

import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.config.ArtifactConfigurer
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_CONFIGURER
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.constant.NODE_DETAIL_KEY
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_KEY
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.artifact.constant.REPO_RATE_LIMIT_KEY
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.composite.CompositeRepository
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.proxy.ProxyRepository
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurity
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.config.RateLimitProperties
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.springframework.beans.factory.ObjectProvider
import org.springframework.util.unit.DataSize
import org.springframework.web.servlet.HandlerMapping
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest

@Suppress("LateinitUsage") // 静态成员通过init构造函数初始化
class ArtifactContextHolder(
    artifactConfigurers: List<ArtifactConfigurer>,
    compositeRepository: CompositeRepository,
    proxyRepository: ProxyRepository,
    repositoryClient: RepositoryClient,
    nodeClient: NodeClient,
    private val httpAuthSecurity: ObjectProvider<HttpAuthSecurity>
) {

    init {
        Companion.artifactConfigurers = artifactConfigurers
        Companion.compositeRepository = compositeRepository
        Companion.proxyRepository = proxyRepository
        Companion.repositoryClient = repositoryClient
        Companion.nodeClient = nodeClient
        Companion.httpAuthSecurity = httpAuthSecurity
        require(artifactConfigurers.isNotEmpty()) { "No ArtifactConfigurer found!" }
        artifactConfigurers.forEach {
            artifactConfigurerMap[it.getRepositoryType()] = it
        }
    }

    companion object {
        private lateinit var artifactConfigurers: List<ArtifactConfigurer>
        private lateinit var compositeRepository: CompositeRepository
        private lateinit var proxyRepository: ProxyRepository
        private lateinit var repositoryClient: RepositoryClient
        private lateinit var nodeClient: NodeClient
        private lateinit var httpAuthSecurity: ObjectProvider<HttpAuthSecurity>


        private const val RECEIVE_RATE_LIMIT_OF_REPO = "receiveRateLimit"
        private const val RESPONSE_RATE_LIMIT_OF_REPO = "responseRateLimit"

        private val artifactConfigurerMap = mutableMapOf<RepositoryType, ArtifactConfigurer>()
        private val repositoryDetailCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build<RepositoryId, RepositoryDetail>()
        private val regex = Regex("""com\.tencent\.bkrepo\.(\w+)\..*""")

        /**
         * 获取当前服务对应的[ArtifactConfigurer]
         */
        fun getCurrentArtifactConfigurer(): ArtifactConfigurer {
            if (artifactConfigurerMap.size == 1) {
                return artifactConfigurers.first()
            }
            val request = HttpContextHolder.getRequest()
            val artifactConfigurer = request.getAttribute(ARTIFACT_CONFIGURER)
            if (artifactConfigurer != null) {
                require(artifactConfigurer is ArtifactConfigurer)
                return artifactConfigurer
            }
            val service = request.requestURI.trimStart(CharPool.SLASH).split(CharPool.SLASH).first()
            val type = RepositoryType.ofValueOrDefault(service)
            val currentConfigurer = artifactConfigurerMap[type]
            checkNotNull(currentConfigurer) { "Artifact service [$type] not found" }
            request.setAttribute(ARTIFACT_CONFIGURER, currentConfigurer)
            return currentConfigurer
        }

        /**
         * 根据仓库类型[category]获取对应仓库实现类
         * 如果[category]为`null`，会根据path variable提取仓库并查询category
         */
        fun getRepository(category: RepositoryCategory? = null): ArtifactRepository {
            val currentArtifactConfigurer = getCurrentArtifactConfigurer()
            return when (category ?: getRepoDetail()!!.category) {
                RepositoryCategory.LOCAL -> currentArtifactConfigurer.getLocalRepository()
                RepositoryCategory.REMOTE -> currentArtifactConfigurer.getRemoteRepository()
                RepositoryCategory.VIRTUAL -> currentArtifactConfigurer.getVirtualRepository()
                RepositoryCategory.COMPOSITE -> compositeRepository
                RepositoryCategory.PROXY -> proxyRepository
            }
        }

        /**
         * 根据当前请求获取对应ArtifactInfo信息
         * 如果请求为空，则返回`null`
         */
        fun getArtifactInfo(): ArtifactInfo? {
            val artifactInfo = HttpContextHolder.getRequestOrNull()?.getAttribute(ARTIFACT_INFO_KEY) ?: return null
            require(artifactInfo is ArtifactInfo)
            return artifactInfo
        }

        /**
         * 根据指定请求获取对应ArtifactInfo信息
         * 如果请求为空，则返回`null`
         */
        fun getArtifactInfo(request: HttpServletRequest): ArtifactInfo? {
            val artifactInfo = request.getAttribute(ARTIFACT_INFO_KEY) ?: return null
            require(artifactInfo is ArtifactInfo)
            return artifactInfo
        }

        /**
         * 根据当前请求获取对应仓库详情
         * 如果请求为空，则返回`null`
         * 注意：http请求必须带上projectId和repoName，否则会抛出RepoNotFoundException
         * 如需不抛出异常，使用getRepoDetailOrNull
         */
        fun getRepoDetail(): RepositoryDetail? {
            val request = HttpContextHolder.getRequestOrNull() ?: return null
            val repositoryAttribute = request.getAttribute(REPO_KEY)
            if (repositoryAttribute != null) {
                require(repositoryAttribute is RepositoryDetail)
                return repositoryAttribute
            }
            val repositoryId = getRepositoryId(request)
            val repoDetail = repositoryDetailCache.getIfPresent(repositoryId) ?: run {
                queryRepoDetail(repositoryId).apply { repositoryDetailCache.put(repositoryId, this) }
            }
            request.setAttribute(REPO_KEY, repoDetail)
            return repoDetail
        }

        /**
         * 根据当前请求获取对应仓库详情
         * 如果请求为空，则返回`null`
         */
        fun getRepoDetailOrNull(): RepositoryDetail? {
            return try {
                getRepoDetail()
            } catch (e: RepoNotFoundException) {
                null
            }
        }

        /**
         * 在非http的上下文获取仓库信息
         * */
        fun getRepoDetail(repositoryId: RepositoryId): RepositoryDetail {
            return repositoryDetailCache.get(repositoryId) {
                queryRepoDetail(repositoryId)
            }
        }

        /**
         * 获取url path。自动处理url前缀
         * @param className 调用者的类名
         * */
        fun getUrlPath(className: String): String? {
            val request = HttpContextHolder.getRequestOrNull() ?: return null
            val realPath = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
            val serviceName = regex.find(className)?.groupValues?.get(1)
            val security = httpAuthSecurity.stream().filter {
                it.prefix == "/$serviceName"
            }.findFirst()
            var path = realPath
            security.ifPresent {
                if (it.prefixEnabled) {
                    path = realPath.removePrefix(it.prefix)
                }
            }
            return path
        }

        /**
         * 根据请求[request]获取[RepositoryId]
         * 解析path variable得到projectId和repoName，并保存在request的attributes中
         */
        private fun getRepositoryId(request: HttpServletRequest): RepositoryId {
            val artifactInfoAttribute = request.getAttribute(ARTIFACT_INFO_KEY)
            if (artifactInfoAttribute != null) {
                require(artifactInfoAttribute is ArtifactInfo)
                return RepositoryId(artifactInfoAttribute.projectId, artifactInfoAttribute.repoName)
            }
            val uriAttribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
            require(uriAttribute is Map<*, *>)
            val projectId = uriAttribute[PROJECT_ID].toString()
            val repoName = uriAttribute[REPO_NAME].toString()
            return RepositoryId(projectId, repoName)
        }

        /**
         * 根据[repositoryId]查询仓库详情
         * 当对应仓库不存在，抛[RepoNotFoundException]异常
         */
        private fun queryRepoDetail(repositoryId: RepositoryId): RepositoryDetail {
            with(repositoryId) {
                val repoType = getCurrentArtifactConfigurer().getRepositoryType().name
                val response = repositoryClient.getRepoDetail(projectId, repoName, repoType)
                return response.data ?: queryRepoDetailFormExtraRepoType(projectId, repoName)
            }
        }

        /**
         * 当主仓库类型查不到，则从其他支持类型获取
         * 当对应仓库不存在，抛[RepoNotFoundException]异常
         */
        fun queryRepoDetailFormExtraRepoType(projectId: String, repoName: String): RepositoryDetail {
            val repoTypeList = getCurrentArtifactConfigurer().getRepositoryTypes()
            var otherRepo: RepositoryDetail? = null
            repoTypeList.forEach {
                val repo = repositoryClient.getRepoDetail(projectId, repoName, it.name).data
                if (repo != null) {
                    otherRepo = repo
                    return@forEach
                }
            }
            return otherRepo ?: throw RepoNotFoundException(repoName)
        }

        fun getNodeDetail(artifactInfo: ArtifactInfo): NodeDetail? {
            val request = HttpContextHolder.getRequestOrNull() ?: return null
            val finalProjectId = artifactInfo.projectId
            val finalRepoName = artifactInfo.repoName
            val finalFullPath = artifactInfo.getArtifactFullPath()

            val attrKey = "$NODE_DETAIL_KEY:$finalProjectId:$finalRepoName$finalFullPath"
            val nodeDetailAttribute = request.getAttribute(attrKey)
            if (nodeDetailAttribute != null) {
                require(nodeDetailAttribute is NodeDetail)
                return nodeDetailAttribute
            }

            val nodeDetail = nodeClient.getNodeDetail(
                projectId = finalProjectId,
                repoName = finalRepoName,
                fullPath = finalFullPath
            ).data
            nodeDetail?.let { request.setAttribute(attrKey, nodeDetail) }
            return nodeDetail
        }

        /**
         * 获取仓库级别的限速配置
         */
        fun getRateLimitOfRepo(): RateLimitProperties {
            val request = HttpContextHolder.getRequestOrNull() ?: return RateLimitProperties()
            val repoRateLimitAttribute = request.getAttribute(REPO_RATE_LIMIT_KEY)
            if (repoRateLimitAttribute != null) {
                require(repoRateLimitAttribute is RateLimitProperties)
                return repoRateLimitAttribute
            }
            // 临时下载链接可能会使用 generic 下载其他业务类型的制品，此处先避免仓库找不到异常
            // 此处修改潜在风险：当其他类型仓库使用 generic 服务的临时下载链接下载时无法控制住
            val repo = getRepoDetailOrNull() ?: return RateLimitProperties()
            val receiveRateLimit = convertToDataSize(repo.configuration.getStringSetting(RECEIVE_RATE_LIMIT_OF_REPO))
            val responseRateLimit = convertToDataSize(repo.configuration.getStringSetting(RESPONSE_RATE_LIMIT_OF_REPO))
            val rateLimitProperties = RateLimitProperties(receiveRateLimit, responseRateLimit)
            request.setAttribute(REPO_RATE_LIMIT_KEY, rateLimitProperties)
            return rateLimitProperties

        }

        private fun convertToDataSize(dataSize: String?): DataSize {
            if (dataSize.isNullOrEmpty()) return DataSize.ofBytes(-1)
            return try {
                DataSize.parse(dataSize)
            } catch (e: Exception) {
                DataSize.ofBytes(-1)
            }
        }
    }

    /**
     * 仓库标识类
     */
    data class RepositoryId(val projectId: String, val repoName: String) {
        override fun toString(): String {
            return StringBuilder(projectId).append(CharPool.SLASH).append(repoName).toString()
        }
    }
}
