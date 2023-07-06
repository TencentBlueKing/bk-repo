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

package com.tencent.bkrepo.oci.service.impl

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.StreamUtils.readText
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.constant.SOURCE_TYPE
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.CompositeConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.pojo.FileInfo
import com.tencent.bkrepo.oci.config.OciProperties
import com.tencent.bkrepo.oci.constant.DESCRIPTION
import com.tencent.bkrepo.oci.constant.DOCKER_IMAGE_MANIFEST_MEDIA_TYPE_V1
import com.tencent.bkrepo.oci.constant.DOWNLOADS
import com.tencent.bkrepo.oci.constant.LAST_MODIFIED_BY
import com.tencent.bkrepo.oci.constant.LAST_MODIFIED_DATE
import com.tencent.bkrepo.oci.constant.MANIFEST_DIGEST
import com.tencent.bkrepo.oci.constant.MD5
import com.tencent.bkrepo.oci.constant.NODE_FULL_PATH
import com.tencent.bkrepo.oci.constant.OCI_IMAGE_MANIFEST_MEDIA_TYPE
import com.tencent.bkrepo.oci.constant.OCI_NODE_FULL_PATH
import com.tencent.bkrepo.oci.constant.OCI_NODE_SIZE
import com.tencent.bkrepo.oci.constant.OCI_PACKAGE_NAME
import com.tencent.bkrepo.oci.constant.OciMessageCode
import com.tencent.bkrepo.oci.constant.PROXY_URL
import com.tencent.bkrepo.oci.constant.REPO_TYPE
import com.tencent.bkrepo.oci.dao.OciReplicationRecordDao
import com.tencent.bkrepo.oci.exception.OciBadRequestException
import com.tencent.bkrepo.oci.exception.OciFileNotFoundException
import com.tencent.bkrepo.oci.exception.OciVersionNotFoundException
import com.tencent.bkrepo.oci.extension.ImagePackageInfoPullExtension
import com.tencent.bkrepo.oci.extension.ImagePackagePullContext
import com.tencent.bkrepo.oci.model.Descriptor
import com.tencent.bkrepo.oci.model.ManifestSchema2
import com.tencent.bkrepo.oci.model.TOciReplicationRecord
import com.tencent.bkrepo.oci.pojo.artifact.OciArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.pojo.digest.OciDigest
import com.tencent.bkrepo.oci.pojo.node.NodeProperty
import com.tencent.bkrepo.oci.pojo.response.OciImage
import com.tencent.bkrepo.oci.pojo.response.OciImageResult
import com.tencent.bkrepo.oci.pojo.response.OciTag
import com.tencent.bkrepo.oci.pojo.response.OciTagResult
import com.tencent.bkrepo.oci.pojo.user.PackageVersionInfo
import com.tencent.bkrepo.oci.service.OciOperationService
import com.tencent.bkrepo.oci.util.ObjectBuildUtils
import com.tencent.bkrepo.oci.util.OciLocationUtils
import com.tencent.bkrepo.oci.util.OciResponseUtils
import com.tencent.bkrepo.oci.util.OciUtils
import com.tencent.bkrepo.repository.api.MetadataClient
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.PackageMetadataClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import com.tencent.bkrepo.repository.pojo.search.PackageQueryBuilder
import com.tencent.devops.plugin.api.PluginManager
import com.tencent.devops.plugin.api.applyExtension
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import java.nio.charset.Charset
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.servlet.http.HttpServletRequest

