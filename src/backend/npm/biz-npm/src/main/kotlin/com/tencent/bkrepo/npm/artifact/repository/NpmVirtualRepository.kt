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

package com.tencent.bkrepo.npm.artifact.repository

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.util.version.SemVersion
import com.tencent.bkrepo.npm.constants.SEARCH_REQUEST
import com.tencent.bkrepo.npm.model.metadata.NpmPackageMetaData
import com.tencent.bkrepo.npm.pojo.NpmSearchInfoMap
import com.tencent.bkrepo.npm.pojo.metadata.MetadataSearchRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream

@Component
class NpmVirtualRepository : VirtualRepository() {

    @Suppress("UNCHECKED_CAST")
    override fun search(context: ArtifactSearchContext): List<NpmSearchInfoMap> {
        val list = mutableListOf<NpmSearchInfoMap>()
        val searchRequest = context.getAttribute<MetadataSearchRequest>(SEARCH_REQUEST)!!
        val virtualConfiguration = context.getVirtualConfiguration()
        val repoList = virtualConfiguration.repositoryList
        val traversedList = getTraversedList(context)
        for (repoIdentify in repoList) {
            if (repoIdentify in traversedList) {
                continue
            }
            traversedList.add(repoIdentify)
            try {
                val subRepoInfo = repositoryClient.getRepoDetail(context.projectId, repoIdentify.name).data!!
                val repository = ArtifactContextHolder.getRepository(subRepoInfo.category) as AbstractArtifactRepository
                val subContext = context.copy(repositoryDetail = subRepoInfo) as ArtifactSearchContext
                repository.search(subContext).let { map ->
                    list.addAll(map as List<NpmSearchInfoMap>)
                }
            } catch (exception: Exception) {
                logger.error(
                    "list Artifact[${context.artifactInfo}] " +
                        "from Repository[$repoIdentify] failed: ${exception.message}"
                )
            }
        }
        return list.subList(0, searchRequest.size)
    }

    override fun query(context: ArtifactQueryContext): InputStream? {
        val localResult = mapEachSubRepo(context, RepositoryCategory.LOCAL) { subContext, repository ->
            require(subContext is ArtifactQueryContext)
            repository.query(subContext) as? InputStream
        }.map {
            inputStream -> inputStream.use { JsonUtils.objectMapper.readValue(it, NpmPackageMetaData::class.java) }
        }

        val remoteResult = mapFirstRepo(context, RepositoryCategory.REMOTE) { subContext, repository ->
            require(subContext is ArtifactQueryContext)
            repository.query(subContext) as? InputStream
        }?.use { JsonUtils.objectMapper.readValue(it, NpmPackageMetaData::class.java) }

        val metadataList = remoteResult?.run { localResult + this } ?: localResult
        if (metadataList.isEmpty()) return null
        // 聚合多个仓库的包级别元数据
        val packageMetadata = metadataList.reduce { acc, element -> aggregateMetadata(acc, element) }
        val metadataString = JsonUtils.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(packageMetadata)
        return ArtifactFileFactory.build(metadataString.byteInputStream()).getInputStream()
    }

    private fun aggregateMetadata(
        originMetadata: NpmPackageMetaData,
        newMetadata: NpmPackageMetaData
    ): NpmPackageMetaData {
        return originMetadata.apply {
            for ((version, metadata) in newMetadata.versions.map) {
                versions.map.putIfAbsent(version, metadata)
            }
            for ((tag, newVersion) in newMetadata.distTags.getMap()) {
                val originVersion = distTags.getMap()[tag]
                if (originVersion.isNullOrEmpty() || SemVersion.parse(originVersion) < SemVersion.parse(newVersion)) {
                    distTags.set(tag, newVersion)
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NpmVirtualRepository::class.java)
    }
}
