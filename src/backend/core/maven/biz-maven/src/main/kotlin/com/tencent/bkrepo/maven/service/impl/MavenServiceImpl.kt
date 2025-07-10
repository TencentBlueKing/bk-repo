/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.maven.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.PARAM_DOWNLOAD
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.artifact.view.ViewModelService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.artifact.MavenDeleteArtifactInfo
import com.tencent.bkrepo.maven.config.MavenProperties
import com.tencent.bkrepo.maven.constants.MAVEN_METADATA_FILE_NAME
import com.tencent.bkrepo.maven.constants.SNAPSHOT_SUFFIX
import com.tencent.bkrepo.maven.enum.HashType
import com.tencent.bkrepo.maven.enum.MavenMessageCode
import com.tencent.bkrepo.maven.enum.SnapshotBehaviorType
import com.tencent.bkrepo.maven.exception.MavenArtifactNotFoundException
import com.tencent.bkrepo.maven.exception.MavenBadRequestException
import com.tencent.bkrepo.maven.model.TMavenMetadataRecord
import com.tencent.bkrepo.maven.pojo.MavenGAVC
import com.tencent.bkrepo.maven.pojo.request.MavenJarSearchRequest
import com.tencent.bkrepo.maven.pojo.response.MavenJarInfoResponse
import com.tencent.bkrepo.maven.service.MavenMetadataService
import com.tencent.bkrepo.maven.service.MavenService
import com.tencent.bkrepo.maven.util.DigestUtils
import com.tencent.bkrepo.maven.util.MavenConfiguration.toMavenRepoConf
import com.tencent.bkrepo.maven.util.MavenGAVCUtils.mavenGAVC
import com.tencent.bkrepo.maven.util.MavenMetadataUtils.deleteVersioning
import com.tencent.bkrepo.maven.util.MavenStringUtils.formatSeparator
import com.tencent.bkrepo.maven.util.MavenStringUtils.parseMavenFileName
import com.tencent.bkrepo.maven.util.MavenUtil
import com.tencent.bkrepo.maven.util.MavenUtil.checksumType
import com.tencent.bkrepo.repository.pojo.list.HeaderItem
import com.tencent.bkrepo.repository.pojo.list.RowItem
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.NodeListViewItem
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import org.apache.commons.lang3.StringUtils
import org.apache.maven.artifact.repository.metadata.Snapshot
import org.apache.maven.artifact.repository.metadata.SnapshotVersion
import org.apache.maven.artifact.repository.metadata.Versioning
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.PatternSyntaxException

