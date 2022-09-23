/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.conan.constant.CONANINFO
import com.tencent.bkrepo.conan.constant.CONANS_URL_TAG
import com.tencent.bkrepo.conan.constant.DEFAULT_REVISION_V1
import com.tencent.bkrepo.conan.constant.INDEX_JSON
import com.tencent.bkrepo.conan.constant.MD5
import com.tencent.bkrepo.conan.constant.PACKAGES_FOLDER
import com.tencent.bkrepo.conan.constant.UPLOAD_URL_PREFIX
import com.tencent.bkrepo.conan.constant.URL
import com.tencent.bkrepo.conan.exception.ConanFileNotFoundException
import com.tencent.bkrepo.conan.exception.ConanPackageNotFoundException
import com.tencent.bkrepo.conan.exception.ConanRecipeNotFoundException
import com.tencent.bkrepo.conan.pojo.ConanFileReference
import com.tencent.bkrepo.conan.pojo.ConanInfo
import com.tencent.bkrepo.conan.pojo.IndexInfo
import com.tencent.bkrepo.conan.pojo.PackageReference
import com.tencent.bkrepo.conan.pojo.RevisionInfo
import com.tencent.bkrepo.conan.utils.ConanInfoLoadUtil
import com.tencent.bkrepo.conan.utils.PathUtils
import com.tencent.bkrepo.conan.utils.PathUtils.buildConanFileName
import com.tencent.bkrepo.conan.utils.PathUtils.buildExportFolderPath
import com.tencent.bkrepo.conan.utils.PathUtils.buildOriginalConanFileName
import com.tencent.bkrepo.conan.utils.PathUtils.buildPackagePath
import com.tencent.bkrepo.conan.utils.PathUtils.buildPackageReference
import com.tencent.bkrepo.conan.utils.PathUtils.buildPackageRevisionFolderPath
import com.tencent.bkrepo.conan.utils.PathUtils.buildReference
import com.tencent.bkrepo.conan.utils.PathUtils.buildRevisionPath
import com.tencent.bkrepo.conan.utils.PathUtils.joinString
import com.tencent.bkrepo.conan.utils.TimeFormatUtil.convertToLocalTime
import com.tencent.bkrepo.conan.utils.TimeFormatUtil.convertToUtcTime
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class CommonService {
    @Autowired
    lateinit var nodeClient: NodeClient
    @Autowired
    lateinit var storageManager: StorageManager
    @Autowired
    lateinit var repositoryClient: RepositoryClient

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
            throw ConanPackageNotFoundException(latestRef.toString())
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
            nodeClient.getNodeDetail(projectId, repoName, it).data?.let {
                when (type) {
                    MD5 -> result[it.name] = it.md5!!
                    else -> result[it.name] = it.fullPath
                }
            }
        }
        return result
    }

    fun checkNodeExist(
        projectId: String,
        repoName: String,
        path: String,
    ): NodeDetail {
        return nodeClient.getNodeDetail(projectId, repoName, path).data
            ?: throw ConanRecipeNotFoundException("Could not find $path in repo $projectId|$repoName")
    }

    fun getContentOfConanInfoFile(
        projectId: String,
        repoName: String,
        path: String,
    ): ConanInfo {
        val node = nodeClient.getNodeDetail(projectId, repoName, path).data
            ?: throw NodeNotFoundException(path)
        val repo = repositoryClient.getRepoDetail(projectId, repoName).data
            ?: throw RepoNotFoundException("$projectId|$repoName not found")
        val inputStream = storageManager.loadArtifactInputStream(node, repo.storageCredentials)
        val file = ArtifactFileFactory.build(inputStream!!, node.size)
        return ConanInfoLoadUtil.load(file.getFile()!!)
    }

    fun getPaths(
        projectId: String,
        repoName: String,
        path: String,
        subFileset: List<String> = emptyList()
    ): List<String> {
        nodeClient.getNodeDetail(projectId, repoName, path).data
            ?: throw NodeNotFoundException(path)
        val subFileMap = mutableMapOf<String, String>()
            nodeClient.listNode(projectId, repoName, path, includeFolder = false, deep = true).data!!.forEach{
                subFileMap[it.name] = it.fullPath
            }
        if (subFileset.isEmpty()) return  subFileMap.values.toList()
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
        val refStr = buildReference(conanFileReference)
        return getLatestRevision(
            projectId = projectId,
            repoName = repoName,
            revPath = revPath,
            refStr = refStr
        )
    }

    fun getLastPackageRevision(
        projectId: String,
        repoName: String,
        packageReference: PackageReference
    ): RevisionInfo? {
        val revPath = getPackageRevisionsFile(packageReference)
        val refStr = buildPackageReference(packageReference)
        return getLatestRevision(
            projectId = projectId,
            repoName = repoName,
            revPath = revPath,
            refStr = refStr
        )
    }

    fun updateIndexJson(
        projectId: String,
        repoName: String,
        revPath: String,
        refStr: String,
        revision: String
    ) {
        val indexJson = getRevisionsList(
            projectId = projectId,
            repoName = repoName,
            revPath = revPath,
            refStr = refStr
        )
        val revisions = indexJson.revisions.filter { it.revision != revision }
        val newIndexJson = indexJson.copy(revisions = revisions)
        val(artifactFile, nodeCreateRequest) = buildFileAndNodeCreateRequest(
            projectId = projectId,
            repoName = repoName,
            fullPath = joinString(revPath, INDEX_JSON),
            indexInfo = newIndexJson,
            operator = SecurityUtils.getUserId()
        )
        uploadIndexJson(artifactFile, nodeCreateRequest)
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
            // TODO revisions 列表中数据要以time进行比较排序，最新数据放在前面
            val revisionV1Path = joinString(revPath, DEFAULT_REVISION_V1)
            nodeClient.getNodeDetail(projectId, repoName, revisionV1Path).data ?: return null
            indexJson.revisions = mutableListOf(
                RevisionInfo(DEFAULT_REVISION_V1, convertToUtcTime(LocalDateTime.now()))
            )
            val(artifactFile, nodeCreateRequest) = buildFileAndNodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = joinString(revisionV1Path, INDEX_JSON),
                indexInfo = indexJson,
                operator = SecurityUtils.getUserId()
            )
            uploadIndexJson(artifactFile, nodeCreateRequest)
            return indexJson.revisions.first()
        } else {
            return indexJson.revisions.sortedWith(
                kotlin.Comparator { r1, r2 ->
                    return@Comparator if (convertToLocalTime(r1.time).isAfter(convertToLocalTime(r2.time))) {
                        1
                    } else {
                        0
                    }
                }
            ).first()
        }
    }

    fun getPackageRevisionsFile(packageReference: PackageReference): String {
        val temp = buildRevisionPath(packageReference.conRef)
        val pFolder = joinString(temp, PACKAGES_FOLDER)
        val pRevison = joinString(pFolder, packageReference.packageId)
        return joinString(pRevison, INDEX_JSON)
    }

    fun getPackageRevisionsFile(conanFileReference: ConanFileReference): String {
        val temp = buildRevisionPath(conanFileReference)
        return joinString(temp, PACKAGES_FOLDER)
    }

    fun getRecipeRevisionsFile(conanFileReference: ConanFileReference): String {
        val recipeFolder = buildPackagePath(conanFileReference)
        return joinString(recipeFolder, INDEX_JSON)
    }

    fun getPackageConanInfoFile(packageReference: PackageReference): String {
        val temp = buildPackageRevisionFolderPath(packageReference)
        return joinString(temp, CONANINFO)
    }

    fun getRevisionsList(
        projectId: String,
        repoName: String,
        revPath: String,
        refStr: String
    ): IndexInfo {
        nodeClient.getNodeDetail(projectId, repoName, revPath).data ?: return IndexInfo(refStr)
        val indexNode = nodeClient.getNodeDetail(projectId, repoName, joinString(revPath, INDEX_JSON)).data
            ?: return IndexInfo(refStr)
        val repo = repositoryClient.getRepoDetail(projectId, repoName).data
            ?: throw RepoNotFoundException("$projectId|$repoName not found")
        storageManager.loadArtifactInputStream(indexNode, repo.storageCredentials)?.use {
            return it.readJsonString<IndexInfo>()
        }
        return IndexInfo(refStr)
    }

    fun getPackageIdList(
        projectId: String,
        repoName: String,
        revPath: String,
    ): List<String> {
        nodeClient.getNodeDetail(projectId, repoName, revPath).data
            ?: throw NodeNotFoundException(revPath)
        return nodeClient.listNode(projectId, repoName, revPath, includeFolder = false, deep = false).data!!.map {
            it.name
        }
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
            overwrite = true,
            operator = operator
        )
        return Pair(artifactFile, nodeCreateRequest)
    }

    /**
     * upload index.json
     */
    fun uploadIndexJson(artifactFile: ArtifactFile, nodeCreateRequest: NodeCreateRequest) {
        val repository = repositoryClient.getRepoDetail(
            nodeCreateRequest.projectId,
            nodeCreateRequest.repoName
        ).data
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
        val files = getDownloadPath(projectId, repoName, path).mapValues { EMPTY }
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
        val files = getDownloadPath(projectId, repoName, path).mapValues { EMPTY }
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
        val refStr = buildReference(conanFileReference)
        return getRevisionsList(
            projectId = projectId,
            repoName = repoName,
            revPath = revPath,
            refStr = refStr
        )
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
        return getRevisionsList(
            projectId = projectId,
            repoName = repoName,
            revPath = revPath,
            refStr = refStr
        )
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
        val revPath = getRecipeRevisionsFile(conanFileReference)
        val refStr = buildReference(conanFileReference)
        val indexJson = getRevisionsList(
            projectId = projectId,
            repoName = repoName,
            revPath = revPath,
            refStr = refStr
        )
        val revisions = if (indexJson.revisions.isEmpty()) {
            val revisionV1Path = joinString(revPath, DEFAULT_REVISION_V1)
            nodeClient.getNodeDetail(projectId, repoName, revisionV1Path).data ?: return emptyMap()
            listOf(RevisionInfo(DEFAULT_REVISION_V1, convertToUtcTime(LocalDateTime.now())))
        } else {
            // TODO 当默认获取列表为有序的以后可以删掉
            indexJson.revisions.sortedWith(
                kotlin.Comparator { r1, r2 ->
                    return@Comparator if (convertToLocalTime(r1.time).isAfter(convertToLocalTime(r2.time))) {
                        1
                    } else {
                        0
                    }
                }
            )
        }

        revisions.forEach {
            val conf = conanFileReference.copy(revision = it.revision)
            val revPath = getPackageRevisionsFile(conf)
            val packageIds = getPackageIdList(projectId, repoName, revPath)
            packageIds.forEach { packageId ->
                val packageReference = PackageReference(conf, packageId)
                val prevPath = getPackageRevisionsFile(packageReference)
                val prefStr = buildPackageReference(packageReference)
                val packageIndexJson = getRevisionsList(
                    projectId = projectId,
                    repoName = repoName,
                    revPath = prevPath,
                    refStr = prefStr
                )
                // TODO 当默认获取列表为有序的以后可以删掉
                val lastRevision = packageIndexJson.revisions.sortedWith(
                    kotlin.Comparator { r1, r2 ->
                        return@Comparator if (convertToLocalTime(r1.time).isAfter(convertToLocalTime(r2.time))) {
                            1
                        } else {
                            0
                        }
                    }
                ).first()
                val path = getPackageConanInfoFile(packageReference.copy(revision = lastRevision.revision))
                result[packageId] = getContentOfConanInfoFile(projectId, repoName, path)
            }
        }
        return result
    }

    /**
     * 获取请求URL前缀，用于生成上传或者下载路径
     */
    private fun getRequestUrlPrefix(conanFileReference: ConanFileReference): String {
        val requestUrl = HttpContextHolder.getRequest().requestURL.toString()
        val prefixUrl = requestUrl.substring(
            0, requestUrl.indexOf(buildOriginalConanFileName(conanFileReference))
        ).trimEnd(CharPool.SLASH).removeSuffix(CONANS_URL_TAG)
        return joinString(
            prefixUrl,
            UPLOAD_URL_PREFIX
        )
    }
}
