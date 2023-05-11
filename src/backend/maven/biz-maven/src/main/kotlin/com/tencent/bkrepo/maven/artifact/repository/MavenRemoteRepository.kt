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
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.artifact.MavenDeleteArtifactInfo
import com.tencent.bkrepo.maven.constants.MAVEN_METADATA_FILE_NAME
import com.tencent.bkrepo.maven.constants.METADATA_KEY_ARTIFACT_ID
import com.tencent.bkrepo.maven.constants.METADATA_KEY_CLASSIFIER
import com.tencent.bkrepo.maven.constants.METADATA_KEY_GROUP_ID
import com.tencent.bkrepo.maven.constants.METADATA_KEY_PACKAGING
import com.tencent.bkrepo.maven.constants.METADATA_KEY_VERSION
import com.tencent.bkrepo.maven.constants.PACKAGE_SUFFIX_REGEX
import com.tencent.bkrepo.maven.constants.SNAPSHOT_TIMESTAMP
import com.tencent.bkrepo.maven.enum.HashType
import com.tencent.bkrepo.maven.enum.MavenMessageCode
import com.tencent.bkrepo.maven.exception.MavenArtifactNotFoundException
import com.tencent.bkrepo.maven.exception.MavenRequestForbiddenException
import com.tencent.bkrepo.maven.pojo.Basic
import com.tencent.bkrepo.maven.pojo.MavenArtifactVersionData
import com.tencent.bkrepo.maven.pojo.MavenGAVC
import com.tencent.bkrepo.maven.util.DigestUtils
import com.tencent.bkrepo.maven.util.MavenGAVCUtils.mavenGAVC
import com.tencent.bkrepo.maven.util.MavenGAVCUtils.toMavenGAVC
import com.tencent.bkrepo.maven.util.MavenStringUtils.formatSeparator
import com.tencent.bkrepo.maven.util.MavenStringUtils.isSnapshotMetadataUri
import com.tencent.bkrepo.maven.util.MavenUtil
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.lang3.StringUtils
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Component
class MavenRemoteRepository : RemoteRepository() {

    /**
     * 针对索引文件`maven-metadata.xml` 每次都尝试从远程拉取最新的索引文件，
     * 如果远程下载失败则改为使用缓存
     */
    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return if ((context.artifactInfo as MavenArtifactInfo).isMetadata()) {
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
        val packageKey = context.request.getParameter("packageKey")
        val version = context.request.getParameter("version")
        val artifactId = packageKey.split(":").last()
        val groupId = packageKey.removePrefix("gav://").split(":")[0]
        with(context.artifactInfo) {
            val trueVersion = packageClient.findVersionByName(projectId, repoName, packageKey, version).data
                ?: throw MavenArtifactNotFoundException(
                    MavenMessageCode.MAVEN_ARTIFACT_NOT_FOUND, getArtifactFullPath(), getRepoIdentify()
                )
            val jarNode = nodeClient.getNodeDetail(
                projectId,
                repoName,
                trueVersion.contentPath!!
            ).data ?: return null
            val packageVersion = packageClient.findVersionByName(projectId, repoName, packageKey, version).data
            val count = packageVersion?.downloads ?: 0
            val createdDate = packageVersion?.createdDate?.format(DateTimeFormatter.ISO_DATE_TIME)
                ?: jarNode.createdDate
            val lastModifiedDate = packageVersion?.lastModifiedDate?.format(DateTimeFormatter.ISO_DATE_TIME)
                ?: jarNode.lastModifiedDate
            val mavenArtifactBasic = Basic(
                groupId,
                artifactId,
                version,
                jarNode.size, jarNode.fullPath,
                jarNode.createdBy, createdDate,
                jarNode.lastModifiedBy, lastModifiedDate,
                count,
                jarNode.sha256,
                jarNode.md5,
                null,
                null
            )
            return MavenArtifactVersionData(mavenArtifactBasic, packageVersion?.packageMetadata)
        }
    }