@Service
class MavenServiceImpl(
    private val nodeService: NodeService,
    private val packageService: PackageService,
    private val viewModelService: ViewModelService,
    private val mavenMetadataService: MavenMetadataService,
    private val mavenProperties: MavenProperties,
    private val storageManager: StorageManager,
) : ArtifactService(), MavenService {

    @Value("\${spring.application.name}")
    private var applicationName: String = "maven"

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    override fun deploy(
        mavenArtifactInfo: MavenArtifactInfo,
        file: ArtifactFile,
    ) {
        val context = ArtifactUploadContext(file)
        try {
            ArtifactContextHolder.getRepository().upload(context)
        } catch (e: PatternSyntaxException) {
            logger.warn(
                "Error [${e.message}] occurred during uploading ${mavenArtifactInfo.getArtifactFullPath()} " +
                    "in repo ${mavenArtifactInfo.getRepoIdentify()}"
            )
            throw MavenBadRequestException(
                MavenMessageCode.MAVEN_ARTIFACT_UPLOAD, mavenArtifactInfo.getArtifactFullPath()
            )
        }
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    override fun dependency(mavenArtifactInfo: MavenArtifactInfo) {
        // 为了兼容jfrog，当查询到目录时，会展示当前目录下所有子项，而不是直接报错
        with(mavenArtifactInfo) {
            val node = nodeService.getNodeDetail(mavenArtifactInfo)
            val download = HttpContextHolder.getRequest().getParameter(PARAM_DOWNLOAD)?.toBoolean() ?: false
            if (node != null) {
                if (node.folder && !download) {
                    logger.info("The folder: ${getArtifactFullPath()} will be displayed...")
                    renderListView(node, this)
                } else {
                    logger.info("The dependency file: ${getArtifactFullPath()} will be downloaded... ")
                    val context = ArtifactDownloadContext()
                    ArtifactContextHolder.getRepository().download(context)
                }
            } else {
                logger.info("The dependency file: ${getArtifactFullPath()} will be downloaded... ")
                val context = ArtifactDownloadContext()
                ArtifactContextHolder.getRepository().download(context)
            }
        }
    }

    /**
     * 当查询节点为目录时，将其子节点以页面形式展示
     */
    private fun renderListView(node: NodeDetail, artifactInfo: MavenArtifactInfo) {
        with(artifactInfo) {
            viewModelService.trailingSlash(applicationName)
            // listNodePage 接口没办法满足当前情况
            val nodeList = nodeService.listNode(
                this, NodeListOption(includeFolder = true, deep = false)
            )
            val currentPath = viewModelService.computeCurrentPath(node)
            val headerList = listOf(
                HeaderItem("Name"),
                HeaderItem("Created by"),
                HeaderItem("Last modified"),
                HeaderItem("Size"),
                HeaderItem("Sha256")
            )
            val itemList = nodeList.map { NodeListViewItem.from(it) }.sorted()
            val rowList = itemList.map {
                RowItem(listOf(it.name, it.createdBy, it.lastModified, it.size, it.sha256))
            }
            viewModelService.render(currentPath, headerList, rowList)
        }
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    override fun delete(mavenArtifactInfo: MavenDeleteArtifactInfo, packageKey: String, version: String?) {
        val context = ArtifactRemoveContext()
        ArtifactContextHolder.getRepository().remove(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    override fun deleteDependency(mavenArtifactInfo: MavenArtifactInfo) {
        val context = ArtifactRemoveContext()
        ArtifactContextHolder.getRepository().remove(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    override fun artifactDetail(mavenArtifactInfo: MavenArtifactInfo, packageKey: String, version: String?): Any? {
        val context = ArtifactQueryContext()
        return ArtifactContextHolder.getRepository().query(context)
    }

    @Principal(PrincipalType.ADMIN)
    override fun searchJar(request: MavenJarSearchRequest): MavenJarInfoResponse {
        with(request) {
            if (fileList.size > mavenProperties.maxLength) {
                throw BadRequestException(CommonMessageCode.REQUEST_CONTENT_INVALID)
            }
            val jarMap = mutableMapOf<String, List<MavenJarInfoResponse.JarInfo>>()
            for (fileName in fileList) {
                val jarList = mutableListOf<MavenJarInfoResponse.JarInfo>()
                val mavenVersion = parseMavenFileName(fileName) ?: continue
                logger.info("fileName $fileName, mavenVersion: $mavenVersion")
                val metadataList = mavenMetadataService.search(
                    mavenVersion.artifactId, mavenVersion.version, mavenVersion.packaging
                )
                for (metadata in metadataList) {
                    val fullPath = buildFullPath(metadata)
                    logger.info("mavenVersion: $mavenVersion, fullPath: $fullPath")
                    val node =
                        nodeService.getNodeDetail((ArtifactInfo(metadata.projectId, metadata.repoName, fullPath)))
                            ?: continue
                    jarList.add(
                        MavenJarInfoResponse.JarInfo(
                            projectId = node.projectId,
                            repoName = node.repoName,
                            fullPath = node.fullPath,
                            groupId = metadata.groupId,
                            artifactId = metadata.artifactId,
                            version = metadata.version,
                            createdDate = node.createdDate,
                            lastModifiedDate = node.lastModifiedDate,
                            md5 = node.md5,
                            sha256 = node.sha256
                        )
                    )
                }
                jarMap[fileName] = jarList
            }
            return MavenJarInfoResponse(jarMap)
        }
    }

    override fun remove(context: ArtifactRemoveContext, remote: Boolean) {
        when (context.artifactInfo) {
            is MavenDeleteArtifactInfo -> {
                deletePackage(
                    artifactInfo = context.artifactInfo as MavenDeleteArtifactInfo,
                    userId = context.userId,
                    storageCredentials = context.storageCredentials,
                    remote = remote
                )
            }

            else -> {
                val fullPath = context.artifactInfo.getArtifactFullPath()
                val nodeInfo = nodeService.getNodeDetail(context.artifactInfo)
                    ?: throw MavenArtifactNotFoundException(
                        MavenMessageCode.MAVEN_ARTIFACT_NOT_FOUND, fullPath, context.artifactInfo.getRepoIdentify()
                    )
                if (nodeInfo.folder) {
                    folderRemoveHandler(context, nodeInfo, remote)
                } else {
                    nodeRemoveHandler(context, nodeInfo, remote)
                }
            }
        }
    }


    /**
     * [artifactPath] maven-metadata.xml 父文件夹
     * 在指定构件路径下，服务生成 快照版本下的maven-metadata.xml,
     */
    override fun verifyMetadataContent(context: ArtifactContext, artifactPath: String?) {
        val fullPath = context.artifactInfo.getArtifactFullPath()
        logger.info(
            "Handling request to create maven-metadata.xml for $fullPath " +
                "and with special path $artifactPath in repo ${context.artifactInfo.getRepoIdentify()}"
        )
        val mavenGavc = fullPath.mavenGAVC()
        val repoConf = context.repositoryDetail.configuration.toMavenRepoConf()
        val records = mavenMetadataService.search(context.artifactInfo, mavenGavc)
        if (records.isEmpty()) return
        generateMetadata(repoConf.mavenSnapshotVersionBehavior, mavenGavc, records)?.let {
            reStoreMavenMetadataRelated(it, context, fullPath, artifactPath)
        }
    }


    /**
     * 生成对应checksum文件
     */
    override fun verifyPath(context: ArtifactContext, fullPath: String, hashType: HashType?) {
        logger.info(
            "Will go to update checkSum files for $fullPath " +
                "in repo ${context.artifactInfo.getRepoIdentify()}"
        )
        val node = nodeService.getNodeDetail(ArtifactInfo(context.projectId, context.repoName, fullPath)) ?: return
        val typeArray = if (hashType == null) {
            HashType.values()
        } else {
            arrayOf(hashType)
        }
        updateArtifactCheckSum(context, node, typeArray)
    }

    override fun createNodeMetaData(artifactFile: ArtifactFile): List<MetadataModel> {
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

    private fun updateMetadata(fullPath: String, metadataArtifact: ArtifactFile) {
        val uploadContext = ArtifactUploadContext(metadataArtifact)
        val metadataNode = buildNodeCreateRequest(uploadContext, fullPath)
        storageManager.storeArtifactFile(metadataNode, metadataArtifact, uploadContext.storageCredentials)
        metadataArtifact.delete()
        logger.info("Success to save $fullPath, size: ${metadataArtifact.getSize()}")
    }

    private fun buildNodeCreateRequest(context: ArtifactUploadContext, fullPath: String): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = context.repositoryDetail.projectId,
            repoName = context.repositoryDetail.name,
            folder = false,
            fullPath = fullPath,
            size = context.getArtifactFile().getSize(),
            sha256 = context.getArtifactSha256(),
            md5 = context.getArtifactMd5(),
            operator = context.userId,
            overwrite = true,
            nodeMetadata = createNodeMetaData(context.getArtifactFile())
        )
    }

    /**
     * 根据MavenMetadata拼接出文件fullpath
     */
    private fun buildFullPath(mavenMetadata: TMavenMetadataRecord): String {
        with(mavenMetadata) {
            val groupId = StringPool.SLASH + groupId.formatSeparator(StringPool.DOT, StringPool.SLASH)
            var list = if (timestamp.isNullOrEmpty()) {
                mutableListOf(artifactId, version)
            } else {
                mutableListOf(artifactId, version.removeSuffix(SNAPSHOT_SUFFIX), timestamp, buildNo.toString())
            }
            if (!classifier.isNullOrEmpty()) {
                list.add(classifier)
            }
            val fileName = "${StringUtils.join(list, '-')}.$extension"
            list = mutableListOf(groupId, artifactId, version, fileName)
            return StringUtils.join(list, '/')
        }
    }

    /**
     * 删除package
     */
    private fun deletePackage(
        artifactInfo: MavenDeleteArtifactInfo,
        userId: String,
        storageCredentials: StorageCredentials?,
        remote: Boolean = false,
    ) {
        with(artifactInfo) {
            logger.info("Will prepare to delete package [$packageName|$version] in repo ${getRepoIdentify()}")
            if (version.isBlank()) {
                packageService.listAllVersion(projectId, repoName, packageName, VersionListOption()).orEmpty().forEach {
                    removeVersion(artifactInfo, it, userId)
                }
                // 删除artifactId目录
                val url = MavenUtil.extractPath(packageName)
                logger.info("$url will be deleted in repo ${getRepoIdentify()}")
                val request = NodeDeleteRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = url,
                    operator = userId
                )
                nodeService.deleteNode(request)
            } else {
                val packageVersion = packageService.findVersionByName(projectId, repoName, packageName, version)
                    ?: throw VersionNotFoundException(version)
                removeVersion(artifactInfo, packageVersion, userId)
                // 需要更新对应的metadata.xml文件
                if (remote) return
                updatePackageMetadata(
                    artifactInfo = artifactInfo,
                    version = version,
                    storageCredentials = storageCredentials,
                    userId = userId
                )
            }
        }
    }

    /**
     * 删除目录以及目录下的文件
     */
    private fun folderRemoveHandler(context: ArtifactRemoveContext, node: NodeDetail, remote: Boolean = false) {
        logger.info("Will try to delete folder ${node.fullPath} in repo ${context.artifactInfo.getRepoIdentify()}")
        val option = NodeListOption(pageNumber = 0, pageSize = 10, includeFolder = true, sort = true)
        val nodes = nodeService.listNodePage(ArtifactInfo(context.projectId, context.repoName, node.fullPath), option)
        if (nodes.records.isEmpty()) {
            // 如果目录下没有任何节点，则删除当前目录并返回
            val request = NodeDeleteRequest(
                projectId = context.projectId,
                repoName = context.repoName,
                fullPath = node.fullPath,
                operator = context.userId
            )
            nodeService.deleteNode(request)
            return
        }
        // 如果当前目录下级节点包含目录，当前目录可能是artifactId所在目录
        val artifactInfo = if (nodes.records.first().folder) {
            // 判断当前目录是否是artifactId所在目录
            val packageKey = extractPackageKey(node.fullPath)
            packageService.findPackageByKey(context.projectId, context.repoName, packageKey)
                ?: throw MavenBadRequestException(MavenMessageCode.MAVEN_ARTIFACT_DELETE, node.fullPath)
            val url = MavenUtil.extractPath(packageKey) + "/$MAVEN_METADATA_FILE_NAME"
            MavenDeleteArtifactInfo(
                projectId = context.projectId,
                repoName = context.repoName,
                packageName = packageKey,
                version = StringPool.EMPTY,
                artifactUri = url
            )
        } else {
            // 下一级没有目录，当前目录就是版本或者当前目录是artifactId目录（没有实际版本）
            val fullPath = node.fullPath.trimEnd('/') + "/$MAVEN_METADATA_FILE_NAME"
            val mavenGAVC = fullPath.mavenGAVC()
            var packageKey = PackageKeys.ofGav(mavenGAVC.groupId, mavenGAVC.artifactId)
            val url = MavenUtil.extractPath(packageKey) + "/$MAVEN_METADATA_FILE_NAME"
            var version = mavenGAVC.version
            packageService.findVersionByName(
                projectId = context.projectId,
                repoName = context.repoName,
                packageKey = packageKey,
                versionName = mavenGAVC.version
            ) ?: run {
                // 当前目录是artifactId目录（没有实际版本）
                packageKey = extractPackageKey(node.fullPath)
                version = StringPool.EMPTY
            }
            MavenDeleteArtifactInfo(
                projectId = context.projectId,
                repoName = context.repoName,
                packageName = packageKey,
                version = version,
                artifactUri = url
            )
        }
        deletePackage(
            artifactInfo = artifactInfo,
            userId = context.userId,
            storageCredentials = context.storageCredentials,
            remote = remote
        )
    }

    /**
     * 从路径中提取出packageKey
     */
    private fun extractPackageKey(fullPath: String): String {
        val pathList = fullPath.trim('/').split("/")
        if (pathList.size <= 1) throw MavenBadRequestException(MavenMessageCode.MAVEN_ARTIFACT_DELETE, fullPath)
        val artifactId = pathList.last()
        val groupId = StringUtils.join(pathList.subList(0, pathList.size - 1), ".")
        return PackageKeys.ofGav(groupId, artifactId)
    }

    /**
     * 删除节点以及更新对应maven-metadata.xml文件
     */
    private fun nodeRemoveHandler(context: ArtifactRemoveContext, node: NodeDetail, remote: Boolean = false) {
        logger.info("Will try to delete node ${node.fullPath} in repo ${context.artifactInfo.getRepoIdentify()}")
        deleteNode(
            artifactInfo = context.artifactInfo,
            userId = context.userId
        )
        try {
            node.fullPath.mavenGAVC()
            if (remote) return
            // 更新对应的metadata文件
            verifyMetadataContent(context, node.fullPath)
            // 更新`/groupId/artifactId/maven-metadata.xml`文件
            updatePackageMetadata(
                artifactInfo = context.artifactInfo,
                version = node.name,
                storageCredentials = context.storageCredentials,
                userId = context.userId
            )
        } catch (ignore: IndexOutOfBoundsException) {
        }
    }

    /**
     * 删除[version] 对应的node节点也会一起删除
     */
    private fun removeVersion(artifactInfo: ArtifactInfo, version: PackageVersion, userId: String) {
        with(artifactInfo as MavenDeleteArtifactInfo) {
            logger.info(
                "Will delete package $packageName version ${version.name} in repo ${getRepoIdentify()}"
            )
            packageService.deleteVersion(projectId, repoName, packageName, version.name)
            val artifactPath = MavenUtil.extractPath(packageName) + "/${version.name}"
            // 需要删除对应的metadata表记录
            val (artifactId, groupId) = MavenUtil.extractGroupIdAndArtifactId(packageName)
            val mavenGAVC = MavenGAVC(groupId, artifactId, version.name, null)
            mavenMetadataService.delete(artifactInfo, null, mavenGAVC)
            val request = NodeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = artifactPath,
                operator = userId
            )
            nodeService.deleteNode(request)
        }
    }

    /**
     * 删除单个节点
     */
    private fun deleteNode(artifactInfo: ArtifactInfo, userId: String) {
        with(artifactInfo) {
            val fullPath = artifactInfo.getArtifactFullPath()
            logger.info("Will prepare to delete file $fullPath in repo ${artifactInfo.getRepoIdentify()} ")
            nodeService.getNodeDetail(artifactInfo)?.let {
                if (checksumType(it.fullPath) == null) {
                    deleteArtifactCheckSums(
                        projectId = projectId,
                        repoName = repoName,
                        userId = userId,
                        node = it
                    )
                }
                // 需要删除对应的metadata表记录
                mavenMetadataService.delete(artifactInfo, it)
                val request = NodeDeleteRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    operator = userId
                )
                nodeService.deleteNode(request)
            } ?: throw MavenArtifactNotFoundException(
                MavenMessageCode.MAVEN_ARTIFACT_NOT_FOUND, fullPath, "$projectId|$repoName"
            )
        }
    }

    /**
     *  更新`/groupId/artifactId/maven-metadata.xml`文件
     */
    private fun updatePackageMetadata(
        artifactInfo: ArtifactInfo,
        version: String,
        storageCredentials: StorageCredentials?,
        userId: String,
    ) {
        // reference https://maven.apache.org/guides/getting-started/#what-is-a-snapshot-version
        // 查找 `/groupId/artifactId/maven-metadata.xml`
        with(artifactInfo) {
            val node = nodeService.getNodeDetail(this) ?: return
            storageManager.loadFullArtifactInputStream(node, storageCredentials).use { artifactInputStream ->
                // 更新 `/groupId/artifactId/maven-metadata.xml`
                val mavenMetadata = MetadataXpp3Reader().read(artifactInputStream)
                mavenMetadata.versioning.versions.remove(version)
                if (mavenMetadata.versioning.versions.size == 0) {
                    nodeService.deleteNode(NodeDeleteRequest(projectId, repoName, node.fullPath, userId))
                    deleteArtifactCheckSums(
                        projectId = projectId,
                        repoName = repoName,
                        userId = userId,
                        node = node
                    )
                    return
                }
                mavenMetadata.deleteVersioning()
                storeMetadataXml(mavenMetadata, node)
            }
        }
    }


    /**
     * 删除构件的checksum文件
     */
    private fun deleteArtifactCheckSums(
        projectId: String,
        repoName: String,
        userId: String,
        node: NodeDetail,
        typeArray: Array<HashType> = HashType.values(),
    ) {
        for (hashType in typeArray) {
            val fullPath = "${node.fullPath}.${hashType.ext}"
            nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))?.let {
                val request = NodeDeleteRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    operator = userId
                )
                nodeService.deleteNode(request)
            }
        }
    }


    private fun storeMetadataXml(
        mavenMetadata: org.apache.maven.artifact.repository.metadata.Metadata,
        node: NodeDetail,
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
            updateMetadata("${node.path}/$MAVEN_METADATA_FILE_NAME", artifactFile)
            artifactFile.delete()
            updateMetadata("${node.path}/$MAVEN_METADATA_FILE_NAME.${HashType.MD5.ext}", metadataArtifactMd5)
            metadataArtifactMd5.delete()
            updateMetadata("${node.path}/$MAVEN_METADATA_FILE_NAME.${HashType.SHA1.ext}", metadataArtifactSha1)
            metadataArtifactSha1.delete()
        }
    }


    private fun generateMetadata(
        behaviorType: SnapshotBehaviorType?,
        mavenGavc: MavenGAVC,
        records: List<TMavenMetadataRecord>,
    ): org.apache.maven.artifact.repository.metadata.Metadata? {
        logger.info("Starting to create maven metadata and behavior type is $behaviorType...")
        val pom = records.firstOrNull { it.extension == "pom" } ?: return null
        val pomLastUpdated = (pom.timestamp ?: ZonedDateTime.now(ZoneId.of("UTC")).format(formatter))
            .replace(".", "")
        return when (behaviorType) {
            SnapshotBehaviorType.NON_UNIQUE -> {
                buildMetadata(mavenGavc, pomLastUpdated)
            }

            else -> {
                val buildNo = if (pom.buildNo == 0) 1 else pom.buildNo
                buildMetadata(mavenGavc, pomLastUpdated, buildNo, pom.timestamp, records)
            }
        }
    }


    /**
     * 生成maven-metadata.xml中的所有属性
     */
    private fun buildMetadata(
        mavenGavc: MavenGAVC,
        pomLastUpdated: String,
        buildNo: Int = 1,
        pomTimestamp: String? = null,
        records: List<TMavenMetadataRecord>? = null,
    ): org.apache.maven.artifact.repository.metadata.Metadata {
        return org.apache.maven.artifact.repository.metadata.Metadata().apply {
            modelVersion = "1.1.0"
            groupId = mavenGavc.groupId
            artifactId = mavenGavc.artifactId
            version = mavenGavc.version
            versioning = generateVersioning(mavenGavc, pomLastUpdated, buildNo, pomTimestamp, records)
        }
    }


    /**
     * 生成maven-metadata.xml中的versioning属性
     */
    private fun generateVersioning(
        mavenGavc: MavenGAVC,
        pomLastUpdated: String,
        buildNo: Int = 1,
        pomTimestamp: String? = null,
        records: List<TMavenMetadataRecord>? = null,
    ): Versioning {
        return Versioning().apply {
            snapshot = Snapshot().apply {
                pomTimestamp?.let {
                    timestamp = pomTimestamp
                }
                buildNumber = buildNo
            }
            lastUpdated = pomLastUpdated
            records?.let {
                snapshotVersions = generateSnapshotVersions(mavenGavc, records)
            }
        }
    }


    /**
     * 生成maven-metadata.xml中的snapshotVersions属性
     */
    private fun generateSnapshotVersions(
        mavenGavc: MavenGAVC,
        records: List<TMavenMetadataRecord>,
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
     * 存储新生成的maven-metadata.xml文件以及对应的md5，sha1等等
     */
    private fun reStoreMavenMetadataRelated(
        metadata: org.apache.maven.artifact.repository.metadata.Metadata,
        context: ArtifactContext,
        fullPath: String,
        artifactPath: String? = null,
    ) {
        logger.info("$fullPath file's metadata has been generated, now will store it")
        ByteArrayOutputStream().use { bos ->
            MetadataXpp3Writer().write(bos, metadata)
            val artifactFile = ArtifactFileFactory.build(bos.toByteArray().inputStream())
            try {
                val path = if (artifactPath == null) {
                    fullPath
                } else {
                    "${artifactPath.substringBeforeLast('/')}/$MAVEN_METADATA_FILE_NAME"
                }
                updateMetadata(path, artifactFile)
                verifyPath(context, path)
            } finally {
                artifactFile.delete()
            }
        }
    }


    /**
     * 上传后更新构件的checksum文件
     */
    private fun updateArtifactCheckSum(context: ArtifactContext, node: NodeDetail, typeArray: Array<HashType>) {
        logger.info(
            "Ready to generate checksum file on server side for ${node.fullPath} " +
                "in repo ${context.artifactInfo.getRepoIdentify()}"
        )
        for (hashType in typeArray) {
            val checksum = node.metadata[hashType.ext] as? String
            checksum?.let {
                generateChecksum(node, hashType, checksum, context.storageCredentials)
            }
        }
    }

    /**
     * 生成对应类型的checksum文件节点，并存储
     */
    private fun generateChecksum(
        node: NodeDetail,
        type: HashType,
        value: String,
        storageCredentials: StorageCredentials?,
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

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MavenServiceImpl::class.java)
        private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss")
    }
}
