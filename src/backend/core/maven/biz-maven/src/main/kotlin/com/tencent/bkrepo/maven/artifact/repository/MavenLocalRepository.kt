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

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.event.node.NodeSeparationRecoveryEvent
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.metadata.service.packages.StageService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.config.MavenProperties
import com.tencent.bkrepo.maven.constants.FULL_PATH
import com.tencent.bkrepo.maven.constants.MAVEN_METADATA_FILE_NAME
import com.tencent.bkrepo.maven.constants.METADATA_KEY_ARTIFACT_ID
import com.tencent.bkrepo.maven.constants.METADATA_KEY_CLASSIFIER
import com.tencent.bkrepo.maven.constants.METADATA_KEY_GROUP_ID
import com.tencent.bkrepo.maven.constants.METADATA_KEY_PACKAGING
import com.tencent.bkrepo.maven.constants.METADATA_KEY_VERSION
import com.tencent.bkrepo.maven.constants.PACKAGE_KEY
import com.tencent.bkrepo.maven.constants.PACKAGE_SUFFIX_REGEX
import com.tencent.bkrepo.maven.constants.VERSION
import com.tencent.bkrepo.maven.constants.X_CHECKSUM_SHA1
import com.tencent.bkrepo.maven.enum.HashType
import com.tencent.bkrepo.maven.enum.MavenMessageCode
import com.tencent.bkrepo.maven.enum.SnapshotBehaviorType
import com.tencent.bkrepo.maven.exception.ConflictException
import com.tencent.bkrepo.maven.exception.MavenArtifactFormatException
import com.tencent.bkrepo.maven.exception.MavenArtifactNotFoundException
import com.tencent.bkrepo.maven.exception.MavenRequestForbiddenException
import com.tencent.bkrepo.maven.pojo.Basic
import com.tencent.bkrepo.maven.pojo.MavenArtifactVersionData
import com.tencent.bkrepo.maven.pojo.MavenGAVC
import com.tencent.bkrepo.maven.pojo.MavenMetadataSearchPojo
import com.tencent.bkrepo.maven.pojo.MavenRepoConf
import com.tencent.bkrepo.maven.pojo.response.MavenArtifactResponse
import com.tencent.bkrepo.maven.service.MavenMetadataService
import com.tencent.bkrepo.maven.service.MavenService
import com.tencent.bkrepo.maven.util.DigestUtils
import com.tencent.bkrepo.maven.util.MavenConfiguration.toMavenRepoConf
import com.tencent.bkrepo.maven.util.MavenConfiguration.versionBehaviorConflict
import com.tencent.bkrepo.maven.util.MavenGAVCUtils.mavenGAVC
import com.tencent.bkrepo.maven.util.MavenGAVCUtils.toMavenGAVC
import com.tencent.bkrepo.maven.util.MavenStringUtils.fileMimeType
import com.tencent.bkrepo.maven.util.MavenStringUtils.formatSeparator
import com.tencent.bkrepo.maven.util.MavenStringUtils.httpStatusCode
import com.tencent.bkrepo.maven.util.MavenStringUtils.isSnapshotNonUniqueUri
import com.tencent.bkrepo.maven.util.MavenStringUtils.isSnapshotUri
import com.tencent.bkrepo.maven.util.MavenStringUtils.resolverName
import com.tencent.bkrepo.maven.util.MavenUtil
import com.tencent.bkrepo.maven.util.MavenUtil.checksumType
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import org.apache.commons.lang3.StringUtils
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.util.regex.Pattern