    override fun remove(context: ArtifactRemoveContext) {
        when (context.artifactInfo) {
            is MavenDeleteArtifactInfo -> {
                deletePackage(context)
            }
            else -> {
                deleteNode(context)
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
            var packaging = matcher.group(2)
            val fileSuffix = packaging
            logger.info("Downloaded File's type is $fileSuffix")
            if (fileSuffix == "pom") {
                val mavenPomModel = artifactFile.getInputStream().use { MavenXpp3Reader().read(it) }
                val verNotBlank = StringUtils.isNotBlank(mavenPomModel.version)
                val isPom = mavenPomModel.packaging.equals("pom", ignoreCase = true)
                if (!verNotBlank || !isPom) {
                    packaging = mavenPomModel.packaging
                }
            }
            val isArtifact = (packaging == fileSuffix)
            logger.info("current downloaded file is artifact: $isArtifact")
            context.putAttribute("isArtifact", isArtifact)
            context.putAttribute(METADATA_KEY_PACKAGING, packaging)
        }
        var node: NodeDetail? = null
        if (!fullPath.isSnapshotMetadataUri() || isNewerSnapshotTimestamp(context, artifactFile)) {
            node = cacheArtifactFile(context, artifactFile)
        }
        val size = artifactFile.getSize()
        val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
        val artifactName = context.artifactInfo.getResponseName()
        return ArtifactResource(artifactStream, artifactName, node, ArtifactChannel.PROXY, context.useDisposition)
    }

    override fun onDownloadSuccess(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource,
        throughput: Throughput
    ) {
        val isArtifact = context.getBooleanAttribute("isArtifact") ?: false
        logger.info("Current downloaded file is artifact: $isArtifact")
        if (isArtifact) {
            val size = artifactResource.getTotalSize()
            val mavenGavc = (context.artifactInfo as MavenArtifactInfo).toMavenGAVC()
            createMavenVersion(context, mavenGavc, context.artifactInfo.getArtifactFullPath(), size)
        }
        super.onDownloadSuccess(context, artifactResource, throughput)
    }

    // maven 客户端下载统计
    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        with(context) {
            val fullPath = artifactInfo.getArtifactFullPath()
            val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
            val packaging = node?.metadata?.get(METADATA_KEY_PACKAGING)
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
        size: Long
    ) {
        val metadata: MutableMap<String, String> = mutableMapOf(
            METADATA_KEY_GROUP_ID to mavenGAVC.groupId,
            METADATA_KEY_ARTIFACT_ID to mavenGAVC.artifactId,
            METADATA_KEY_VERSION to mavenGAVC.version
        )
        try {
            mavenGAVC.classifier?.let { metadata[METADATA_KEY_CLASSIFIER] = it }
            packageClient.createVersion(
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
                    packageMetadata = metadata.map { MetadataModel(key = it.key, value = it.value) }
                )
            )
        } catch (ignore: DuplicateKeyException) {
            logger.warn(
                "The package info has been created for version[${mavenGAVC.version}] " +
                        "and package[${mavenGAVC.artifactId}] in repo ${context.artifactInfo.getRepoIdentify()}"
            )
        }
    }

    /**
     * 删除package
     */
    fun deletePackage(context: ArtifactRemoveContext) {
        with(context.artifactInfo as MavenDeleteArtifactInfo) {
            logger.info("Will prepare to delete package [$packageName|$version] in repo ${getRepoIdentify()}")
            if (version.isBlank()) {
                packageClient.listAllVersion(projectId, repoName, packageName).data.orEmpty().forEach {
                    removeVersion(context, it)
                }
                // 删除package下得metadata.xml文件
                deleteNode(context, true)
            } else {
                packageClient.findVersionByName(projectId, repoName, packageName, version).data?.let {
                    removeVersion(context, it)
                } ?: throw VersionNotFoundException(version)
            }
        }
    }

