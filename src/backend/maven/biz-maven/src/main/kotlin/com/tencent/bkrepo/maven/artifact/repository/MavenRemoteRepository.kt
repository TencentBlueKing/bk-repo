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

package com.tencent.bkrepo.maven.artifact.repository

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.artifact.MavenDeleteArtifactInfo
import com.tencent.bkrepo.maven.constants.IS_ARTIFACT
import com.tencent.bkrepo.maven.constants.MAVEN_METADATA_FILE_NAME
import com.tencent.bkrepo.maven.constants.METADATA_KEY_ARTIFACT_ID
import com.tencent.bkrepo.maven.constants.METADATA_KEY_CLASSIFIER
import com.tencent.bkrepo.maven.constants.METADATA_KEY_GROUP_ID
import com.tencent.bkrepo.maven.constants.METADATA_KEY_PACKAGING
import com.tencent.bkrepo.maven.constants.METADATA_KEY_VERSION
import com.tencent.bkrepo.maven.constants.PACKAGE_SUFFIX_REGEX
import com.tencent.bkrepo.maven.constants.SNAPSHOT_BUILD_NUMBER
import com.tencent.bkrepo.maven.constants.SNAPSHOT_TIMESTAMP
import com.tencent.bkrepo.maven.constants.X_CHECKSUM_SHA1
import com.tencent.bkrepo.maven.enum.HashType
import com.tencent.bkrepo.maven.enum.MavenMessageCode
import com.tencent.bkrepo.maven.exception.MavenArtifactNotFoundException
import com.tencent.bkrepo.maven.pojo.MavenArtifactVersionData
import com.tencent.bkrepo.maven.service.MavenOperationService
import com.tencent.bkrepo.maven.util.MavenGAVCUtils.toMavenGAVC
import com.tencent.bkrepo.maven.util.MavenStringUtils.checksumType
import com.tencent.bkrepo.maven.util.MavenStringUtils.isSnapshotMetadataChecksumUri
import com.tencent.bkrepo.maven.util.MavenStringUtils.isSnapshotMetadataUri
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import okhttp3.Request
import okhttp3.Response
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.regex.Pattern

