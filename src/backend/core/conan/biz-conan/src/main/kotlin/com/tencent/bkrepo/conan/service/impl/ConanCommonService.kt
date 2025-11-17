/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.conan.service.impl

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.conan.config.ConanProperties
import com.tencent.bkrepo.conan.constant.CONANS_URL_TAG
import com.tencent.bkrepo.conan.constant.ConanMessageCode
import com.tencent.bkrepo.conan.constant.DEFAULT_REVISION_V1
import com.tencent.bkrepo.conan.constant.MD5
import com.tencent.bkrepo.conan.constant.UPLOAD_URL_PREFIX
import com.tencent.bkrepo.conan.constant.URL
import com.tencent.bkrepo.conan.exception.ConanFileNotFoundException
import com.tencent.bkrepo.conan.exception.ConanRecipeNotFoundException
import com.tencent.bkrepo.conan.pojo.ConanFileReference
import com.tencent.bkrepo.conan.pojo.ConanInfo
import com.tencent.bkrepo.conan.pojo.IndexInfo
import com.tencent.bkrepo.conan.pojo.PackageReference
import com.tencent.bkrepo.conan.pojo.RevisionInfo
import com.tencent.bkrepo.conan.utils.ConanInfoLoadUtil
import com.tencent.bkrepo.conan.utils.ConanPathUtils
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildConanFileName
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildExportFolderPath
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildPackageReference
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildPackageRevisionFolderPath
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildRevisionPath
import com.tencent.bkrepo.conan.utils.ConanPathUtils.getPackageConanInfoFile
import com.tencent.bkrepo.conan.utils.ConanPathUtils.getPackageRevisionsFile
import com.tencent.bkrepo.conan.utils.ConanPathUtils.getRecipeRevisionsFile
import com.tencent.bkrepo.conan.utils.ConanPathUtils.joinString
import com.tencent.bkrepo.conan.utils.TimeFormatUtil.convertToUtcTime
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import java.time.LocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ConanCommonService(
    private val properties: ConanProperties
) {
    @Autowired
    lateinit var nodeService: NodeService

    @Autowired
    lateinit var storageManager: StorageManager

    @Autowired
    lateinit var repositoryService: RepositoryService

    @Autowired
    lateinit var lockOperation: LockOperation

    /**
     * 获取指定的文件以及子文件的下载地址
     * 返回{"filepath": "http://..."}
     */
    fun getDownloadConanFileUrls(
        projectId: String,
        repoName: String,
        conanFileReference: ConanFileReference,
        subFileset: List<String> = emptyList()
    ): Map<String, String> {
        val latestRef = getLatestRef(projectId, repoName, conanFileReference)
        val path = buildExportFolderPath(latestRef)
        // TODO 重复代码抽离
        val result = mutableMapOf<String, String>()
        val tempMap = getDownloadPath(projectId, repoName, path, subFileset)
        val prefix = getRequestUrlPrefix(conanFileReference)
        tempMap.forEach { (t, u) -> result[t] = joinString(prefix, u) }
        return result
    }

    /**
     * 获取指定package下的文件以及子文件的下载地址
     * 返回{"filepath": "http://..."}
     */
    fun getPackageDownloadUrls(
        projectId: String,
        repoName: String,
        packageReference: PackageReference,
        subFileset: List<String> = emptyList()
    ): Map<String, String> {
        val latestRef = getLatestPackageRef(projectId, repoName, packageReference)
        val path = buildPackageRevisionFolderPath(latestRef)
        val result = mutableMapOf<String, String>()
        val tempMap = getDownloadPath(projectId, repoName, path, subFileset)
        val prefix = getRequestUrlPrefix(packageReference.conRef)
        tempMap.forEach { (t, u) -> result[t] = joinString(prefix, u) }
        return result
    }

    /**
     * 获取所有文件以及对应md5
     * 返回{"filepath": "md5值}
     */
    fun getRecipeSnapshot(
        projectId: String,
        repoName: String,
        conanFileReference: ConanFileReference,
        subFileset: List<String> = emptyList()
    ): Map<String, String> {
        val latestRef = getLatestRef(projectId, repoName, conanFileReference)
        val path = buildExportFolderPath(latestRef)
        return getSnapshot(projectId, repoName, path, subFileset)
    }

    /**
     * 获取package下所有文件以及对应md5
     * 返回{"filepath": "md5值}
     */
    fun getPackageSnapshot(
        projectId: String,
        repoName: String,
        packageReference: PackageReference,
        subFileset: List<String> = emptyList()
    ): Map<String, String> {
        val latestRef = getLatestPackageRef(projectId, repoName, packageReference)
        val path = buildPackageRevisionFolderPath(latestRef)
        return getSnapshot(projectId, repoName, path, subFileset)
    }

    /**
     * 查询目录下的所有文件，并返回
     * 返回值： key/value： 路径/md5
     */
    fun getSnapshot(
        projectId: String,
        repoName: String,
        path: String,
        subFileset: List<String> = emptyList()
    ): Map<String, String> {
        return getNodeInfo(
            projectId = projectId,
            repoName = repoName,
            path = path,
            subFileset = subFileset,
            type = MD5
        )
    }

    /**
     * 查询目录下的所有文件，并返回
     * 返回值： key/value： 路径/md5
     */
    fun getDownloadPath(
        projectId: String,
        repoName: String,
        path: String,
        subFileset: List<String> = emptyList()
    ): Map<String, String> {
        return getNodeInfo(
            projectId = projectId,
            repoName = repoName,
            path = path,
            subFileset = subFileset
        )
    }

    /**
     * 获取对应文件上传地址
     */
    fun getConanFileUploadUrls(
        projectId: String,
        repoName: String,
        conanFileReference: ConanFileReference,
        fileSizes: Map<String, String>
    ): Map<String, String> {
        val latestRef = getLatestRef(projectId, repoName, conanFileReference)
        val path = buildExportFolderPath(latestRef)
        val result = mutableMapOf<String, String>()
        val prefix = getRequestUrlPrefix(conanFileReference)
        fileSizes.forEach { (k, _) -> result[k] = joinString(prefix, path, k) }
        return result
    }

    /**
     * 获取package对应上传地址
     */
    fun getPackageUploadUrls(
        projectId: String,
        repoName: String,
        packageReference: PackageReference,
        fileSizes: Map<String, String>
    ): Map<String, String> {
        val latestRef = getLatestPackageRef(projectId, repoName, packageReference)
        try {
            getRecipeSnapshot(projectId, repoName, latestRef.conRef)
        } catch (e: ConanFileNotFoundException) {
            throw PackageNotFoundException(latestRef.toString())
        }
        val path = buildPackageRevisionFolderPath(latestRef)
        val result = mutableMapOf<String, String>()
        val prefix = getRequestUrlPrefix(packageReference.conRef)
        fileSizes.forEach { (k, _) -> result[k] = joinString(prefix, path, k) }
        return result
    }

    /**
     * 获取对应节点相关信息
     */
    fun getNodeInfo(
        projectId: String,
        repoName: String,
        path: String,
        subFileset: List<String> = emptyList(),
        type: String = URL
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pathList = getPaths(projectId, repoName, path, subFileset)
        pathList.forEach { it ->
            nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, it))?.let {
                when (type) {
                    MD5 -> result[it.name] = it.md5!!
                    else -> result[it.name] = it.fullPath
                }
            }
        }
        return result
    }

    fun getNodeDetail(
        projectId: String,
        repoName: String,
        path: String,
    ): NodeDetail {
        return nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, path))
            ?: throw ConanRecipeNotFoundException(ConanMessageCode.CONAN_RECIPE_NOT_FOUND, path, "$projectId|$repoName")
    }

    fun getContentOfConanInfoFile(
        projectId: String,
        repoName: String,
        path: String,
    ): ConanInfo {
        val fullPath = "/$path"
        val node = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
            ?: throw NodeNotFoundException(path)
        val repo = repositoryService.getRepoDetail(projectId, repoName)
            ?: throw RepoNotFoundException("$projectId|$repoName not found")
        val inputStream = storageManager.loadArtifactInputStream(node, repo.storageCredentials)
        // TODO 需要调整
        val file = ArtifactFileFactory.build(inputStream!!, node.size)
        return ConanInfoLoadUtil.load(file.flushToFile())
    }

    fun getPaths(
        projectId: String,
        repoName: String,
        path: String,
        subFileset: List<String> = emptyList()
    ): List<String> {
        val fullPath = "/$path"
        nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
            ?: throw NodeNotFoundException(path)
        val subFileMap = mutableMapOf<String, String>()
        nodeService.listNode(
            ArtifactInfo(projectId, repoName, fullPath),
            NodeListOption(includeFolder = false, deep = true)
        ).forEach {
            subFileMap[it.name] = it.fullPath
        }
        if (subFileset.isEmpty()) return subFileMap.values.toList()
        return subFileset.intersect(subFileMap.keys).map { subFileMap[it]!! }
    }

    fun getLatestPackageRef(
        projectId: String,
        repoName: String,
        packageReference: PackageReference
    ): PackageReference {
        val ref = getLatestRef(
            projectId = projectId,
            repoName = repoName,
            conanFileReference = packageReference.conRef
        )
        val newPRef = packageReference.copy(conRef = ref)
        val revision = getLastPackageRevision(
            projectId = projectId,
            repoName = repoName,
            packageReference = newPRef
        )?.revision ?: DEFAULT_REVISION_V1
        return newPRef.copy(revision = revision)
    }

    fun getLatestRef(
        projectId: String,
        repoName: String,
        conanFileReference: ConanFileReference
    ): ConanFileReference {
        val revision = getLastRevision(
            projectId = projectId,
            repoName = repoName,
            conanFileReference = conanFileReference
        )?.revision ?: DEFAULT_REVISION_V1
        return conanFileReference.copy(revision = revision)
    }

    fun getLastRevision(
        projectId: String,
        repoName: String,
        conanFileReference: ConanFileReference
    ): RevisionInfo? {
        val revPath = getRecipeRevisionsFile(conanFileReference)
        val refStr = buildConanFileName(conanFileReference)
        return lockAction(projectId, repoName, revPath) {
            getLatestRevision(
                projectId = projectId,
                repoName = repoName,
                revPath = revPath,
                refStr = refStr
            )
        }
    }

    fun getLastPackageRevision(
        projectId: String,
        repoName: String,
        packageReference: PackageReference
    ): RevisionInfo? {
        val revPath = getPackageRevisionsFile(packageReference)
        val refStr = buildPackageReference(packageReference)
        return lockAction(projectId, repoName, revPath) {
            getLatestRevision(
                projectId = projectId,
                repoName = repoName,
                revPath = revPath,
                refStr = refStr
            )
        }
    }

    fun getLatestRevision(
        projectId: String,
        repoName: String,
        revPath: String,
        refStr: String
    ): RevisionInfo? {
        val indexJson = getRevisionsList(
            projectId = projectId,
            repoName = repoName,
            revPath = revPath,
            refStr = refStr
        )
        if (indexJson.revisions.isEmpty()) {
            val revisionV1Path = joinString(revPath, DEFAULT_REVISION_V1)
            nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, revisionV1Path)) ?: return null
            return RevisionInfo(DEFAULT_REVISION_V1, convertToUtcTime(LocalDateTime.now()))
        } else {
            return indexJson.revisions.first()
        }
    }

    fun getRevisionsList(
        projectId: String,
        repoName: String,
        revPath: String,
        refStr: String
    ): IndexInfo {
        val fullPath = "/$revPath"
        val indexNode = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
            ?: return IndexInfo(refStr)
        val repo = repositoryService.getRepoDetail(projectId, repoName)
            ?: throw RepoNotFoundException("$projectId|$repoName not found")
        storageManager.loadArtifactInputStream(indexNode, repo.storageCredentials)?.use {
            return it.readJsonString()
        }
        return IndexInfo(refStr)
    }

    fun getPackageIdList(
        projectId: String,
        repoName: String,
        revPath: String,
    ): List<String> {
        nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, revPath)) ?: return emptyList()
        return nodeService.listNode(
            ArtifactInfo(projectId, repoName, revPath),
            NodeListOption(includeFolder = true, deep = false)
        ).map { it.name }
    }

    fun buildFileAndNodeCreateRequest(
        indexInfo: IndexInfo,
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String
    ): Pair<ArtifactFile, NodeCreateRequest> {
        val artifactFile = ArtifactFileFactory.build(indexInfo.toJsonString().byteInputStream())
        val nodeCreateRequest = NodeCreateRequest(
            projectId = projectId,
            repoName = repoName,
            folder = false,
            fullPath = fullPath,
            size = artifactFile.getSize(),
            sha256 = artifactFile.getFileSha256(),
            md5 = artifactFile.getFileMd5(),
            crc64ecma = artifactFile.getFileCrc64ecma(),
            overwrite = true,
            operator = operator
        )
        return Pair(artifactFile, nodeCreateRequest)
    }

    /**
     * upload index.json
     */
    fun uploadIndexJson(
        projectId: String,
        repoName: String,
        fullPath: String,
        indexInfo: IndexInfo
    ) {
        val (artifactFile, nodeCreateRequest) = buildFileAndNodeCreateRequest(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            indexInfo = indexInfo,
            operator = SecurityUtils.getUserId()
        )
        val repository = repositoryService.getRepoDetail(
            nodeCreateRequest.projectId,
            nodeCreateRequest.repoName
        )
            ?: throw RepoNotFoundException("Repository[${nodeCreateRequest.repoName}] does not exist")
        storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, repository.storageCredentials)
    }

    // v2
    /**
     * 获取指定package下的文件列表
     * 返回{"files":{"filepath": ""}}
     */
    fun getPackageFiles(
        projectId: String,
        repoName: String,
        packageReference: PackageReference
    ): Map<String, Map<String, String>> {
        val path = buildPackageRevisionFolderPath(packageReference)
        val files = getDownloadPath(projectId, repoName, path)
        return mapOf("files" to files)
    }

    /**
     * 获取指定recipe下的文件列表
     * 返回{"files":{"filepath": ""}}
     */
    fun getRecipeFiles(
        projectId: String,
        repoName: String,
        conanFileReference: ConanFileReference
    ): Map<String, Map<String, String>> {
        val path = buildRevisionPath(conanFileReference)
        val files = getDownloadPath(projectId, repoName, path)
        return mapOf("files" to files)
    }

    /**
     * 获取recipe对应的revisions信息
     */
    fun getRecipeRevisions(
        projectId: String,
        repoName: String,
        conanFileReference: ConanFileReference
    ): IndexInfo {
        val revPath = getRecipeRevisionsFile(conanFileReference)
        val refStr = buildConanFileName(conanFileReference)
        return lockAction(projectId, repoName, revPath) {
            getRevisionsList(
                projectId = projectId,
                repoName = repoName,
                revPath = revPath,
                refStr = refStr
            )
        }
    }

    /**
     * 获取package对应的revisions信息
     */
    fun getPackageRevisions(
        projectId: String,
        repoName: String,
        packageReference: PackageReference
    ): IndexInfo {
        val revPath = getPackageRevisionsFile(packageReference)
        val refStr = buildPackageReference(packageReference)
        return lockAction(projectId, repoName, revPath) {
            getRevisionsList(
                projectId = projectId,
                repoName = repoName,
                revPath = revPath,
                refStr = refStr
            )
        }
    }

    /**
     * 获取package对应的conaninfo.txt文件内容
     */
    fun getPackageConanInfo(
        projectId: String,
        repoName: String,
        conanFileReference: ConanFileReference
    ): Map<String, ConanInfo> {
        val result = mutableMapOf<String, ConanInfo>()
        val tempRevPath = getPackageRevisionsFile(conanFileReference)
        val packageIds = getPackageIdList(projectId, repoName, tempRevPath)
        packageIds.forEach { packageId ->
            val packageReference = PackageReference(conanFileReference, packageId)
            getLastPackageRevision(
                projectId = projectId,
                repoName = repoName,
                packageReference = packageReference,
            )?.let {
                val path = getPackageConanInfoFile(packageReference.copy(revision = it.revision))
                result[packageId] = getContentOfConanInfoFile(projectId, repoName, path)
            }
        }
        return result
    }

    /**
     * 针对自旋达到次数后，还没有获取到锁的情况默认也会执行所传入的方法,确保业务流程不中断
     */
    fun <T> lockAction(projectId: String, repoName: String, revPath: String, action: () -> T): T {
        val lockKey = buildRedisKey(projectId, repoName, revPath)
        val lock = lockOperation.getLock(lockKey)
        return if (lockOperation.getSpinLock(lockKey, lock)) {
            logger.info("Lock for key $lockKey has been acquired.")
            try {
                action()
            } finally {
                lockOperation.close(lockKey, lock)
                logger.info("Lock for key $lockKey has been released.")
            }
        } else {
            action()
        }
    }

    fun getDomain(): String {
        return properties.domain
    }

    private fun buildRedisKey(projectId: String, repoName: String, revPath: String): String {
        return "$REDIS_LOCK_KEY_PREFIX$projectId/$repoName/$revPath"
    }

    /**
     * 获取请求URL前缀，用于生成上传或者下载路径
     */
    private fun getRequestUrlPrefix(conanFileReference: ConanFileReference): String {
        val requestUri = HttpContextHolder.getRequest().requestURI
        val prefixPath = requestUri.substring(
            0, requestUri.indexOf(ConanPathUtils.buildOriginalConanFileName(conanFileReference))
        ).trimEnd(CharPool.SLASH).removeSuffix(CONANS_URL_TAG).trimEnd(CharPool.SLASH)
        return joinString(
            properties.domain,
            prefixPath,
            UPLOAD_URL_PREFIX
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ConanCommonService::class.java)
        const val REDIS_LOCK_KEY_PREFIX = "conan:lock:indexJson:"
    }
}
