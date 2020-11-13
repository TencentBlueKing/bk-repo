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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.tencent.bkrepo.common.artifact.repository.context

import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_KEY
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.composite.CompositeRepository
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerMapping
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest

@Component
class ArtifactContextHolder(
    artifactConfiguration: ArtifactConfiguration,
    repositoryClient: RepositoryClient,
    localRepository: ObjectProvider<LocalRepository>,
    remoteRepository: ObjectProvider<RemoteRepository>,
    virtualRepository: ObjectProvider<VirtualRepository>,
    compositeRepository: ObjectProvider<CompositeRepository>
) {

    init {
        Companion.artifactConfiguration = artifactConfiguration
        Companion.repositoryClient = repositoryClient
        Companion.localRepository = localRepository
        Companion.remoteRepository = remoteRepository
        Companion.virtualRepository = virtualRepository
        Companion.compositeRepository = compositeRepository
    }

    companion object {
        lateinit var artifactConfiguration: ArtifactConfiguration
        private lateinit var repositoryClient: RepositoryClient
        private lateinit var localRepository: ObjectProvider<LocalRepository>
        private lateinit var remoteRepository: ObjectProvider<RemoteRepository>
        private lateinit var virtualRepository: ObjectProvider<VirtualRepository>
        private lateinit var compositeRepository: ObjectProvider<CompositeRepository>
        private val repositoryDetailCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build<RepositoryId, RepositoryDetail>()

        fun getRepository(repositoryCategory: RepositoryCategory? = null): ArtifactRepository {
            return when (repositoryCategory ?: getRepoDetail()!!.category) {
                RepositoryCategory.LOCAL -> localRepository.`object`
                RepositoryCategory.REMOTE -> remoteRepository.`object`
                RepositoryCategory.VIRTUAL -> virtualRepository.`object`
                RepositoryCategory.COMPOSITE -> compositeRepository.`object`
            }
        }

        fun getRepoDetail(): RepositoryDetail? {
            val request = HttpContextHolder.getRequestOrNull() ?: return null
            val repositoryAttribute = request.getAttribute(REPO_KEY)
            return if (repositoryAttribute == null) {
                val repositoryId = getRepositoryId(request)
                val repoDetail = repositoryDetailCache.getIfPresent(repositoryId) ?: run {
                    queryRepoDetail(repositoryId).apply { repositoryDetailCache.put(repositoryId, this) }
                }
                request.setAttribute(REPO_KEY, repoDetail)
                return repoDetail
            } else {
                repositoryAttribute as RepositoryDetail
            }
        }

        private fun getRepositoryId(request: HttpServletRequest): RepositoryId {
            val artifactInfoAttribute = request.getAttribute(ARTIFACT_INFO_KEY)
            return if (artifactInfoAttribute == null) {
                val attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
                val projectId = attributes[PROJECT_ID].toString()
                val repoName = attributes[REPO_NAME].toString()
                RepositoryId(projectId, repoName)
            } else {
                val artifactInfo = artifactInfoAttribute as ArtifactInfo
                RepositoryId(artifactInfo.projectId, artifactInfo.repoName)
            }
        }

        private fun queryRepoDetail(repositoryId: RepositoryId): RepositoryDetail {
            with(repositoryId) {
                val repoType = artifactConfiguration.getRepositoryType().name
                val response = repositoryClient.getRepoDetail(projectId, repoName, repoType)
                return response.data ?: throw ArtifactNotFoundException("Repository[$repositoryId] not found")
            }
        }
    }

    data class RepositoryId(val projectId: String, val repoName: String) {
        override fun toString(): String {
            return StringBuilder(projectId).append(CharPool.SLASH).append(repoName).toString()
        }
    }
}