@Component
class MavenRemoteRepository(
    private val mavenOperationService: MavenOperationService
) : RemoteRepository() {

    /**
     * 针对索引文件`maven-metadata.xml` 每次都尝试从远程拉取最新的索引文件，
     * 如果远程下载失败则改为使用缓存
     */
    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return if ((context.artifactInfo as MavenArtifactInfo).isAboutPackageMetadata()) {
            val remoteConfiguration = context.getRemoteConfiguration()
            val httpClient = createHttpClient(remoteConfiguration)
            val downloadUrl = createRemoteDownloadUrl(context)
            val request = Request.Builder().url(downloadUrl).build()
            val response = httpClient.newCall(request).execute()
            logger.info("Metadata not found in cache, download from $downloadUrl with $remoteConfiguration")
            if (checkResponse(response)) {
                onDownloadResponse(context, response)
            } else getCacheArtifactResource(context)
        } else super.onDownload(context)
    }

    override fun query(context: ArtifactQueryContext): MavenArtifactVersionData? {
        return mavenOperationService.queryVersionDetail(context)
    }

    override fun remove(context: ArtifactRemoveContext) {
        when (context.artifactInfo) {
            is MavenDeleteArtifactInfo -> {
                deletePackageOrVersion(
                    artifactInfo = context.artifactInfo as MavenDeleteArtifactInfo,
                    userId = context.userId
                )
            }
            else -> {
                val fullPath = context.artifactInfo.getArtifactFullPath()
                val nodeInfo = nodeClient.getNodeDetail(context.projectId, context.repoName, fullPath).data
                    ?: throw MavenArtifactNotFoundException(
                        MavenMessageCode.MAVEN_ARTIFACT_NOT_FOUND, fullPath, context.artifactInfo.getRepoIdentify()
                    )
                if (nodeInfo.folder) {
                    mavenOperationService.folderRemoveHandler(context, nodeInfo)?.let {
                        deletePackageOrVersion(
                            artifactInfo = it,
                            userId = context.userId
                        )
                    }
                } else {
                    logger.info(
                        "Will try to delete node ${nodeInfo.fullPath} in repo ${context.artifactInfo.getRepoIdentify()}"
                    )
                    mavenOperationService.deleteNode(
                        artifactInfo = context.artifactInfo,
                        userId = context.userId
                    )
                }
            }
        }
    }

    @Suppress("NestedBlockDepth")
    override fun onDownloadResponse(context: ArtifactDownloadContext, response: Response): ArtifactResource {
        val fullPath = context.artifactInfo.getArtifactFullPath()
        val matcher = Pattern.compile(PACKAGE_SUFFIX_REGEX).matcher(fullPath)
        logger.info(
            "Resolving response for downloading file [$fullPath] to repo ${context.artifactInfo.getRepoIdentify()}"
        )
        val artifactFile = createTempFile(response.body!!)
        if (matcher.matches()) {
            val (packaging, isArtifact) = mavenOperationService.resolveMavenArtifact(matcher.group(2), artifactFile)
            context.putAttribute(IS_ARTIFACT, isArtifact)
            context.putAttribute(METADATA_KEY_PACKAGING, packaging)
        }
        if (fullPath.isSnapshotMetadataUri()) {
            artifactFile.getInputStream().use { MetadataXpp3Reader().read(it) }?.versioning?.snapshot?.run {
                context.putAttribute(SNAPSHOT_TIMESTAMP, timestamp.replace(".", ""))
                context.putAttribute(SNAPSHOT_BUILD_NUMBER, buildNumber)
            }
        }
        if (fullPath.isSnapshotMetadataChecksumUri()) {
            val metadataNode = nodeClient.getNodeDetail(
                context.projectId, context.repoName, fullPath.substringBeforeLast(".")
            ).data
            metadataNode?.nodeMetadata?.find { it.key == SNAPSHOT_TIMESTAMP }?.value?.let {
                context.putAttribute(SNAPSHOT_TIMESTAMP, it)
            }
        }
        if (fullPath.checksumType() == null) {
            context.response.setHeader(X_CHECKSUM_SHA1, artifactFile.getFileSha1())
        }
        val size = artifactFile.getSize()
        val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
        val node = cacheArtifactFile(context, artifactFile)
        return ArtifactResource(
            artifactStream,
            context.artifactInfo.getResponseName(),
            node,
            ArtifactChannel.PROXY,
            context.useDisposition
        )
    }

    override fun onDownloadSuccess(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource,
        throughput: Throughput
    ) {
        val isArtifact = context.getBooleanAttribute(IS_ARTIFACT) ?: false
        logger.info("Current downloaded file is artifact: $isArtifact")
        if (isArtifact) {
            val size = artifactResource.getTotalSize()
            val mavenGavc = (context.artifactInfo as MavenArtifactInfo).toMavenGAVC()
            val fullPath = context.artifactInfo.getArtifactFullPath()
            mavenOperationService.createMavenVersion(context, mavenGavc, fullPath, size)
        }
        super.onDownloadSuccess(context, artifactResource, throughput)
    }

    // maven 客户端下载统计
    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        with(context) {
            return mavenOperationService.buildPackageDownloadRecord(
                projectId, repoName, artifactInfo.getArtifactFullPath()
            )
        }
    }

    /**
     * 删除package或Version
     */
    private fun deletePackageOrVersion(
        artifactInfo: MavenDeleteArtifactInfo,
        userId: String
    ) {
        with(artifactInfo) {
            logger.info("Will prepare to delete package [$packageName|$version] in repo ${getRepoIdentify()}")
            if (version.isBlank()) {
                mavenOperationService.deletePackage(artifactInfo, userId)
            } else {
                packageClient.findVersionByName(projectId, repoName, packageName, version).data?.let {
                    mavenOperationService.removeVersion(artifactInfo, it, userId)
                } ?: throw VersionNotFoundException(version)
            }
        }
    }

    override fun buildCacheNodeCreateRequest(context: ArtifactContext, artifactFile: ArtifactFile): NodeCreateRequest {
        val nodeCreateRequest = super.buildCacheNodeCreateRequest(context, artifactFile)
        val packaging = context.getStringAttribute(METADATA_KEY_PACKAGING)
        val fullPath = context.artifactInfo.getArtifactFullPath()
        if (packaging != null || fullPath.endsWith(MAVEN_METADATA_FILE_NAME)) {
            val metadata = nodeCreateRequest.nodeMetadata?.toMutableList() ?: mutableListOf()
            mavenOperationService.createNodeMetaData(artifactFile).forEach { metadata.add(it) }
            if (packaging != null) {
                val mavenGavc = (context.artifactInfo as MavenArtifactInfo).toMavenGAVC()
                metadata.add(MetadataModel(key = METADATA_KEY_PACKAGING, value = packaging))
                metadata.add(MetadataModel(key = METADATA_KEY_GROUP_ID, value = mavenGavc.groupId))
                metadata.add(MetadataModel(key = METADATA_KEY_ARTIFACT_ID, value = mavenGavc.artifactId))
                metadata.add(MetadataModel(key = METADATA_KEY_VERSION, value = mavenGavc.version))
                mavenGavc.classifier?.let { metadata.add(MetadataModel(key = METADATA_KEY_CLASSIFIER, value = it)) }
            }
            if (fullPath.isSnapshotMetadataUri()) {
                context.getStringAttribute(SNAPSHOT_TIMESTAMP)?.let {
                    metadata.add(MetadataModel(SNAPSHOT_TIMESTAMP, it))
                }
                context.getStringAttribute(SNAPSHOT_BUILD_NUMBER)?.let {
                    metadata.add(MetadataModel(SNAPSHOT_BUILD_NUMBER, it))
                }
            }
            return nodeCreateRequest.copy(nodeMetadata = metadata)
        }
        return nodeCreateRequest
    }

    override fun loadArtifactResource(cacheNode: NodeDetail, context: ArtifactDownloadContext): ArtifactResource? {
        return super.loadArtifactResource(cacheNode, context)?.also {
            cacheNode.nodeMetadata.find { it.key == HashType.SHA1.ext }?.let {
                context.response.setHeader(X_CHECKSUM_SHA1, it.value.toString())
            }
            if (context.artifactInfo.getArtifactFullPath().isSnapshotMetadataUri()) {
                cacheNode.nodeMetadata.find { it.key == SNAPSHOT_TIMESTAMP }?.let {
                    context.putAttribute(SNAPSHOT_TIMESTAMP, it.value)
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MavenRemoteRepository::class.java)
    }
}