@Component
class MavenLocalRepository(
    private val stageService: StageService,
    private val mavenMetadataService: MavenMetadataService,
    private val mavenProperties: MavenProperties,
    private val mavenService: MavenService,
) : LocalRepository() {

    @Value("\${maven.domain:http://127.0.0.1:25803}")
    val mavenDomain = ""

    /**
     * 获取MAVEN节点创建请求
     */
    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val request = super.buildNodeCreateRequest(context)
        return request.copy(
            overwrite = true,
            nodeMetadata = createNodeMetaData(context)
        )
    }

    fun buildMavenArtifactNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val request = super.buildNodeCreateRequest(context)
        // 此处对请求的fullPath 做处理
        val combineUrl = combineUrl(context, request.fullPath)
        logger.info(
            "Node store path is $combineUrl, and original path is ${context.artifactInfo.getArtifactFullPath()} " +
                "in repo ${context.artifactInfo.getRepoIdentify()}"
        )
        return request.copy(
            fullPath = combineUrl,
            overwrite = true,
            nodeMetadata = createNodeMetaData(context)
        )
    }

    private fun createNodeMetaData(context: ArtifactUploadContext): List<MetadataModel> {
        with(context) {
            val md5 = getArtifactMd5()
            val sha1 = getArtifactSha1()
            val sha256 = getArtifactSha256()
            val sha512 = getArtifactFile().getInputStream().use {
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
    }

    /**
     * 对请求参数和仓库SnapshotVersionBehavior的设置做判断，如果行为不一致生成新的url
     * 当[SnapshotBehaviorType.UNIQUE] 或 [SnapshotBehaviorType.NON_UNIQUE] 服务器才介入生成构件路径的过程
     */
    private fun combineUrl(
        context: ArtifactUploadContext,
        fullPath: String,
    ): String {
        val mavenArtifactInfo = context.artifactInfo as MavenArtifactInfo
        logger.info(
            "Try to combine the url for ${mavenArtifactInfo.getArtifactFullPath()} " +
                "in repo ${mavenArtifactInfo.getRepoIdentify()}, and isSnapshot ${mavenArtifactInfo.isSnapshot()}"
        )
        val mavenRepoConf = getRepoConf(context)
        val snapshotFlag = mavenArtifactInfo.isSnapshot()
        if (!snapshotFlag) {
            return mavenArtifactInfo.getArtifactFullPath()
        }
        val name = fullPath.split("/").last()
        val result = when (mavenRepoConf.mavenSnapshotVersionBehavior) {
            SnapshotBehaviorType.NON_UNIQUE -> {
                val nonUniqueName = name.resolverName(
                    mavenArtifactInfo.artifactId,
                    mavenArtifactInfo.versionId
                ).combineToNonUnique()
                fullPath.replace(name, nonUniqueName)
            }

            SnapshotBehaviorType.UNIQUE -> {
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
                        mavenVersion.buildNo = this.buildNo
                    }
                    val nonUniqueName = mavenVersion.combineToUnique()
                    fullPath.replace(name, nonUniqueName)
                } else {
                    null
                }
            }

            else -> null
        }
        return result ?: mavenArtifactInfo.getArtifactFullPath()
    }

    /**
     *
     */
    private fun buildMavenArtifactNode(
        context: ArtifactUploadContext,
        packaging: String,
        mavenGavc: MavenGAVC,
    ): NodeCreateRequest {
        val request = buildMavenArtifactNodeCreateRequest(context)
        val metadata = request.nodeMetadata as? MutableList
        metadata?.add(MetadataModel(key = METADATA_KEY_PACKAGING, value = packaging))
        metadata?.add(MetadataModel(key = METADATA_KEY_GROUP_ID, value = mavenGavc.groupId))
        metadata?.add(MetadataModel(key = METADATA_KEY_ARTIFACT_ID, value = mavenGavc.artifactId))
        metadata?.add(MetadataModel(key = METADATA_KEY_VERSION, value = mavenGavc.version))
        mavenGavc.classifier?.let { metadata?.add(MetadataModel(key = METADATA_KEY_CLASSIFIER, value = it)) }
        return request
    }

    override fun onUploadBefore(context: ArtifactUploadContext) {
        super.onUploadBefore(context)
        val noOverwrite = HeaderUtils.getBooleanHeader("X-BKREPO-NO-OVERWRITE")
        val path = context.artifactInfo.getArtifactFullPath()
        logger.info("The File $path does not want to be overwritten: $noOverwrite")
        if (noOverwrite) {
            // -SNAPSHOT/** 路径下的metadata.xml 文件不做判断
            if (!path.endsWith(MAVEN_METADATA_FILE_NAME)) {
                val node = nodeService.getNodeDetail(context.artifactInfo)
                if (node != null && checksumType(path) == null) {
                    val message = "The File $path already existed in the ${context.artifactInfo.getRepoIdentify()}, " +
                        "please check your overwrite configuration."
                    logger.warn(message)
                    throw MavenRequestForbiddenException(
                        MavenMessageCode.MAVEN_REQUEST_FORBIDDEN, path, context.artifactInfo.getRepoIdentify()
                    )
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
        context: ArtifactUploadContext,
    ) {
        with(context) {
            val suffix = ".${hashType.ext}"
            val artifactFilePath = artifactInfo.getArtifactFullPath().removeSuffix(suffix)
            // *-SNAPSHOT/maven-metadata.xml 交由服务生成后，lastUpdated会与客户端生成的不同，导致后续checksum校验不通过
            // *-SNAPSHOT/*-SNAPSHOT.jar 的构件上传后，如果仓库设置为`unique` 服务端生成时间戳，无法找到对应节点
            val repoConf = getRepoConf(context)
            if (artifactFilePath.isSnapshotUri() &&
                (
                    artifactFilePath.endsWith(MAVEN_METADATA_FILE_NAME) ||
                        repoConf.versionBehaviorConflict(artifactFilePath)
                    )
            ) {
                return
            }
            val node =
                nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, artifactFilePath))
                    ?: throw NotFoundException(ArtifactMessageCode.NODE_NOT_FOUND, artifactFilePath)
            val serverDigest = node.metadata[hashType.ext].toString()
            val clientDigest = MavenUtil.extractDigest(getArtifactFile().getInputStream())
            if (clientDigest != serverDigest) {
                throw ConflictException(MavenMessageCode.MAVEN_CHECKSUM_CONFLICT, clientDigest, serverDigest)
            }
        }
    }

    private fun getRepoConf(context: ArtifactContext): MavenRepoConf {
        return context.repositoryDetail.configuration.toMavenRepoConf()
    }

    override fun onUpload(context: ArtifactUploadContext) {
        val matcher = Pattern.compile(PACKAGE_SUFFIX_REGEX).matcher(context.artifactInfo.getArtifactFullPath())
        logger.info(
            "Handling request to upload file ${context.artifactInfo.getArtifactFullPath()} " +
                "in repo ${context.artifactInfo.getRepoIdentify()}"
        )
        if (matcher.matches()) {
            var packaging = matcher.group(2)
            logger.info("File's package is $packaging")

            val fileSuffix = packaging
            if (packaging == "pom") {
                val mavenPomModel = pomReader(context)
                val verNotBlank = StringUtils.isNotBlank(mavenPomModel.version)
                val isPom = mavenPomModel.packaging.equals("pom", ignoreCase = true)
                if (!verNotBlank || !isPom) {
                    packaging = mavenPomModel.packaging
                }
            }
            val isArtifact = (packaging == fileSuffix)
            logger.info("Current file is artifact: $isArtifact")
            val mavenGavc = (context.artifactInfo as MavenArtifactInfo).toMavenGAVC()
            val node = buildMavenArtifactNode(context, packaging, mavenGavc)
            storageManager.storeArtifactFile(
                request = node,
                artifactFile = context.getArtifactFile(),
                storageCredentials = context.storageCredentials
            )
            context.putAttribute(FULL_PATH, node.fullPath)
            if (isArtifact) createMavenVersion(context, mavenGavc, node.fullPath)
            // 更新包各模块版本最新记录
            logger.info("Prepare to create maven metadata....")
            try {
                mavenMetadataService.update(node)
            } catch (e: DuplicateKeyException) {
                logger.warn(
                    "DuplicateKeyException occurred during updating metadata for " +
                        "${context.artifactInfo.getArtifactFullPath()} " +
                        "in repo ${context.artifactInfo.getRepoIdentify()}"
                )
            }
        } else {
            val artifactFullPath = context.artifactInfo.getArtifactFullPath()
            val isSnapShotUri = artifactFullPath.isSnapshotUri()
            val metadataCheckSum = metadataUploadHandler(artifactFullPath, context)
            val artifactCheckSum = artifactUploadHandler(artifactFullPath, context)
            logger.info(
                "Handling request to upload unmatched file $artifactFullPath " +
                    "in repo ${context.artifactInfo.getRepoIdentify()}"
            )
            // -SNAPSHOT/** 路径下的构件和metadata.xml 文件的checksum 做拦截，
            // metadata.xml.* 改由系统生成
            // 构件名如果与仓库配置不符也改由系统生成
            if (isSnapShotUri && (metadataCheckSum || artifactCheckSum)) {
                logger.info(
                    "The unmatched file $artifactFullPath will be generated by server side " +
                        "in repo ${context.artifactInfo.getRepoIdentify()}"
                )
                return
            } else {
                super.onUpload(context)
            }
        }
    }

    private fun pomReader(context: ArtifactUploadContext): Model {
        return context.getArtifactFile().getInputStream().use {
            try {
                MavenXpp3Reader().read(it)
            } catch (e: Exception) {
                throw MavenArtifactFormatException(MavenMessageCode.MAVEN_ARTIFACT_FORMAT_ERROR, "pom")
            }
        }
    }

    private fun metadataUploadHandler(artifactFullPath: String, context: ArtifactContext): Boolean {
        val repoConf = getRepoConf(context)
        if (repoConf.mavenSnapshotVersionBehavior != SnapshotBehaviorType.DEPLOYER) {
            for (hashType in HashType.values()) {
                val suffix = ".${hashType.ext}"
                val isDigestFile = artifactFullPath.endsWith(suffix)
                if (isDigestFile) {
                    val artifactFilePath = artifactFullPath.removeSuffix(suffix)
                    return artifactFilePath.endsWith(MAVEN_METADATA_FILE_NAME)
                }
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
            checksumType(artifactFullPath) != null &&
                repoConf.versionBehaviorConflict(artifactFullPath)
            )
    }


    /**
     * 上传pom 和 jar 时返回文件上传成功信息
     */
    override fun onUploadSuccess(context: ArtifactUploadContext) {
        super.onUploadSuccess(context)
        val repoConf = getRepoConf(context)
        with(context) {
            val fullPath = context.getStringAttribute(FULL_PATH) ?: artifactInfo.getArtifactFullPath()
            val mimeType = fullPath.fileMimeType()
            if (mimeType != null) {
                val node =
                    nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath)) ?: return
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
                response.status = fullPath.httpStatusCode(repoConf)
                response.writer.println(mavenArtifactResponse.toJsonString())
                response.writer.flush()
            }
        }
    }

    override fun onUploadFinished(context: ArtifactUploadContext) {
        super.onUploadFinished(context)
        val repoConf = getRepoConf(context)
        val artifactFullPath = context.getStringAttribute(FULL_PATH) ?: context.artifactInfo.getArtifactFullPath()
        val isSnapshotUri = artifactFullPath.isSnapshotUri()
        val isSnapshotNonUniqueUri = artifactFullPath.isSnapshotNonUniqueUri()
        logger.info(
            "The file $artifactFullPath has been uploaded, isSnapshotUri $isSnapshotUri " +
                "the isSnapshotNonUniqueUri is $isSnapshotNonUniqueUri," +
                "the snapshot behavior type is ${repoConf.mavenSnapshotVersionBehavior}"
        )

        if (!isSnapshotUri || repoConf.mavenSnapshotVersionBehavior == SnapshotBehaviorType.DEPLOYER) {
            return
        }
        // 生成`maven-metadata.xml`
        if (artifactFullPath.endsWith(MAVEN_METADATA_FILE_NAME)) {
            mavenService.verifyMetadataContent(context)
            return
        }
        if (checksumType(artifactFullPath) == null) {
            // 处理maven2 *1.0-SNAPSHOT/1.0-SNAPSHOT.jar 格式构件
            // 对应 checksum 有客户端请求时再去生成，因为客户端 在上传时 不知道由服务器生成的 时间戳
            // 在.pom 上传完之后需要重新生成 maven-metadata.xml , 已记录由服务器生成的最新构件
            // 处理maven-metadata.xml文件上传顺序无法确定，导致metadata文件无法更新的问题
            mavenService.verifyMetadataContent(context, artifactFullPath)
            mavenService.verifyPath(context, artifactFullPath)
            return
        }
    }


    /**
     * checksum 文件不存在时，系统生成
     */
    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        with(context) {
            val originalFullPath = artifactInfo.getArtifactFullPath()
            val node = try {
                getNodeInfoForDownload(context)
            } catch (e: MavenArtifactNotFoundException) {
                sendSeparationRecoveryEvent(
                    artifactInfo.projectId, artifactInfo.repoName, originalFullPath, context.repo.type.name
                )
                throw e
            }
            if (node == null) {
                sendSeparationRecoveryEvent(
                    artifactInfo.projectId, artifactInfo.repoName, originalFullPath, context.repo.type.name
                )
            }
            node?.metadata?.get(HashType.SHA1.ext)?.let {
                response.addHeader(X_CHECKSUM_SHA1, it.toString())
            }
            // 制品下载拦截
            node?.let {
                downloadIntercept(context, it)
                packageVersion(node)?.let { packageVersion -> downloadIntercept(context, packageVersion) }
            }
            val inputStream = storageManager.loadArtifactInputStream(node, storageCredentials) ?: return null
            val responseName = artifactInfo.getResponseName()
            return ArtifactResource(inputStream, responseName, node, ArtifactChannel.LOCAL, useDisposition)
        }
    }

    /**
     * 针对两种路径获取对应节点信息
     */
    private fun getNodeInfoForDownload(context: ArtifactDownloadContext): NodeDetail? {
        with(context) {
            var fullPath = artifactInfo.getArtifactFullPath()
            val checksumType = checksumType(fullPath)
            logger.info("Will download node $fullPath in repo ${artifactInfo.getRepoIdentify()}")
            // 针对正常路径
            var node = findNode(context, fullPath, checksumType)
            if (node != null) return node
            // 剔除文件夹路径
            if (checksumType == null) {
                if (!fullPath.matches(Regex(PACKAGE_SUFFIX_REGEX))) {
                    throw MavenArtifactNotFoundException(
                        MavenMessageCode.MAVEN_ARTIFACT_NOT_FOUND, fullPath, artifactInfo.getRepoIdentify()
                    )
                }
            }
            // 剔除类似-20190917.073536-2.jar路径，实际不存在的
            if (!fullPath.isSnapshotNonUniqueUri()) {
                throw MavenArtifactNotFoundException(
                    MavenMessageCode.MAVEN_ARTIFACT_NOT_FOUND, fullPath, artifactInfo.getRepoIdentify()
                )
            }
            val mavenArtifactInfo = context.artifactInfo as MavenArtifactInfo
            try {
                mavenArtifactInfo.isSnapshot()
            } catch (e: Exception) {
                val maven = if (checksumType != null) {
                    fullPath.removeSuffix(".${checksumType.ext}").toMavenGAVC()
                } else {
                    fullPath.toMavenGAVC()
                }
                mavenArtifactInfo.artifactId = maven.artifactId
                mavenArtifactInfo.versionId = maven.version
                mavenArtifactInfo.groupId = maven.groupId
            }
            if (isUniqueAndSnapshot(context, mavenArtifactInfo)) {
                // 针对非正常路径: 获取后缀为-1.0.0-SNAPSHOT.jar， 实际存储后缀为-1.0.0-20190917.073536-2.jar
                fullPath = urlConvert(context, checksumType, mavenArtifactInfo) ?: return null
                logger.info(
                    "Will download node ${artifactInfo.getArtifactFullPath()} " +
                        "with the new path $fullPath in repo ${artifactInfo.getRepoIdentify()}"
                )
                node = findNode(context, fullPath, checksumType)
            }
            return node
        }
    }

    /**
     * 针对可能源文件存在，但是实际上对应checksum文件不存在需要处理
     */
    private fun findNode(
        context: ArtifactDownloadContext,
        fullPath: String,
        checksumType: HashType? = null,
    ): NodeDetail? {
        with(context) {
            artifactInfo.setArtifactMappingUri(fullPath)
            var node = ArtifactContextHolder.getNodeDetail(artifactInfo)
            if (node != null || checksumType == null) {
                return node
            }
            // 针对可能文件上传，但是对应checksum文件没有生成的情况
            logger.info(
                "Will try to get $fullPath after removing suffix ${checksumType.ext} " +
                    "in ${artifactInfo.getRepoIdentify()}"
            )
            val temPath = fullPath.removeSuffix(".${checksumType.ext}")
            artifactInfo.setArtifactMappingUri(temPath)
            node = ArtifactContextHolder.getNodeDetail(artifactInfo)
            // 源文件存在，但是对应checksum文件不存在，需要生成
            if (node != null) {
                mavenService.verifyPath(context, temPath, checksumType)
                artifactInfo.setArtifactMappingUri(fullPath)
                node = ArtifactContextHolder.getNodeDetail(artifactInfo)
            }
            return node
        }
    }

    /**
     * 判断是否是snapshot并且仓库是UNIQUE
     */
    private fun isUniqueAndSnapshot(context: ArtifactDownloadContext, mavenArtifactInfo: MavenArtifactInfo): Boolean {
        val mavenRepoConf = getRepoConf(context)
        val snapshotFlag = mavenArtifactInfo.isSnapshot()
        if (!snapshotFlag) {
            return false
        }
        if (mavenRepoConf.mavenSnapshotVersionBehavior != SnapshotBehaviorType.UNIQUE) {
            return false
        }
        return true
    }

    /**
     * 针对unique仓库，snapshot版本，直接下载，导致路径不存在的问题特殊处理
     * 源请求： jungle-udp-l5/1.0.0-SNAPSHOT/jungle-udp-l5-1.0.0-SNAPSHOT.jar
     * 实际存储路径： jungle-udp-l5/1.0.0-SNAPSHOT/jungle-udp-l5-1.0.0-20190917.073536-2.jar
     */
    private fun urlConvert(
        context: ArtifactDownloadContext,
        checksumType: HashType? = null,
        mavenArtifactInfo: MavenArtifactInfo,
    ): String? {
        val fullPath = if (checksumType == null) {
            mavenArtifactInfo.getArtifactFullPath()
        } else {
            mavenArtifactInfo.getArtifactFullPath().removeSuffix(".${checksumType.ext}")
        }
        val name = fullPath.split("/").last()
        logger.info("The name of fullPath $fullPath is $name in repo ${context.artifactInfo.getRepoIdentify()}")
        val path = getUniquePath(
            fullPath = fullPath,
            name = name,
            mavenArtifactInfo = mavenArtifactInfo,
            projectId = context.projectId,
            repoName = context.repoName
        ) ?: return null
        return if (checksumType == null) {
            path
        } else {
            path + ".${checksumType.ext}"
        }
    }

    /**
     * 将-1.0.0-SNAPSHOT.jar转换为1.0.0-20190917.073536-2.jar
     */
    private fun getUniquePath(
        fullPath: String,
        name: String,
        mavenArtifactInfo: MavenArtifactInfo,
        projectId: String,
        repoName: String,
    ): String? {
        val mavenVersion = name.resolverName(mavenArtifactInfo.artifactId, mavenArtifactInfo.versionId)
        val list = mavenMetadataService.search(
            MavenMetadataSearchPojo(
                projectId = projectId,
                repoName = repoName,
                groupId = mavenArtifactInfo.groupId,
                artifactId = mavenArtifactInfo.artifactId,
                version = mavenArtifactInfo.versionId,
                classifier = mavenVersion.classifier,
                extension = mavenVersion.packaging
            )
        )
        if (list.isNullOrEmpty()) return null
        list[0].apply {
            mavenVersion.timestamp = this.timestamp
            mavenVersion.buildNo = this.buildNo
        }
        val uniqueName = mavenVersion.combineToUnique()
        val nonUniqueName = mavenVersion.combineToNonUnique()
        // 针对非正常路径： 获取后缀为-1.0.0-SNAPSHOT.jar， 但是版本和versionId不一致的
        if (nonUniqueName != name) {
            throw MavenArtifactNotFoundException(
                MavenMessageCode.MAVEN_ARTIFACT_NOT_FOUND, fullPath, "$projectId|$repoName"
            )
        }
        return fullPath.replace(name, uniqueName)
    }

    private fun createMavenVersion(context: ArtifactUploadContext, mavenGAVC: MavenGAVC, fullPath: String) {
        val metadata = mutableMapOf(
            METADATA_KEY_GROUP_ID to mavenGAVC.groupId,
            METADATA_KEY_ARTIFACT_ID to mavenGAVC.artifactId,
            METADATA_KEY_VERSION to mavenGAVC.version
        )
        try {
            mavenGAVC.classifier?.let { metadata[METADATA_KEY_CLASSIFIER] = it }
            packageService.createPackageVersion(
                PackageVersionCreateRequest(
                    context.projectId,
                    context.repoName,
                    packageName = mavenGAVC.artifactId,
                    packageKey = PackageKeys.ofGav(mavenGAVC.groupId, mavenGAVC.artifactId),
                    packageType = PackageType.MAVEN,
                    versionName = mavenGAVC.version,
                    size = context.getArtifactFile().getSize(),
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
     * 删除文件，删除对应文件后还需要更新对应的maven-metadata.xml文件, 同时还需要删除对应的metadata记录
     */
    override fun remove(context: ArtifactRemoveContext) {
        mavenService.remove(context)
    }

    override fun query(context: ArtifactQueryContext): MavenArtifactVersionData? {
        val packageKey = context.request.getParameter(PACKAGE_KEY)
        val version = context.request.getParameter(VERSION)
        val artifactId = packageKey.split(StringPool.COLON).last()
        val groupId = packageKey.removePrefix("gav://").split(StringPool.COLON)[0]
        val trueVersion = packageService.findVersionByName(
            context.projectId,
            context.repoName,
            packageKey,
            version
        ) ?: return null
        with(context.artifactInfo) {
            val jarNode = nodeService.getNodeDetail(
                ArtifactInfo(projectId, repoName, trueVersion.contentPath!!)
            ) ?: return null
            val type = jarNode.nodeMetadata.find { it.key == METADATA_KEY_PACKAGING }?.value as String?
            val stageTag = stageService.query(projectId, repoName, packageKey, version)
            val packageVersion = packageService.findVersionByName(
                projectId, repoName, packageKey, version
            )
            val count = packageVersion?.downloads ?: 0
            val mavenArtifactBasic = Basic(
                groupId,
                artifactId,
                version,
                type,
                jarNode.size, jarNode.fullPath,
                jarNode.createdBy, jarNode.createdDate,
                jarNode.lastModifiedBy, jarNode.lastModifiedDate,
                count,
                jarNode.sha256,
                jarNode.md5,
                stageTag,
                null
            )
            return MavenArtifactVersionData(mavenArtifactBasic, packageVersion?.packageMetadata)
        }
    }

    // maven 客户端下载统计
    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource,
    ): PackageDownloadRecord? {
        with(context) {
            val fullPath = artifactInfo.getArtifactFullPath()
            val node = nodeService.getNodeDetail(artifactInfo)
            return if (node != null && node.metadata[METADATA_KEY_PACKAGING] != null) {
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

    private fun packageVersion(node: NodeDetail): PackageVersion? {
        val groupId = node.metadata[METADATA_KEY_GROUP_ID]?.toString()
        val artifactId = node.metadata[METADATA_KEY_ARTIFACT_ID]?.toString()
        val version = node.metadata[METADATA_KEY_VERSION]?.toString()
        return if (groupId != null && artifactId != null && version != null) {
            val packageKey = PackageKeys.ofGav(groupId, artifactId)
            packageService.findVersionByName(node.projectId, node.repoName, packageKey, version)
        } else {
            null
        }
    }

    private fun sendSeparationRecoveryEvent(
        projectId: String,
        repoName: String,
        fullPath: String,
        repoType: String,
    ) {
        if (!mavenProperties.autoRecovery) return
        if (mavenProperties.recoveryTopic.isNullOrEmpty()) return
        val event = buildNodeSeparationRecoveryEvent(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            repoType = repoType
        )
        messageSupplier.delegateToSupplier(
            data = event,
            topic = mavenProperties.recoveryTopic!!,
            key = event.getFullResourceKey(),
        )
    }

    private fun buildNodeSeparationRecoveryEvent(
        projectId: String,
        repoName: String,
        fullPath: String,
        repoType: String,
    ): NodeSeparationRecoveryEvent {
        return NodeSeparationRecoveryEvent(
            projectId = projectId,
            repoName = repoName,
            resourceKey = fullPath,
            userId = SecurityUtils.getUserId(),
            repoType = repoType
        )
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MavenLocalRepository::class.java)
    }
}
