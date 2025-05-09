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
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.constants.MAVEN_METADATA_FILE_NAME
import com.tencent.bkrepo.maven.constants.PACKAGE_SUFFIX_REGEX
import com.tencent.bkrepo.maven.constants.SNAPSHOT_BUILD_NUMBER
import com.tencent.bkrepo.maven.constants.SNAPSHOT_TIMESTAMP
import com.tencent.bkrepo.maven.constants.X_CHECKSUM_SHA1
import com.tencent.bkrepo.maven.enum.HashType
import com.tencent.bkrepo.maven.enum.MavenMessageCode
import com.tencent.bkrepo.maven.exception.MavenArtifactNotFoundException
import com.tencent.bkrepo.maven.pojo.Basic
import com.tencent.bkrepo.maven.pojo.MavenArtifactVersionData
import com.tencent.bkrepo.maven.pojo.MavenGAVC
import com.tencent.bkrepo.maven.service.MavenMetadataService
import com.tencent.bkrepo.maven.service.MavenService
import com.tencent.bkrepo.maven.util.DigestUtils
import com.tencent.bkrepo.maven.util.MavenGAVCUtils.mavenGAVC
import com.tencent.bkrepo.maven.util.MavenGAVCUtils.toMavenGAVC
import com.tencent.bkrepo.maven.util.MavenStringUtils.checksumType
import com.tencent.bkrepo.maven.util.MavenStringUtils.formatSeparator
import com.tencent.bkrepo.maven.util.MavenStringUtils.isSnapshotMetadataChecksumUri
import com.tencent.bkrepo.maven.util.MavenStringUtils.isSnapshotMetadataUri
import com.tencent.bkrepo.repository.api.StageClient
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import okhttp3.Request
import okhttp3.Response
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Component
class MavenRemoteRepository(
    private val stageClient: StageClient,
    private val mavenMetadataService: MavenMetadataService,
    private val mavenService: MavenService,
) : RemoteRepository() {


    /**
     * 针对索引文件`maven-metadata.xml` 每次都尝试从远程拉取最新的索引文件，
     * 如果远程下载失败则改为使用缓存
     */
    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        val artifactInfo = context.artifactInfo as MavenArtifactInfo
        if (!artifactInfo.isMetadata()) {
            return super.onDownload(context)
        }

        // 准备远程下载
        val remoteConfiguration = context.getRemoteConfiguration()
        val httpClient = createHttpClient(remoteConfiguration)
        val downloadUrl = createRemoteDownloadUrl(context)
        val request = Request.Builder().url(downloadUrl).build()

        logger.info("Remote download url: $downloadUrl, network config: ${remoteConfiguration.network}")

        // 尝试远程下载
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            logger.warn("An error occurred while sending request $downloadUrl", e)
            null
        }

        return when {
            response != null && checkResponse(response) -> onDownloadResponse(context, response)
            else -> getCacheArtifactResource(context)
        }
    }

    override fun query(context: ArtifactQueryContext): MavenArtifactVersionData? {
        val packageKey = context.request.getParameter("packageKey")
        val version = context.request.getParameter("version")
        val artifactId = packageKey.split(":").last()
        val groupId = packageKey.removePrefix("gav://").split(":")[0]
        val trueVersion = packageService.findVersionByName(
            context.projectId,
            context.repoName,
            packageKey,
            version
        ) ?: throw MavenArtifactNotFoundException(
            MavenMessageCode.MAVEN_VERSION_NOT_FOUND, "Can not find version $version of package $packageKey"
        )
        with(context.artifactInfo) {
            val jarNode = nodeService.getNodeDetail(
                ArtifactInfo(
                    projectId,
                    repoName,
                    trueVersion.contentPath!!
                )
            ) ?: return null
            val type = (jarNode.metadata["packaging"] as? String) ?: "jar"
            val classifier = jarNode.metadata["classifier"] as? String
            val stageTag = stageClient.query(projectId, repoName, packageKey, version).data
            val packageVersion = packageService.findVersionByName(
                projectId,
                repoName,
                packageKey,
                version
            )
            val count = packageVersion?.downloads ?: 0
            val createdDate = packageVersion?.createdDate?.format(DateTimeFormatter.ISO_DATE_TIME)
                ?: jarNode.createdDate
            val lastModifiedDate = packageVersion?.lastModifiedDate?.format(DateTimeFormatter.ISO_DATE_TIME)
                ?: jarNode.lastModifiedDate
            val mavenArtifactMavenBasicInfo = Basic(
                groupId,
                artifactId,
                version,
                if (type == "jar") null else type,
                jarNode.size, jarNode.fullPath,
                jarNode.createdBy, createdDate,
                jarNode.lastModifiedBy, lastModifiedDate,
                count,
                jarNode.sha256!!,
                jarNode.md5!!,
                stageTag ?: emptyList(),
                null
            )
            return MavenArtifactVersionData(mavenArtifactMavenBasicInfo, packageVersion?.packageMetadata)
        }
    }

    override fun remove(context: ArtifactRemoveContext) {
        mavenService.remove(context, true)
    }

    @Suppress("NestedBlockDepth")
    override fun onDownloadResponse(
        context: ArtifactDownloadContext,
        response: Response,
        useDisposition: Boolean,
        syncCache: Boolean,
    ): ArtifactResource {
        val fullPath = context.artifactInfo.getArtifactFullPath()
        val matcher = Pattern.compile(PACKAGE_SUFFIX_REGEX).matcher(fullPath)
        logger.info(
            "Resolving response for downloading file [$fullPath] to repo ${context.artifactInfo.getRepoIdentify()}"
        )
        val artifactFile = createTempFile(response.body!!)
        if (matcher.matches()) {
            var packaging = matcher.group(2)
            val fileSuffix = packaging
            logger.info("Downloaded File's type is $fileSuffix")
            val isArtifact = (packaging == fileSuffix)
            logger.info("current downloaded file is artifact: $isArtifact")
            context.putAttribute("isArtifact", isArtifact)
            context.putAttribute("packaging", packaging)
        }
        if (fullPath.isSnapshotMetadataUri()) {
            artifactFile.getInputStream().use { MetadataXpp3Reader().read(it) }?.versioning?.snapshot?.run {
                context.putAttribute(SNAPSHOT_TIMESTAMP, timestamp.replace(".", ""))
                context.putAttribute(SNAPSHOT_BUILD_NUMBER, buildNumber)
            }
        }
        if (fullPath.isSnapshotMetadataChecksumUri()) {
            val metadataNode = nodeService.getNodeDetail(
                ArtifactInfo(
                    context.projectId, context.repoName, fullPath.substringBeforeLast(".")
                )
            )
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
        node?.let {
            mavenMetadataService.update(node)
            if (shouldCache(context)) {
                val isArtifact = context.getBooleanAttribute("isArtifact") ?: false
                if (isArtifact) {
                    val size = node.size
                    val mavenGavc = (context.artifactInfo as MavenArtifactInfo).toMavenGAVC()
                    createMavenVersion(context, mavenGavc, context.artifactInfo.getArtifactFullPath(), size)
                }
            }
        }
        return ArtifactResource(
            artifactStream,
            context.artifactInfo.getResponseName(),
            node,
            ArtifactChannel.PROXY,
            context.useDisposition
        )
    }

    // maven 客户端下载统计
    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource,
    ): PackageDownloadRecord? {
        with(context) {
            val fullPath = artifactInfo.getArtifactFullPath()
            val node = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
            val packaging = node?.metadata?.get("packaging")
            if (packaging == null || node.name.endsWith(".pom") && packaging != "pom") {
                return null
            }
            val mavenGAVC = fullPath.mavenGAVC()
            val version = mavenGAVC.version
            val artifactId = mavenGAVC.artifactId
            val groupId = mavenGAVC.groupId.formatSeparator("/", ".")
            val packageKey = PackageKeys.ofGav(groupId, artifactId)
            return PackageDownloadRecord(projectId, repoName, packageKey, version)
        }
    }

    private fun createMavenVersion(
        context: ArtifactDownloadContext,
        mavenGAVC: MavenGAVC,
        fullPath: String,
        size: Long,
    ) {
        logger.info("create maven version: $mavenGAVC with fullPath: $fullPath")
        val metadata: MutableMap<String, String> = mutableMapOf(
            "groupId" to mavenGAVC.groupId,
            "artifactId" to mavenGAVC.artifactId,
            "version" to mavenGAVC.version,
        )
        mavenGAVC.classifier?.let {
            metadata["classifier"] = it
        }
        try {
            mavenGAVC.classifier?.let { metadata["classifier"] = it }
            packageService.createPackageVersion(
                PackageVersionCreateRequest(
                    context.projectId,
                    context.repoName,
                    packageName = mavenGAVC.artifactId,
                    packageKey = PackageKeys.ofGav(mavenGAVC.groupId, mavenGAVC.artifactId),
                    packageType = PackageType.MAVEN,
                    versionName = mavenGAVC.version,
                    size = size,
                    artifactPath = fullPath,
                    overwrite = true,
                    createdBy = context.userId,
                    packageMetadata = metadata.map { MetadataModel(key = it.key, value = it.value, system = true) }
                )
            )
        } catch (ignore: DuplicateKeyException) {
            logger.warn(
                "The package info has been created for version[${mavenGAVC.version}] " +
                    "and package[${mavenGAVC.artifactId}] in repo ${context.artifactInfo.getRepoIdentify()}"
            )
        }
    }

    override fun buildCacheNodeCreateRequest(context: ArtifactContext, artifactFile: ArtifactFile): NodeCreateRequest {
        val nodeCreateRequest = super.buildCacheNodeCreateRequest(context, artifactFile)
        val packaging = context.getStringAttribute("packaging")
        val fullPath = context.artifactInfo.getArtifactFullPath()
        if (packaging != null || fullPath.endsWith(MAVEN_METADATA_FILE_NAME)) {
            val metadata = nodeCreateRequest.nodeMetadata?.toMutableList() ?: mutableListOf()
            createNodeMetaData(artifactFile).forEach { metadata.add(it) }
            if (packaging != null) {
                val mavenGavc = (context.artifactInfo as MavenArtifactInfo).toMavenGAVC()
                metadata.add(MetadataModel(key = "packaging", value = packaging))
                metadata.add(MetadataModel(key = "groupId", value = mavenGavc.groupId))
                metadata.add(MetadataModel(key = "artifactId", value = mavenGavc.artifactId))
                metadata.add(MetadataModel(key = "version", value = mavenGavc.version))
                mavenGavc.classifier?.let { metadata.add(MetadataModel(key = "classifier", value = it)) }
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

    private fun createNodeMetaData(artifactFile: ArtifactFile): List<MetadataModel> {
        val md5 = artifactFile.getFileMd5()
        val sha1 = artifactFile.getFileSha1()
        val sha256 = artifactFile.getFileSha256()
        val sha512 = artifactFile.getInputStream().use {
            val bytes = it.readBytes()
            DigestUtils.sha512(bytes, 0, bytes.size)
        }
        return mutableMapOf(
            HashType.MD5.ext to md5,
            HashType.SHA1.ext to sha1,
            HashType.SHA256.ext to sha256,
            HashType.SHA512.ext to sha512
        ).map {
            MetadataModel(key = it.key, value = it.value)
        }.toMutableList()
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
        private val logger: Logger = LoggerFactory.getLogger(MavenRemoteRepository::class.java)
    }
}