@Service
class OciOperationServiceImpl(
    private val nodeClient: NodeClient,
    private val metadataClient: MetadataClient,
    private val packageMetadataClient: PackageMetadataClient,
    private val packageClient: PackageClient,
    private val storageService: StorageService,
    private val storageManager: StorageManager,
    private val repositoryClient: RepositoryClient,
    private val ociProperties: OciProperties,
    private val ociReplicationRecordDao: OciReplicationRecordDao,
    private val pluginManager: PluginManager
    ) : OciOperationService {

    /**
     * 检查package 对应的version是否存在
     * 不存在则抛出异常OciFileNotFoundException
     */
    private fun checkVersionExist(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String
    ) {
        packageClient.findVersionByName(
            projectId = projectId,
            repoName = repoName,
            packageKey = packageKey,
            version = version
        ).data ?: throw OciVersionNotFoundException(
            OciMessageCode.OCI_VERSION_NOT_FOUND, "$packageKey/$version", "$projectId|$repoName"
        )
    }

    /**
     * 保存节点元数据
     */
    override fun saveMetaData(
        projectId: String,
        repoName: String,
        fullPath: String,
        metadata: MutableMap<String, Any>,
        userId: String
    ) {
        val metadataSaveRequest = ObjectBuildUtils.buildMetadataSaveRequest(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            metadata = metadata,
            userId = userId
        )
        metadataClient.saveMetadata(metadataSaveRequest)
    }

    /**
     * 需要将blob中相关metadata写进package version中
     */
    override fun updatePackageInfo(
        ociArtifactInfo: OciArtifactInfo,
        packageKey: String,
        appVersion: String?,
        description: String?
    ) {
        with(ociArtifactInfo) {
            val packageUpdateRequest = ObjectBuildUtils.buildPackageUpdateRequest(
                artifactInfo = this,
                name = packageName,
                appVersion = appVersion,
                description = description,
                packageKey = packageKey
            )
            packageClient.updatePackage(packageUpdateRequest)
        }
    }

    /**
     * 删除package
     */
    fun remove(userId: String, artifactInfo: OciArtifactInfo) {
        with(artifactInfo) {
            // 可能存在支持多种type
            val repoDetail = getRepositoryInfo(artifactInfo)
            val packageKey = PackageKeys.ofName(repoDetail.type.name.toLowerCase(), packageName)
            if (version.isNotBlank()) {
                packageClient.findVersionByName(
                    projectId,
                    repoName,
                    packageKey,
                    version
                ).data?.let {
                    removeVersion(
                        artifactInfo = this,
                        version = it.name,
                        userId = userId,
                        packageKey = packageKey
                    )
                } ?: throw VersionNotFoundException(version)
            } else {
                // 删除package目录
                deleteNode(projectId, repoName, "${StringPool.SLASH}$packageName", userId)
                //删除package下所有版本
                packageClient.deletePackage(
                    projectId,
                    repoName,
                    packageKey
                )
            }
        }
    }

    /**
     * 删除[version] 默认会删除对应的节点
     */
    private fun removeVersion(
        artifactInfo: OciArtifactInfo,
        version: String,
        userId: String,
        packageKey: String
    ) {
        with(artifactInfo) {
            // 删除对应版本下所有关联的节点，包含manifest以及blobs
            deleteVersionRelatedNodes(
                artifactInfo = artifactInfo,
                version = version,
                userId = userId
            )
            packageClient.deleteVersion(projectId, repoName, packageKey, version)
        }
    }


    /**
     * 删除对应版本下所有关联的节点，包含manifest以及blobs
     */
    private fun deleteVersionRelatedNodes(
        artifactInfo: OciArtifactInfo,
        version: String,
        userId: String,
    ) {
        with(artifactInfo) {
            val manifest = OciLocationUtils.buildManifestPath(packageName, version)
            val blobsFolder = OciLocationUtils.blobVersionFolderLocation(version, packageName)
                logger.info("Will delete blobsFolder [$blobsFolder] and manifest $manifest " +
                                "in package $packageName|$version in repo [$projectId/$repoName]")
            // 删除manifest
            deleteNode(projectId, repoName, manifest, userId)
            // 删除blobs
            deleteNode(projectId, repoName, blobsFolder, userId)
        }
    }


    private fun deleteNode(
        projectId: String,
        repoName: String,
        fullPath: String,
        userId: String
    ) {
        val request = NodeDeleteRequest(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            operator = userId
        )
        nodeClient.deleteNode(request)
    }

    /**
     * 查询仓库相关信息
     */
    private fun getRepositoryInfo(artifactInfo: OciArtifactInfo): RepositoryDetail {
        with(artifactInfo) {
            val result = repositoryClient.getRepoDetail(projectId, repoName, REPO_TYPE).data ?: run {
                ArtifactContextHolder.queryRepoDetailFormExtraRepoType(projectId, repoName)
            }
            return result
        }
    }

    override fun detailVersion(
        userId: String,
        artifactInfo: OciArtifactInfo,
        packageKey: String,
        version: String
    ): PackageVersionInfo {
        with(artifactInfo) {
            logger.info("Try to get detail of the [$packageKey/$version] in repo ${artifactInfo.getRepoIdentify()}")
            val repoDetail = getRepositoryInfo(artifactInfo)
            val name = PackageKeys.resolveName(repoDetail.type.name.toLowerCase(), packageKey)
            checkVersionExist(
                projectId = projectId,
                repoName = repoName,
                packageKey = packageKey,
                version = version
            )
            val nodeDetail = getImageNodeDetail(
                projectId = projectId,
                repoName = repoName,
                name = name,
                version = version
            ) ?: throw OciVersionNotFoundException(
                OciMessageCode.OCI_VERSION_NOT_FOUND, "$packageKey/$version", "$projectId|$repoName"
            )
            val packageVersion = packageClient.findVersionByName(projectId, repoName, packageKey, version).data!!
            val basicInfo = ObjectBuildUtils.buildBasicInfo(nodeDetail, packageVersion)
            return PackageVersionInfo(basicInfo, packageVersion.packageMetadata)
        }
    }

    /**
     * 获取node节点
     * 查不到抛出OciFileNotFoundException异常
     */
    private fun getImageNodeDetail(
        projectId: String,
        repoName: String,
        name: String,
        digestStr: String? = null,
        version: String = StringPool.EMPTY
    ): NodeDetail? {
        val ociArtifactInfo = if (digestStr == null) {
            // 返回manifest文件的节点信息
            OciManifestArtifactInfo(
                projectId = projectId,
                repoName = repoName,
                packageName = name,
                reference = version,
                isValidDigest = false,
                version = StringPool.EMPTY
            )
        } else {
            // 返回blob文件的节点信息
            OciBlobArtifactInfo(
                projectId = projectId,
                repoName = repoName,
                packageName = name,
                digest = digestStr,
                version = StringPool.EMPTY
            )
        }
        val fullPath = ociArtifactInfo.getArtifactFullPath()
        val nodeDetail = nodeClient.getNodeDetail(projectId, repoName, fullPath).data ?: run {
            val oldDockerFullPath = getDockerNode(ociArtifactInfo) ?: return@run null
            nodeClient.getNodeDetail(projectId, repoName, oldDockerFullPath).data ?: run {
                logger.warn("node [$fullPath] don't found.")
                null
            }
        }
        return nodeDetail
    }

    override fun deletePackage(userId: String, artifactInfo: OciArtifactInfo) {
        logger.info("Try to delete the package [${artifactInfo.packageName}] in repo ${artifactInfo.getRepoIdentify()}")
        remove(userId, artifactInfo)
    }

    override fun deleteVersion(userId: String, artifactInfo: OciArtifactInfo) {
        logger.info(
            "Try to delete the package [${artifactInfo.packageName}/${artifactInfo.version}] " +
                    "in repo ${artifactInfo.getRepoIdentify()}"
        )
        remove(userId, artifactInfo)
    }

    override fun getRegistryDomain(): String {
        return ociProperties.domain
    }

    /**
     * 构造节点创建请求
     */
    private fun buildNodeCreateRequest(
        ociArtifactInfo: OciArtifactInfo,
        artifactFile: ArtifactFile
    ): NodeCreateRequest {

        return ObjectBuildUtils.buildNodeCreateRequest(
            projectId = ociArtifactInfo.projectId,
            repoName = ociArtifactInfo.repoName,
            artifactFile = artifactFile,
            fullPath = ociArtifactInfo.getArtifactFullPath()
        )
    }

    /**
     * 保存文件内容(当使用追加上传时，文件已存储，只需存储节点信息)
     * 特殊：对于manifest文件，node存tag
     */
    override fun storeArtifact(
        ociArtifactInfo: OciArtifactInfo,
        artifactFile: ArtifactFile,
        storageCredentials: StorageCredentials?,
        fileInfo: FileInfo?,
        proxyUrl: String?
    ): NodeDetail? {
        val request = buildNodeCreateRequest(ociArtifactInfo, artifactFile)
        val nodeDetail = if (fileInfo != null) {
            val newNodeRequest = request.copy(
                size = fileInfo.size,
                md5 = fileInfo.md5,
                sha256 = fileInfo.sha256
            )
            createNode(newNodeRequest)
            null
        } else {
            storageManager.storeArtifactFile(request, artifactFile, storageCredentials)
        }
        proxyUrl?.let {
            saveMetaData(
                projectId = request.projectId,
                repoName = request.repoName,
                fullPath = request.fullPath,
                metadata = mutableMapOf(PROXY_URL to proxyUrl),
                userId = SecurityUtils.getUserId()
            )
        }
        return nodeDetail
    }

    /**
     * 当使用追加上传时，文件已存储，只需存储节点信息
     */
    override fun createNode(request: NodeCreateRequest): NodeDetail {
        try {
            return nodeClient.createNode(request).data!!
        } catch (exception: Exception) {
            // 异常往上抛
            throw exception
        }
    }

    /**
     * 更新整个blob相关信息,blob相关的mediatype，version等信息需要从manifest中获取
     */
    override fun updateOciInfo(
        ociArtifactInfo: OciManifestArtifactInfo,
        digest: OciDigest,
        nodeDetail: NodeDetail,
        storageCredentials: StorageCredentials?,
        sourceType: ArtifactChannel?
    ) {
        logger.info(
            "Will start to update oci info for ${ociArtifactInfo.getArtifactFullPath()} " +
                    "in repo ${ociArtifactInfo.getRepoIdentify()}"
        )

        val manifest = loadManifest(nodeDetail.sha256!!, nodeDetail.size, storageCredentials)
        // 将该版本对应的blob sha256放到manifest节点的元数据中
        val (mediaType, digestList) = if (manifest == null) {
            Pair(DOCKER_IMAGE_MANIFEST_MEDIA_TYPE_V1, null)
        } else {
            // 更新manifest文件的metadata
            val mediaTypeV2 = if (manifest.mediaType.isNullOrEmpty()) {
                HeaderUtils.getHeader(HttpHeaders.CONTENT_TYPE) ?: OCI_IMAGE_MANIFEST_MEDIA_TYPE
            } else {
                manifest.mediaType
            }
            Pair(mediaTypeV2, OciUtils.manifestIteratorDigest(manifest))
        }

        // 更新manifest节点元数据
        updateNodeMetaData(
            projectId = ociArtifactInfo.projectId,
            repoName = ociArtifactInfo.repoName,
            version = ociArtifactInfo.reference,
            fullPath = nodeDetail.fullPath,
            mediaType = mediaType!!,
            digestList = digestList,
            sourceType = sourceType
        )


        if (ociArtifactInfo.packageName.isEmpty()) return
        // 处理manifest中的blob数据
        syncBlobInfo(
            ociArtifactInfo = ociArtifactInfo,
            manifest = manifest,
            nodeDetail = nodeDetail,
            sourceType = sourceType
        )
    }


    private fun loadManifest(
        sha256: String,
        size: Long,
        storageCredentials: StorageCredentials?
    ): ManifestSchema2? {
        val manifestBytes = storageService.load(
            sha256,
            Range.full(size),
            storageCredentials
        )!!.readText()

        return try {
            OciUtils.stringToManifestV2(manifestBytes)
        } catch (e: OciBadRequestException) {
            null
        }
    }

    override fun createPackageForThirdPartyImage(
        ociArtifactInfo: OciManifestArtifactInfo,
        manifestPath: String,
    ): Boolean {
        with(ociArtifactInfo) {
            val repositoryDetail = repositoryClient.getRepoDetail(projectId, repoName).data ?: return false
            val nodeDetail = nodeClient.getNodeDetail(projectId, repoName, manifestPath).data ?: return false
            val manifest = loadManifest(
                nodeDetail.sha256!!, nodeDetail.size, repositoryDetail.storageCredentials
            ) ?: return false
            return syncBlobInfo(
                ociArtifactInfo = ociArtifactInfo,
                manifest = manifest,
                nodeDetail = nodeDetail,
                sourceType = ArtifactChannel.REPLICATION,
                userId = SYSTEM_USER
            )
        }
    }

    /**
     * 将部分信息存入节点metadata中
     */
    private fun updateNodeMetaData(
        projectId: String,
        repoName: String,
        version: String? = null,
        fullPath: String,
        mediaType: String,
        digestList: List<String>? = null,
        sourceType: ArtifactChannel? = null
    ) {
        // 将基础信息存储到metadata中
        val metadata = ObjectBuildUtils.buildMetadata(
            mediaType = mediaType,
            version = version,
            digestList = digestList,
            sourceType = sourceType
        )
        saveMetaData(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            metadata = metadata,
            userId = SecurityUtils.getUserId()
        )
    }


    /**
     * 同步blob层的数据和config里面的数据
     */
    private fun syncBlobInfo(
        ociArtifactInfo: OciManifestArtifactInfo,
        nodeDetail: NodeDetail,
        sourceType: ArtifactChannel? = null,
        manifest: ManifestSchema2? = null,
        userId: String = SecurityUtils.getUserId()
    ): Boolean {
        logger.info(
            "Will start to sync blobs and config info from manifest ${ociArtifactInfo.getArtifactFullPath()} " +
                "to blobs in repo ${ociArtifactInfo.getRepoIdentify()}."
        )
        // existFlag 判断manifest里的所有blob是否都已经创建节点
        // size 整个镜像blob汇总的大小
        val (existFlag, size) = manifestHandler(
            manifest, ociArtifactInfo, userId
        )
        // 如果当前镜像下的blob没有全部存储在制品库，则不生成版本，由定时任务去生成
        if (existFlag) {
            // 第三方同步的索引更新等所有文件全部上传完成后才去进行
            // 根据flag生成package信息以及package version信息
            doPackageOperations(
                manifestPath = nodeDetail.fullPath,
                ociArtifactInfo = ociArtifactInfo,
                manifestDigest = OciDigest.fromSha256(nodeDetail.sha256!!),
                size = size,
                sourceType = sourceType,
                userId = userId
            )
            return true
        } else {
            val query = Query.query(
                where(TOciReplicationRecord::projectId).isEqualTo(ociArtifactInfo.projectId)
                    .and(TOciReplicationRecord::repoName).isEqualTo(ociArtifactInfo.repoName)
                    .and(TOciReplicationRecord::packageName).isEqualTo(ociArtifactInfo.packageName)
                    .and(TOciReplicationRecord::packageVersion).isEqualTo(ociArtifactInfo.reference)
            )
            val update = Update().setOnInsert(TOciReplicationRecord::manifestPath.name, nodeDetail.fullPath)
            ociReplicationRecordDao.upsert(query, update)
            return false
        }
    }


    /**
     * 针对v2版本manifest文件做特殊处理
     */
    private fun manifestHandler(
        manifest: ManifestSchema2?,
        ociArtifactInfo: OciManifestArtifactInfo,
        userId: String = SecurityUtils.getUserId()
    ): Pair<Boolean, Long> {
        //当manifest为空时，可能是v1版本镜像,直接返回
        if (manifest == null) return Pair(true, 0)
        // 用于判断是否所有blob都以存在
        var existFlag = true
        // 统计所有mainfest中的文件size作为整个package version的size
        var size: Long = 0
        val descriptorList = OciUtils.manifestIterator(manifest)

        // 当同一版本覆盖上传时，先删除当前版本对应的blobs目录，然后再创建
        val blobsFolder = OciLocationUtils.blobVersionFolderLocation(
            ociArtifactInfo.reference, ociArtifactInfo.packageName
        )
        deleteNode(ociArtifactInfo.projectId, ociArtifactInfo.repoName, blobsFolder, userId)

        // 同步layer以及config层blob信息
        descriptorList.forEach {
            size += it.size
            existFlag = existFlag && doSyncBlob(it, ociArtifactInfo, userId)
            if (!existFlag) {
                // 第三方同步场景下，如果当前镜像下的blob没有全部存储在制品库，则不生成版本，由定时任务去生成
                return Pair(false, 0)
            }
        }
        return Pair(existFlag, size)
    }


    /**
     * 更新blobs的信息
     */
    private fun doSyncBlob(
        descriptor: Descriptor,
        ociArtifactInfo: OciManifestArtifactInfo,
        userId: String = SecurityUtils.getUserId()
    ): Boolean {
        with(ociArtifactInfo) {
            logger.info(
                "Handling sync blob digest [${descriptor.digest}] in repo ${ociArtifactInfo.getRepoIdentify()}"
            )
            if (!OciDigest.isValid(descriptor.digest)) {
                logger.info("Invalid blob digest [$descriptor]")
                return false
            }
            val blobDigest = OciDigest(descriptor.digest)
            val fullPath = OciLocationUtils.buildDigestBlobsPath(packageName, blobDigest)
            return createBlobNode(
                fullPath = fullPath,
                descriptor = descriptor,
                ociArtifactInfo = this,
                userId = userId
            )
        }
    }

    /**
     * 检查blob节点是否存在，如不存在，则创建
     */
    private fun createBlobNode(
        fullPath: String,
        descriptor: Descriptor,
        ociArtifactInfo: OciManifestArtifactInfo,
        userId: String = SecurityUtils.getUserId()
    ): Boolean {
        with(ociArtifactInfo) {
            // 并发情况下，版本目录下可能存在着非该版本的blob
            // 覆盖上传时会先删除原有目录，并发情况下可能导致blobs节点不存在
            val nodeProperty = nodeClient.getDeletedNodeDetail(
                projectId, repoName, fullPath
            ).data?.firstOrNull{ it.sha256 == descriptor.sha256 }?.let {
                NodeProperty(it.fullPath, it.md5, it.size)
            } ?: getNodeByDigest(projectId, repoName, descriptor.digest)

            if (nodeProperty.fullPath.isNullOrEmpty()) return false
            val newPath = OciLocationUtils.blobVersionPathLocation(reference, packageName, descriptor.filename)
            if (newPath != nodeProperty.fullPath) {
                // 创建新路径节点 /packageName/blobs/version/xxx
                val nodeCreateRequest = ObjectBuildUtils.buildNodeCreateRequest(
                    projectId = projectId,
                    repoName = repoName,
                    size = nodeProperty.size!!,
                    sha256 = descriptor.sha256,
                    fullPath = newPath,
                    md5 = nodeProperty.md5 ?: StringPool.UNKNOWN,
                    userId = userId
                )
                createNode(nodeCreateRequest)
            }
            // 删除临时存储路径节点 /packageName/blobs/xxx
            deleteNode(projectId, repoName, fullPath, userId)
            return true
        }
    }

    /**
     * 根据blob信息生成对应的package以及version信息
     */
    private fun doPackageOperations(
        manifestPath: String,
        ociArtifactInfo: OciManifestArtifactInfo,
        manifestDigest: OciDigest,
        size: Long,
        sourceType: ArtifactChannel? = null,
        userId: String = SecurityUtils.getUserId()
    ) {
        with(ociArtifactInfo) {
            logger.info("Will create package info for [$packageName/$version in repo ${getRepoIdentify()} ")
            // 针对支持多仓库类型，如docker和oci
            val repoType = repositoryClient.getRepoDetail(projectId, repoName).data!!.type.name
            val packageKey = PackageKeys.ofName(repoType.toLowerCase(), packageName)
            val metadata = mutableMapOf<String, Any>(MANIFEST_DIGEST to manifestDigest.toString())
                .apply {
                    sourceType?.let { this[SOURCE_TYPE] = sourceType }
                }
            val request = ObjectBuildUtils.buildPackageVersionCreateRequest(
                ociArtifactInfo = this,
                packageName = packageName,
                version = ociArtifactInfo.reference,
                size = size,
                manifestPath = manifestPath,
                repoType = repoType,
                userId = userId
            )
            packageClient.createVersion(request)
            savePackageMetaData(
                projectId = projectId,
                repoName = repoName,
                packageKey = packageKey,
                version = ociArtifactInfo.reference,
                metadata = metadata
            )
        }
    }

    /**
     * 保存package元数据，元数据存为系统元数据
     */
    fun savePackageMetaData(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        metadata: MutableMap<String, Any>
    ) {
        val metadataSaveRequest = ObjectBuildUtils.buildPackageMetadataSaveRequest(
            projectId = projectId,
            repoName = repoName,
            packageKey = packageKey,
            version = version,
            metadata = metadata,
            userId = SecurityUtils.getUserId()
        )
        packageMetadataClient.saveMetadata(metadataSaveRequest)
    }


    /**
     * 获取对应存储节点路径
     * 特殊：manifest文件按tag存储， 但是查询时存在tag/digest
     */
    override fun getNodeFullPath(artifactInfo: OciArtifactInfo): String? {
        if (artifactInfo is OciManifestArtifactInfo) {
            // 根据类型解析实际存储路径，manifest获取路径有tag/digest
            if (artifactInfo.isValidDigest) {
                return getNodeByDigest(
                    projectId = artifactInfo.projectId,
                    repoName = artifactInfo.repoName,
                    digestStr = artifactInfo.reference
                ).fullPath
            }
        }
        return artifactInfo.getArtifactFullPath()
    }

    /**
     * 根据sha256值获取对应的node fullpath
     */
    override fun getNodeByDigest(
        projectId: String,
        repoName: String,
        digestStr: String
    ): NodeProperty {
        val ociDigest = OciDigest(digestStr)
        val queryModel = NodeQueryBuilder()
            .select(NODE_FULL_PATH, MD5, OCI_NODE_SIZE)
            .projectId(projectId)
            .repoName(repoName)
            .sha256(ociDigest.getDigestHex())
            .sortByAsc(NODE_FULL_PATH)
        val result = nodeClient.search(queryModel.build()).data ?: run {
            logger.warn(
                "Could not find $digestStr " +
                    "in repo $projectId|$repoName"
            )
            return NodeProperty()
        }
        if (result.records.isEmpty()) return NodeProperty()
        return NodeProperty(
            fullPath = result.records[0][NODE_FULL_PATH] as String,
            md5 = result.records[0][MD5] as String?,
            size = result.records[0][OCI_NODE_SIZE] as Int?
        )
    }

    /**
     * 针对老的docker仓库的数据做兼容性处理
     * 老版数据node存储格式不一样：
     * 1 docker-local/nginx/latest 下存所有manifest和blobs
     * 2 docker-local/nginx/_uploads/ 临时存储上传的blobs，待manifest文件上传成功后移到到对应版本下，如docker-local/nginx/latest
     */
    override fun getDockerNode(artifactInfo: OciArtifactInfo): String? {
        if (artifactInfo is OciManifestArtifactInfo) {
            // 根据类型解析实际存储路径，manifest获取路径有tag/digest
            if (artifactInfo.isValidDigest)
                return getNodeByDigest(
                    projectId = artifactInfo.projectId,
                    repoName = artifactInfo.repoName,
                    digestStr = artifactInfo.reference
                ).fullPath
            return "/${artifactInfo.packageName}/${artifactInfo.reference}/manifest.json"
        }
        if (artifactInfo is OciBlobArtifactInfo) {
            val digestStr = artifactInfo.digest ?: StringPool.EMPTY
            return getNodeByDigest(
                projectId = artifactInfo.projectId,
                repoName = artifactInfo.repoName,
                digestStr = digestStr
            ).fullPath
        }
        return null
    }

    override fun getReturnDomain(request: HttpServletRequest): String {
        logger.info("oci ociProperties ,${ociProperties}")
        return OciResponseUtils.getResponseURI(
            request = request,
            enableHttps = ociProperties.https,
            domain = ociProperties.domain,
        ).toString()
    }

    override fun getManifest(artifactInfo: OciManifestArtifactInfo): String {
        val context = ArtifactQueryContext()
        try {
            val inputStream = ArtifactContextHolder.getRepository().query(context) ?: OciFileNotFoundException(
                OciMessageCode.OCI_FILE_NOT_FOUND,
                context.artifactInfo.getArtifactFullPath(),
                context.artifactInfo.getRepoIdentify()
            )
            return (inputStream as ArtifactInputStream).readBytes().toString(Charset.defaultCharset())
        } catch (e: Exception) {
            logger.warn(e.message.toString())
            throw OciFileNotFoundException(
                OciMessageCode.OCI_FILE_NOT_FOUND,
                context.artifactInfo.getArtifactFullPath(),
                context.artifactInfo.getRepoIdentify()
            )
        }
    }

    override fun getImageList(
        projectId: String,
        repoName: String,
        pageNumber: Int,
        pageSize: Int,
        name: String?
    ): OciImageResult {
        return getOciArtifactList(
            projectId = projectId,
            repoName = repoName,
            pageNumber = pageNumber,
            pageSize = pageSize,
            name = name
        )
    }

    fun getOciArtifactList(
        projectId: String,
        repoName: String,
        pageNumber: Int,
        pageSize: Int,
        name: String?
    ): OciImageResult {
        val queryModel = PackageQueryBuilder().select(
            OCI_PACKAGE_NAME,
            LAST_MODIFIED_BY,
            LAST_MODIFIED_DATE,
            DESCRIPTION,
            DOWNLOADS
        ).projectId(projectId).repoName(repoName).sortByAsc(OCI_NODE_FULL_PATH)
            .page(pageNumber, pageSize)
        name?.let {
            queryModel.name("*$name*", OperationType.MATCH)
        }
        val result = packageClient.searchPackage(queryModel.build()).data ?: run {
            logger.warn("find repo list failed: [$projectId, $repoName] ")
            return OciImageResult(0, emptyList())
        }
        val data = mutableListOf<OciImage>()
        result.records.forEach {
            val imageName = it[OCI_PACKAGE_NAME] as String
            val lastModifiedBy = it[LAST_MODIFIED_BY] as String
            val lastModifiedDate = it[LAST_MODIFIED_DATE] as Long
            val downLoadCount = it[DOWNLOADS] as Int
            val description = it[DESCRIPTION] as String? ?: StringPool.EMPTY
            val localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastModifiedDate), ZoneId.systemDefault())
            data.add(
                OciImage(
                    name = imageName,
                    lastModifiedBy = lastModifiedBy,
                    lastModifiedDate = localDateTime.toString(),
                    downloadCount = downLoadCount.toLong(),
                    logoUrl = StringPool.EMPTY,
                    description = description
                )
            )
        }
        return OciImageResult(result.totalRecords, data)
    }

    override fun getRepoTag(
        projectId: String,
        repoName: String,
        pageNumber: Int,
        packageName: String,
        pageSize: Int,
        tag: String?
    ): OciTagResult {
        val artifactInfo = OciArtifactInfo(projectId, repoName, StringPool.EMPTY, StringPool.EMPTY)
        val repoDetail = getRepositoryInfo(artifactInfo)
        val packageKey = PackageKeys.ofName(repoDetail.type.name.toLowerCase(), packageName)
        val result = packageClient.listVersionPage(
            projectId,
            repoName,
            packageKey,
            VersionListOption(pageNumber, pageSize, tag, null)
        ).data ?: return OciTagResult(0, emptyList())
        val data = mutableListOf<OciTag>()
        result.records.forEach {
            val name = it.name
            val stageTag = buildString(it.stageTag)
            val size = it.size
            val lastModifiedBy = it.lastModifiedBy
            val lastModifiedDate = it.lastModifiedDate.toString()
            val downLoadCount = it.downloads
            val registryUrl = "${ociProperties.domain}/$projectId/$repoName/$packageName:$name"
            data.add(
                OciTag(name, stageTag, size, lastModifiedBy, lastModifiedDate, downLoadCount, registryUrl)
            )
        }
        return OciTagResult(result.totalRecords, data)
    }

    override fun getPackagesFromThirdPartyRepo(projectId: String, repoName: String) {
        val repositoryDetail = repositoryClient.getRepoDetail(projectId, repoName).data
            ?: throw RepoNotFoundException("$projectId|$repoName")
        buildImagePackagePullContext(projectId, repoName, repositoryDetail.configuration).forEach {
            pluginManager.applyExtension<ImagePackageInfoPullExtension> {
                queryAndCreateDockerPackageInfo(it)
            }
        }
    }

    override fun refreshFullPathOfBlob(projectId: String, repoName: String, userId: String) {
        val repositoryDetail = repositoryClient.getRepoDetail(projectId, repoName).data
            ?: throw RepoNotFoundException("$projectId|$repoName")
        val packageList = packageClient.listAllPackageNames(projectId, repoName).data ?: return
        packageList.forEach { pName ->
            packageClient.listAllVersion(projectId, repoName, pName).data.orEmpty().forEach { pVersion ->
                val packageName = PackageKeys.resolveName(repositoryDetail.type.name.toLowerCase(), pName)
                val manifestPath = OciLocationUtils.buildManifestPath(packageName, pVersion.name)
                logger.info("Manifest $manifestPath will be refreshed")
                val manifestNode = nodeClient.getNodeDetail(projectId, repoName, manifestPath).data ?: return
                val manifest = loadManifest(manifestNode.sha256!!, manifestNode.size, repositoryDetail.storageCredentials)
                    ?: run {
                        logger.warn("The content of manifest.json $manifestPath is null, check the mediaType.")
                        return
                    }
                val ociArtifactInfo = OciManifestArtifactInfo(
                    projectId = projectId,
                    repoName = repoName,
                    packageName = packageName,
                    version = pVersion.name,
                    reference = pVersion.name,
                    isValidDigest = false
                )
                OciUtils.manifestIterator(manifest).forEach {
                    doSyncBlob(it, ociArtifactInfo, userId)
                }
            }
        }
    }

    private fun buildImagePackagePullContext(
        projectId: String,
        repoName: String,
        config: RepositoryConfiguration
    ): List<ImagePackagePullContext> {
        val result = mutableListOf<ImagePackagePullContext>()
        when (config) {
            is RemoteConfiguration -> {
                try {
                    val remoteUrl = UrlFormatter.addProtocol(config.url)
                    result.add(ImagePackagePullContext(
                        projectId = projectId,
                        repoName = repoName,
                        remoteUrl = remoteUrl,
                        userName = config.credentials.username,
                        password = config.credentials.password
                    ))
                } catch (e: Exception) {
                    logger.warn("illegal remote url ${config.url} for repo $projectId|$repoName")
                }
            }
            is CompositeConfiguration -> {
                config.proxy.channelList.forEach {
                    try {
                        val remoteUrl = UrlFormatter.addProtocol(it.url)
                        result.add(ImagePackagePullContext(
                            projectId = projectId,
                            repoName = repoName,
                            remoteUrl = remoteUrl,
                            userName = it.username,
                            password = it.password
                        ))
                    } catch (e: Exception) {
                        logger.warn("illegal proxy url ${it.url} for repo $projectId|$repoName")
                    }
                }
            }
            else -> throw UnsupportedOperationException()
        }
        return result
    }

    private fun buildString(stageTag: List<String>): String {
        if (stageTag.isEmpty()) return StringPool.EMPTY
        return StringUtils.join(stageTag.toTypedArray()).toString()
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(OciOperationServiceImpl::class.java)
    }
}
