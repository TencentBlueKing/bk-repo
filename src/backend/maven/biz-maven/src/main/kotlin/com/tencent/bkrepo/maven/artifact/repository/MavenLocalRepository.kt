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

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.maven.PACKAGE_SUFFIX_REGEX
import com.tencent.bkrepo.maven.SNAPSHOT_SUFFIX
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.constants.X_CHECKSUM_SHA1
import com.tencent.bkrepo.maven.enum.HashType
import com.tencent.bkrepo.maven.enum.MavenMessageCode
import com.tencent.bkrepo.maven.enum.SnapshotBehaviorType
import com.tencent.bkrepo.maven.exception.ConflictException
import com.tencent.bkrepo.maven.model.TMavenMetadataRecord
import com.tencent.bkrepo.maven.pojo.Basic
import com.tencent.bkrepo.maven.pojo.MavenArtifactVersionData
import com.tencent.bkrepo.maven.pojo.MavenGAVC
import com.tencent.bkrepo.maven.pojo.MavenMetadataSearchPojo
import com.tencent.bkrepo.maven.pojo.MavenRepoConf
import com.tencent.bkrepo.maven.pojo.response.MavenArtifactResponse
import com.tencent.bkrepo.maven.service.MavenMetadataService
import com.tencent.bkrepo.maven.util.DigestUtils
import com.tencent.bkrepo.maven.util.MavenConfiguration.toMavenRepoConf
import com.tencent.bkrepo.maven.util.MavenGAVCUtils.mavenGAVC
import com.tencent.bkrepo.maven.util.MavenGAVCUtils.toMavenGAVC
import com.tencent.bkrepo.maven.util.MavenMetadataUtils.deleteVersioning
import com.tencent.bkrepo.maven.util.MavenStringUtils.fileMimeType
import com.tencent.bkrepo.maven.util.MavenStringUtils.formatSeparator
import com.tencent.bkrepo.maven.util.MavenStringUtils.httpStatusCode
import com.tencent.bkrepo.maven.util.MavenStringUtils.isSnapshotNonUniqueUri
import com.tencent.bkrepo.maven.util.MavenStringUtils.isSnapshotUri
import com.tencent.bkrepo.maven.util.MavenStringUtils.resolverName
import com.tencent.bkrepo.maven.util.MavenUtil
import com.tencent.bkrepo.repository.api.StageClient
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import org.apache.commons.lang.StringUtils
import org.apache.maven.artifact.repository.metadata.Snapshot
import org.apache.maven.artifact.repository.metadata.SnapshotVersion
import org.apache.maven.artifact.repository.metadata.Versioning
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Component
class MavenLocalRepository(
    private val stageClient: StageClient,
    private val mavenMetadataService: MavenMetadataService
) : LocalRepository() {

    @Value("\${maven.domain:http://127.0.0.1:25803}")
    val mavenDomain = ""

    /**
     * 获取MAVEN节点创建请求
     */
    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val request = super.buildNodeCreateRequest(context)
        val md5 = context.getArtifactMd5()
        val sha1 = context.getArtifactSha1()
        val sha256 = context.getArtifactSha256()
        val sha512 = context.getArtifactFile().getInputStream().use {
            val bytes = it.readBytes()
            DigestUtils.sha512(bytes, 0, bytes.size)
        }
        return request.copy(
            overwrite = true,
            metadata = mutableMapOf(
                HashType.MD5.ext to md5,
                HashType.SHA1.ext to sha1,
                HashType.SHA256.ext to sha256,
                HashType.SHA512.ext to sha512
            )
        )
    }

    fun buildMavenArtifactNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val request = super.buildNodeCreateRequest(context)
        val mavenArtifactInfo = context.artifactInfo as MavenArtifactInfo
        // 此处对请求的fullPath 做处理
        val combineUrl = combineUrl(context, mavenArtifactInfo, request)
        val md5 = context.getArtifactMd5()
        val sha1 = context.getArtifactSha1()
        val sha256 = context.getArtifactSha256()
        val sha512 = context.getArtifactFile().getInputStream().use {
            val bytes = it.readBytes()
            DigestUtils.sha512(bytes, 0, bytes.size)
        }
        return request.copy(
            fullPath = combineUrl,
            overwrite = true,
            metadata = mutableMapOf(
                HashType.MD5.ext to md5,
                HashType.SHA1.ext to sha1,
                HashType.SHA256.ext to sha256,
                HashType.SHA512.ext to sha512
            )
        )
    }

    /**
     * 对请求参数和仓库SnapshotVersionBehavior的设置做判断，如果行为不一致生成新的url
     */
    private fun combineUrl(
        context: ArtifactUploadContext,
        mavenArtifactInfo: MavenArtifactInfo,
        request: NodeCreateRequest
    ): String {
        var result: String? = null
        if (mavenArtifactInfo.isSnapshot() &&
            getRepoConf(context).mavenSnapshotVersionBehavior == SnapshotBehaviorType.NON_UNIQUE
        ) {
            val name = request.fullPath.split("/").last()
            val nonUniqueName =
                name.resolverName(mavenArtifactInfo.artifactId, mavenArtifactInfo.versionId).combineToNonUnique()
            result = request.fullPath.replace(name, nonUniqueName)
        } else if (mavenArtifactInfo.isSnapshot() &&
            getRepoConf(context).mavenSnapshotVersionBehavior == SnapshotBehaviorType.UNIQUE
        ) {
            val name = request.fullPath.split("/").last()
            val mavenVersion = name.resolverName(mavenArtifactInfo.artifactId, mavenArtifactInfo.versionId)
            if (mavenVersion.timestamp.isNullOrBlank()) {
                // 查询最新记录
                mavenMetadataService.findAndModify(
                    MavenMetadataSearchPojo(
                        projectId = context.projectId,
                        repoName = context.repoName,
                        groupId = mavenArtifactInfo.groupId,
                        artifactId = mavenArtifactInfo.artifactId,
                        version = mavenArtifactInfo.versionId,
                        classifier = mavenVersion.classifier,
                        extension = mavenVersion.packaging
                    )
                ).apply {
                    mavenVersion.timestamp = this.timestamp
                    mavenVersion.buildNo = this.buildNo.toString()
                }
                val nonUniqueName = mavenVersion.combineToUnique()
                result = request.fullPath.replace(name, nonUniqueName)
            }
        }
        return result ?: mavenArtifactInfo.getArtifactFullPath()
    }

    /**
     *
     */
    private fun buildMavenArtifactNode(
        context: ArtifactUploadContext,
        packaging: String,
        mavenGavc: MavenGAVC
    ): NodeCreateRequest {
        val request = buildMavenArtifactNodeCreateRequest(context)
        val metadata = request.metadata as? MutableMap
        metadata?.set("packaging", packaging)
        metadata?.set("groupId", mavenGavc.groupId)
        metadata?.set("artifactId", mavenGavc.artifactId)
        metadata?.set("version", mavenGavc.version)
        mavenGavc.classifier?.let { metadata?.set("classifier", it) }
        return request
    }

    override fun onUploadBefore(context: ArtifactUploadContext) {
        super.onUploadBefore(context)
        val noOverwrite = HeaderUtils.getBooleanHeader("X-BKREPO-NO-OVERWRITE")
        if (noOverwrite) {
            with(context.artifactInfo) {
                val node = nodeClient.getNodeDetail(projectId, repoName, getArtifactFullPath()).data
                if (node != null) {
                    throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, getArtifactFullPath())
                }
            }
        }
        for (hashType in HashType.values()) {
            val artifactFullPath = context.artifactInfo.getArtifactFullPath()
            val suffix = ".${hashType.ext}"
            val isDigestFile = artifactFullPath.endsWith(suffix)
            if (isDigestFile) {
                // 校验hash
                validateDigest(hashType, context)
                return
            }
        }
    }

    private fun validateDigest(
        hashType: HashType,
        context: ArtifactUploadContext
    ) {
        with(context) {
            val suffix = ".${hashType.ext}"
            val artifactFilePath = artifactInfo.getArtifactFullPath().removeSuffix(suffix)
            // *-SNAPSHOT/maven-metadata.xml 交由服务生成后，lastUpdated会与客户端生成的不同，导致后续checksum校验不通过
            // *-SNAPSHOT/*-SNAPSHOT.jar 的构件上传后，如果仓库设置为`unique` 服务端生成时间戳，无法找到对应节点
            val repoConf = getRepoConf(context)
            if (artifactFilePath.isSnapshotUri() &&
                (artifactFilePath.endsWith("maven-metadata.xml") ||
                        repoConf.mavenSnapshotVersionBehavior == SnapshotBehaviorType.UNIQUE)
            ) {
                return
            }
            val node =
                nodeClient.getNodeDetail(projectId, repoName, artifactFilePath).data ?: throw NotFoundException(
                    ArtifactMessageCode.NODE_NOT_FOUND, artifactFilePath
                )
            val serverDigest = node.metadata[hashType.ext].toString()
            val clientDigest = MavenUtil.extractDigest(getArtifactFile().getInputStream())
            if (clientDigest != serverDigest) {
                throw ConflictException(MavenMessageCode.CHECKSUM_CONFLICT, clientDigest, serverDigest)
            }
        }
    }

    private fun getRepoConf(context: ArtifactContext): MavenRepoConf {
        val repositoryInfo = repositoryClient.getRepoInfo(context.projectId, context.repoName).data
            ?: throw ErrorCodeException(
                CommonMessageCode.RESOURCE_NOT_FOUND,
                "${context.projectId}/${context.repoName}"
            )
        val mavenConfiguration = repositoryInfo.configuration
        return mavenConfiguration.toMavenRepoConf()
    }

    override fun onUpload(context: ArtifactUploadContext) {
        val matcher = Pattern.compile(PACKAGE_SUFFIX_REGEX).matcher(context.artifactInfo.getArtifactFullPath())
        if (matcher.matches()) {
            var packaging = matcher.group(2)
            val fileSuffix = packaging
            if (packaging == "pom") {
                val mavenPomModel = context.getArtifactFile().getInputStream().use { MavenXpp3Reader().read(it) }
                if (StringUtils.isNotBlank(mavenPomModel.version) &&
                    mavenPomModel.packaging.equals("pom", ignoreCase = true)
                ) else {
                    packaging = mavenPomModel.packaging
                }
            }
            val isArtifact = (packaging == fileSuffix)
            val mavenGavc = (context.artifactInfo as MavenArtifactInfo).toMavenGAVC()
            val node = buildMavenArtifactNode(context, packaging, mavenGavc)
            storageManager.storeArtifactFile(node, context.getArtifactFile(), context.storageCredentials)
            if (isArtifact) createMavenVersion(context, mavenGavc)
            // 更新包各模块版本最新记录
            mavenMetadataService.update(node)
        } else {
            val artifactFullPath = context.artifactInfo.getArtifactFullPath()
            // -SNAPSHOT/** 路径下的构件和metadata.xml 文件的checksum 做拦截，
            // metadata.xml.* 改由系统生成
            // 构件名如果与仓库配置不符也改由系统生成
            if (artifactFullPath.isSnapshotUri() &&
                (matedataUploadHandler(artifactFullPath) || artifactUploadHandler(artifactFullPath, context))
            ) {
                return
            } else {
                super.onUpload(context)
            }
        }
    }

    private fun matedataUploadHandler(artifactFullPath: String): Boolean {
        for (hashType in HashType.values()) {
            val suffix = ".${hashType.ext}"
            val isDigestFile = artifactFullPath.endsWith(suffix)
            if (isDigestFile) {
                val artifactFilePath = artifactFullPath.removeSuffix(suffix)
                return artifactFilePath.endsWith("maven-metadata.xml")
            }
        }
        return false
    }

    /**
     * 当上传构件url 为 1.0-SNAPSHOT/xx-1.0-SNAPSHOT.jar
     * 仓库属性  SnapshotVersionBehavior == [SnapshotBehaviorType.UNIQUE]
     *
     */
    private fun artifactUploadHandler(artifactFullPath: String, context: ArtifactContext): Boolean {
        val repoConf = getRepoConf(context)
        return (
            repoConf.mavenSnapshotVersionBehavior == SnapshotBehaviorType.UNIQUE &&
                artifactFullPath.isSnapshotNonUniqueUri() &&
                checksumType(context) != null
            )
    }

    /**
     * 判断请求是否为checksum请求，并返回类型
     */
    private fun checksumType(context: ArtifactContext): HashType? {
        var type: HashType? = null
        val artifactFullPath = context.artifactInfo.getArtifactFullPath()
        for (hashType in HashType.values()) {
            val suffix = ".${hashType.ext}"
            if (artifactFullPath.endsWith(suffix)) type = hashType
        }
        return type
    }

    /**
     * 上传pom 和 jar 时返回文件上传成功信息
     */
    override fun onUploadSuccess(context: ArtifactUploadContext) {
        super.onUploadSuccess(context)
        val repoConf = getRepoConf(context)
        with(context) {
            val mimeType = artifactInfo.getArtifactFullPath().fileMimeType()
            if (mimeType != null) {
                val node =
                    nodeClient.getNodeDetail(projectId, repoName, artifactInfo.getArtifactFullPath()).data ?: return
                val uri = "$mavenDomain/$projectId/$repoName/${node.fullPath}"
                val mavenArtifactResponse = MavenArtifactResponse(
                    projectId = node.projectId,
                    repo = node.repoName,
                    created = node.createdDate,
                    createdBy = node.createdBy,
                    downloadUri = uri,
                    mimeType = mimeType,
                    size = node.size.toString(),
                    checksums = MavenArtifactResponse.Checksums(
                        sha1 = node.metadata["sha1"] as? String,
                        md5 = node.md5,
                        sha256 = node.sha256
                    ),
                    originalChecksums = MavenArtifactResponse.OriginalChecksums(node.sha256),
                    uri = uri
                )
                response.status = artifactInfo.getArtifactFullPath().httpStatusCode(repoConf)
                response.writer.println(mavenArtifactResponse.toJsonString())
                response.writer.flush()
            }
        }
    }

    override fun onUploadFinished(context: ArtifactUploadContext) {
        super.onUploadFinished(context)
        val repoConf = getRepoConf(context)
        val artifactFullPath = context.artifactInfo.getArtifactFullPath()
        if (artifactFullPath.isSnapshotUri() && repoConf.mavenSnapshotVersionBehavior != SnapshotBehaviorType.DEPLOYER) {
            // 生成`maven-metadata.xml`
            if (artifactFullPath.endsWith("maven-metadata.xml")) {
                verifyMetadataContent(context)
            } else if (artifactFullPath.isSnapshotNonUniqueUri() &&
                repoConf.mavenSnapshotVersionBehavior == SnapshotBehaviorType.UNIQUE &&
                checksumType(context) == null
            ) {
                // 处理maven2 *1.0-SNAPSHOT/1.0-SNAPSHOT.jar 格式构件
                verifyArtifact(context)
            }
        }
    }

    private fun verifyArtifact(context: ArtifactUploadContext) {
        // 生成.md5 和 .sha1
        val node = nodeClient.getNodeDetail(
            context.projectId,
            context.repoName,
            context.artifactInfo.getArtifactFullPath()
        ).data ?: return
        (node.metadata[HashType.MD5.ext] as? String)?.let {
            generateChecksum(node, HashType.MD5, it, context.storageCredentials)
        }
        (node.metadata[HashType.SHA1.ext] as? String)?.let {
            generateChecksum(node, HashType.SHA1, it, context.storageCredentials)
        }
    }

    private fun verifyPathWithHashType(context: ArtifactContext, hashType: HashType) {
        val node = nodeClient.getNodeDetail(
            context.projectId,
            context.repoName,
            context.artifactInfo.getArtifactFullPath().removeSuffix(".${hashType.ext}")
        ).data ?: return
        val checksum = node.metadata[hashType.ext] as? String
        if (checksum != null) {
            generateChecksum(node, hashType, checksum, context.storageCredentials)
        }
    }

    private fun verifyPath(context: ArtifactUploadContext) {
        val node = nodeClient.getNodeDetail(
            context.projectId,
            context.repoName,
            context.artifactInfo.getArtifactFullPath()
        ).data ?: return
        for (hashType in HashType.values()) {
            val checksum = node.metadata[hashType.ext] as? String
            checksum?.let {
                generateChecksum(node, hashType, checksum, context.storageCredentials)
            }
        }
    }

    /**
     * 服务生成 快照版本下的maven-metadata.xml
     */
    private fun verifyMetadataContent(context: ArtifactUploadContext) {
        val mavenGavc = context.artifactInfo.getArtifactFullPath().mavenGAVC()
        val repoConf = getRepoConf(context)
        val records = mavenMetadataService.search(context.artifactInfo as MavenArtifactInfo, mavenGavc)
        if (records.isEmpty()) return
        val mavenMetadata = generateMetadata(repoConf, mavenGavc, records)
        ByteArrayOutputStream().use { bos ->
            MetadataXpp3Writer().write(bos, mavenMetadata)
            val artifactFile = ArtifactFileFactory.build(bos.toByteArray().inputStream())
            try {
                updateMetadata(context.artifactInfo.getArtifactFullPath(), artifactFile)
                verifyPath(context)
            } finally {
                artifactFile.delete()
            }
        }
    }

    private fun generateChecksum(
        node: NodeDetail,
        type: HashType,
        value: String,
        storageCredentials: StorageCredentials?
    ) {
        val artifactFile = ArtifactFileFactory.build(value.byteInputStream())
        try {
            val nodeCreateRequest = NodeCreateRequest(
                projectId = node.projectId,
                repoName = node.repoName,
                fullPath = "${node.fullPath}.${type.ext}",
                folder = false,
                overwrite = true,
                size = artifactFile.getSize(),
                md5 = artifactFile.getFileMd5(),
                sha256 = artifactFile.getFileSha256(),
                operator = SecurityUtils.getUserId()
            )
            storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, storageCredentials)
        } finally {
            artifactFile.delete()
        }
    }

    private fun generateMetadata(
        repoConf: MavenRepoConf,
        mavenGavc: MavenGAVC,
        records: List<TMavenMetadataRecord>
    ): org.apache.maven.artifact.repository.metadata.Metadata {
        val pom = records.first { it.extension == "pom" }
        val pomLastUpdated = (pom.timestamp ?: ZonedDateTime.now(ZoneId.of("UTC")).format(formatter))
            .replace(".", "")
        return if (repoConf.mavenSnapshotVersionBehavior == SnapshotBehaviorType.NON_UNIQUE) {
            nonuniqueMetadata(mavenGavc, pomLastUpdated)
        } else {
            uniqueMetadata(mavenGavc, pomLastUpdated, pom, records)
        }
    }

    private fun nonuniqueMetadata(
        mavenGavc: MavenGAVC,
        pomLastUpdated: String
    ): org.apache.maven.artifact.repository.metadata.Metadata {
        return org.apache.maven.artifact.repository.metadata.Metadata().apply {
            modelVersion = "1.1.0"
            groupId = mavenGavc.groupId
            artifactId = mavenGavc.artifactId
            version = mavenGavc.version
            versioning = Versioning().apply {
                snapshot = Snapshot().apply {
                    buildNumber = 1
                }
                lastUpdated = pomLastUpdated
            }
        }
    }

    private fun uniqueMetadata(
        mavenGavc: MavenGAVC,
        pomLastUpdated: String,
        pom: TMavenMetadataRecord,
        records: List<TMavenMetadataRecord>
    ): org.apache.maven.artifact.repository.metadata.Metadata {
        return org.apache.maven.artifact.repository.metadata.Metadata().apply {
            modelVersion = "1.1.0"
            groupId = mavenGavc.groupId
            artifactId = mavenGavc.artifactId
            version = mavenGavc.version
            versioning = Versioning().apply {
                snapshot = Snapshot().apply {
                    timestamp = pom.timestamp
                    buildNumber = pom.buildNo ?: 1
                }
                lastUpdated = pomLastUpdated
                snapshotVersions = generateSnapshotVersions(mavenGavc, records)
            }
        }
    }

    private fun generateSnapshotVersions(
        mavenGavc: MavenGAVC,
        records: List<TMavenMetadataRecord>
    ): List<SnapshotVersion> {
        val snapshotVersionList = mutableListOf<SnapshotVersion>()
        for (record in records) {
            snapshotVersionList.add(
                SnapshotVersion().apply {
                    classifier = record.classifier
                    extension = record.extension
                    version = "${mavenGavc.version.removeSuffix(SNAPSHOT_SUFFIX)}-" +
                        "${record.timestamp}-${record.buildNo}"
                    updated = record.timestamp
                }
            )
        }
        return snapshotVersionList
    }

    /**
     * checksum 文件不存在时，系统生成
     */
    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        val checksumType = checksumType(context)
        var node = nodeClient.getNodeDetail(
            context.projectId,
            context.repoName,
            context.artifactInfo.getArtifactFullPath()
        ).data
        if (checksumType != null && node == null) {
            verifyPathWithHashType(context, checksumType)
        }
        with(context) {
            node = nodeClient.getNodeDetail(projectId, repoName, artifactInfo.getArtifactFullPath()).data
            node?.metadata?.get(HashType.SHA1.ext)?.let {
                response.addHeader(X_CHECKSUM_SHA1, it.toString())
            }
            val inputStream = storageManager.loadArtifactInputStream(node, storageCredentials) ?: return null
            val responseName = artifactInfo.getResponseName()
            return ArtifactResource(inputStream, responseName, node, ArtifactChannel.LOCAL, useDisposition)
        }
    }

    private fun createMavenVersion(context: ArtifactUploadContext, mavenGAVC: MavenGAVC) {
        val metadata = mutableMapOf(
            "groupId" to mavenGAVC.groupId,
            "artifactId" to mavenGAVC.artifactId,
            "version" to mavenGAVC.version
        )
        mavenGAVC.classifier?.let { metadata["classifier"] = it }
        packageClient.createVersion(
            PackageVersionCreateRequest(
                context.projectId,
                context.repoName,
                packageName = mavenGAVC.artifactId,
                packageKey = PackageKeys.ofGav(mavenGAVC.groupId, mavenGAVC.artifactId),
                packageType = PackageType.MAVEN,
                versionName = mavenGAVC.version,
                size = context.getArtifactFile().getSize(),
                artifactPath = context.artifactInfo.getArtifactFullPath(),
                overwrite = true,
                createdBy = context.userId,
                metadata = metadata
            )
        )
    }

    fun updateMetadata(fullPath: String, metadataArtifact: ArtifactFile) {
        val uploadContext = ArtifactUploadContext(metadataArtifact)
        val metadataNode = buildNodeCreateRequest(uploadContext).copy(fullPath = fullPath)
        storageManager.storeArtifactFile(metadataNode, metadataArtifact, uploadContext.storageCredentials)
        metadataArtifact.delete()
        logger.info("Success to save $fullPath, size: ${metadataArtifact.getSize()}")
    }

    override fun remove(context: ArtifactRemoveContext) {
        val packageKey = context.request.getParameter("packageKey")
        val version = context.request.getParameter("version")
        with(context.artifactInfo) {
            if (version.isNullOrBlank()) {
                packageClient.deletePackage(
                    projectId,
                    repoName,
                    packageKey
                )
            } else {
                packageClient.deleteVersion(
                    projectId,
                    repoName,
                    packageKey,
                    version
                )
            }
            logger.info("Success to delete $packageKey:$version")
        }
        executeDelete(context, packageKey, version)
    }

    private fun findMetadata(projectId: String, repoName: String, path: String): NodeDetail? {
        return nodeClient.getNodeDetail(projectId, repoName, "/$path/maven-metadata.xml").data
    }

    /**
     * 删除jar包 关联文件 并修改该包的版本管理文件
     */
    fun executeDelete(context: ArtifactRemoveContext, packageKey: String, version: String?) {
        val artifactId = packageKey.split(":").last()
        val groupId = packageKey.removePrefix("gav://").split(":")[0]
        val artifactPath = StringUtils.join(groupId.split("."), "/") + "/$artifactId"
        val versionPath = "$artifactPath/$version"
        // 删除 `/{groupId}/{artifact}/` 目录
        if (version.isNullOrBlank()) {
            nodeClient.deleteNode(
                NodeDeleteRequest(
                    context.projectId,
                    context.repoName,
                    "/$artifactPath",
                    ArtifactRemoveContext().userId
                )
            )
            return
        } else {
            nodeClient.deleteNode(
                NodeDeleteRequest(
                    context.projectId,
                    context.repoName,
                    "/$versionPath",
                    ArtifactRemoveContext().userId
                )
            )
            updateMetadata(context.projectId, context.repoName, artifactPath, version, context.userId)
            return
        }
    }

    /**
     * [path] /groupId/artifactId
     */
    private fun updateMetadata(
        projectId: String,
        repoName: String,
        path: String,
        deleteVersion: String,
        userId: String
    ) {
        // reference https://maven.apache.org/guides/getting-started/#what-is-a-snapshot-version
        // 查找 `/groupId/artifactId/maven-metadata.xml`
        val artifactMetadataNode = findMetadata(projectId, repoName, path)
        artifactMetadataNode?.let { node ->
            storageService.load(
                node.sha256!!,
                Range.full(node.size),
                ArtifactRemoveContext().storageCredentials
            ).use { artifactInputStream ->
                // 更新 `/groupId/artifactId/maven-metadata.xml`
                val mavenMetadata = MetadataXpp3Reader().read(artifactInputStream)
                mavenMetadata.versioning.versions.remove(deleteVersion)
                if (mavenMetadata.versioning.versions.size == 0) {
                    nodeClient.deleteNode(NodeDeleteRequest(projectId, repoName, artifactMetadataNode.fullPath, userId))
                    return
                }
                mavenMetadata.deleteVersioning()
                storeMetadataXml(mavenMetadata, node)
            }
        }
    }

    private fun storeMetadataXml(
        mavenMetadata: org.apache.maven.artifact.repository.metadata.Metadata,
        node: NodeDetail
    ) {
        ByteArrayOutputStream().use { metadata ->
            MetadataXpp3Writer().write(metadata, mavenMetadata)
            val artifactFile = ArtifactFileFactory.build(metadata.toByteArray().inputStream())
            val resultXmlMd5 = artifactFile.getFileMd5()
            val resultXmlSha1 = artifactFile.getFileSha1()
            val metadataArtifactMd5 = ByteArrayInputStream(resultXmlMd5.toByteArray()).use {
                ArtifactFileFactory.build(it)
            }
            val metadataArtifactSha1 = ByteArrayInputStream(resultXmlSha1.toByteArray()).use {
                ArtifactFileFactory.build(it)
            }
            updateMetadata("${node.path}/maven-metadata.xml", artifactFile)
            artifactFile.delete()
            updateMetadata("${node.path}/maven-metadata.xml.md5", metadataArtifactMd5)
            metadataArtifactMd5.delete()
            updateMetadata("${node.path}/maven-metadata.xml.sha1", metadataArtifactSha1)
            metadataArtifactSha1.delete()
        }
    }

    override fun query(context: ArtifactQueryContext): MavenArtifactVersionData? {
        val packageKey = context.request.getParameter("packageKey")
        val version = context.request.getParameter("version")
        val artifactId = packageKey.split(":").last()
        val groupId = packageKey.removePrefix("gav://").split(":")[0]
        val trueVersion = packageClient.findVersionByName(
            context.projectId,
            context.repoName,
            packageKey,
            version
        ).data ?: return null
        with(context.artifactInfo) {
            val jarNode = nodeClient.getNodeDetail(
                projectId, repoName, trueVersion.contentPath!!
            ).data ?: return null
            val stageTag = stageClient.query(projectId, repoName, packageKey, version).data
            val mavenArtifactMetadata = jarNode.metadata
            val packageVersion = packageClient.findVersionByName(
                projectId, repoName, packageKey, version
            ).data
            val count = packageVersion?.downloads ?: 0
            val mavenArtifactBasic = Basic(
                groupId,
                artifactId,
                version,
                jarNode.size, jarNode.fullPath,
                jarNode.createdBy, jarNode.createdDate,
                jarNode.lastModifiedBy, jarNode.lastModifiedDate,
                count,
                jarNode.sha256,
                jarNode.md5,
                stageTag,
                null
            )
            return MavenArtifactVersionData(mavenArtifactBasic, mavenArtifactMetadata)
        }
    }

    // maven 客户端下载统计
    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        with(context) {
            val fullPath = artifactInfo.getArtifactFullPath()
            val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
            return if (node != null && node.metadata["packaging"] != null) {
                val mavenGAVC = fullPath.mavenGAVC()
                val version = mavenGAVC.version
                val artifactId = mavenGAVC.artifactId
                val groupId = mavenGAVC.groupId.formatSeparator("/", ".")
                val packageKey = PackageKeys.ofGav(groupId, artifactId)
                PackageDownloadRecord(projectId, repoName, packageKey, version)
            } else {
                null
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MavenLocalRepository::class.java)
        private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss")
    }
}