    /**
     * 删除[version] 对应的node节点也会一起删除
     */
    private fun removeVersion(context: ArtifactRemoveContext, version: PackageVersion) {
        with(context.artifactInfo as MavenDeleteArtifactInfo) {
            logger.info(
                "Will delete package $packageName version ${version.name} in repo ${getRepoIdentify()}"
            )
            packageClient.deleteVersion(projectId, repoName, packageName, version.name)
            val artifactPath = MavenUtil.extractPath(packageName) + "/${version.name}"
            val request = NodeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = artifactPath,
                operator = context.userId
            )
            nodeClient.deleteNode(request)
        }
    }

    /**
     * 删除单个节点
     */
    private fun deleteNode(context: ArtifactRemoveContext, forceDeleted: Boolean = false) {
        with(context) {
            val fullPath = artifactInfo.getArtifactFullPath()
            logger.info("Will prepare to delete file $fullPath in repo ${artifactInfo.getRepoIdentify()} ")
            // 如果删除单个文件不能删除metadata.xml文件, 如果文件删除了对应的校验文件也要删除
            if (fullPath.endsWith(MAVEN_METADATA_FILE_NAME) && !forceDeleted) {
                throw MavenRequestForbiddenException(
                    MavenMessageCode.MAVEN_REQUEST_FORBIDDEN, fullPath, artifactInfo.getRepoIdentify()
                )
            }
            val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
            if (node != null) {
                if (checksumType(node.fullPath) == null) {
                    deleteArtifactCheckSums(context, node)
                }
                val request = NodeDeleteRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    operator = userId
                )
                nodeClient.deleteNode(request)
            } else {
                // 抛异常改为error日志
                logger.error("Can not find node $fullPath in repo $artifactInfo")
            }
        }
    }

    /**
     * 删除构件的checksum文件
     */
    private fun deleteArtifactCheckSums(
        context: ArtifactContext,
        node: NodeDetail,
        typeArray: Array<HashType> = HashType.values()
    ) {
        with(node) {
            for (hashType in typeArray) {
                val fullPath = "${node.fullPath}.${hashType.ext}"
                nodeClient.getNodeDetail(projectId, repoName, fullPath).data?.let {
                    val request = NodeDeleteRequest(
                        projectId = projectId,
                        repoName = repoName,
                        fullPath = fullPath,
                        operator = context.userId
                    )
                    nodeClient.deleteNode(request)
                }
            }
        }
    }

    /**
     * 判断请求是否为checksum请求，并返回类型
     */
    private fun checksumType(artifactFullPath: String): HashType? {
        var type: HashType? = null
        for (hashType in HashType.values()) {
            val suffix = ".${hashType.ext}"
            if (artifactFullPath.endsWith(suffix)) {
                type = hashType
                break
            }
        }
        logger.info("The hashType of the file $artifactFullPath is $type")
        return type
    }

    override fun buildCacheNodeCreateRequest(context: ArtifactContext, artifactFile: ArtifactFile): NodeCreateRequest {
        val nodeCreateRequest = super.buildCacheNodeCreateRequest(context, artifactFile)
        val packaging = context.getStringAttribute(METADATA_KEY_PACKAGING)
        if (packaging != null) {
            val mavenGavc = (context.artifactInfo as MavenArtifactInfo).toMavenGAVC()
            val metadata = nodeCreateRequest.nodeMetadata?.toMutableList() ?: mutableListOf()
            createNodeMetaData(artifactFile).forEach {
                metadata.add(it)
            }
            metadata.add(MetadataModel(key = METADATA_KEY_PACKAGING, value = packaging))
            metadata.add(MetadataModel(key = METADATA_KEY_GROUP_ID, value = mavenGavc.groupId))
            metadata.add(MetadataModel(key = METADATA_KEY_ARTIFACT_ID, value = mavenGavc.artifactId))
            metadata.add(MetadataModel(key = METADATA_KEY_VERSION, value = mavenGavc.version))
            mavenGavc.classifier?.let { metadata.add(MetadataModel(key = METADATA_KEY_CLASSIFIER, value = it)) }
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

    private fun isNewerSnapshotTimestamp(context: ArtifactDownloadContext, artifactFile: ArtifactFile): Boolean {
        val localTimeStamp = context.getAttribute<String>(SNAPSHOT_TIMESTAMP) ?: return true
        val remoteTimestamp = MetadataXpp3Reader().read(artifactFile.getInputStream())
            ?.versioning?.snapshot?.timestamp?.replace(".", "")
        return remoteTimestamp != null && localTimeStamp < remoteTimestamp
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MavenRemoteRepository::class.java)
    }
}
