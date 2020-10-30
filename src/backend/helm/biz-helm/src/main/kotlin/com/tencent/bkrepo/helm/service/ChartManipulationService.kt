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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.constant.OCTET_STREAM
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.MultipartArtifactFile
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.async.PackageHandler
import com.tencent.bkrepo.helm.constants.CHART
import com.tencent.bkrepo.helm.constants.CHART_PACKAGE_FILE_EXTENSION
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.constants.NAME
import com.tencent.bkrepo.helm.constants.PROV
import com.tencent.bkrepo.helm.constants.PROVENANCE_FILE_EXTENSION
import com.tencent.bkrepo.helm.constants.REPO_TYPE
import com.tencent.bkrepo.helm.constants.VERSION
import com.tencent.bkrepo.helm.exception.HelmErrorInvalidProvenanceFileException
import com.tencent.bkrepo.helm.exception.HelmFileNotFoundException
import com.tencent.bkrepo.helm.exception.HelmRepoNotFoundException
import com.tencent.bkrepo.helm.pojo.HelmSuccessResponse
import com.tencent.bkrepo.helm.pojo.IndexEntity
import com.tencent.bkrepo.helm.utils.DecompressUtil.getArchivesContent
import com.tencent.bkrepo.helm.utils.YamlUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.streams.toList

@Service
class ChartManipulationService {

    @Autowired
    private lateinit var chartRepositoryService: ChartRepositoryService

    @Autowired
    private lateinit var packageHandler: PackageHandler

    @Autowired
    private lateinit var nodeClient: NodeClient

    @Autowired
    private lateinit var repositoryClient: RepositoryClient

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun uploadProv(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap): HelmSuccessResponse {
        val context = ArtifactUploadContext(artifactFileMap)
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        // context.contextAttributes = getContextAttrMap(artifactFileMap = artifactFileMap)
        setContextAttributes(context, artifactFileMap)
        if (!artifactFileMap.keys.contains(PROV)) throw HelmFileNotFoundException("no package or provenance file found in form fields chart and prov")
        repository.upload(context)
        return HelmSuccessResponse.pushSuccess()
    }

    fun setContextAttributes(
        context: ArtifactContext,
        artifactFileMap: ArtifactFileMap,
        chartFileInfo: Map<String, Any>? = null
    ) {
        artifactFileMap.entries.forEach { (name, _) ->
            if (CHART != name && PROV != name) {
                throw HelmFileNotFoundException("no package or provenance file found in form fields chart and prov")
            }
            if (CHART == name) {
                context.putAttribute(name + FULL_PATH, getChartFileFullPath(chartFileInfo))
            }
            if (PROV == name) {
                context.putAttribute(name + FULL_PATH, getProvFileFullPath(artifactFileMap))
            }
        }
    }

    fun getChartFileFullPath(chartFile: Map<String, Any>?): String {
        val chartName = chartFile?.get(NAME) as String
        val chartVersion = chartFile[VERSION] as String
        return String.format("/%s-%s.%s", chartName, chartVersion, CHART_PACKAGE_FILE_EXTENSION)
    }

    private fun getProvFileFullPath(artifactFileMap: ArtifactFileMap): String {
        val inputStream = (artifactFileMap[PROV] as MultipartArtifactFile).getInputStream()
        val contentStr = String(inputStream.readBytes())
        val hasPGPBegin = contentStr.startsWith("-----BEGIN PGP SIGNED MESSAGE-----")
        val nameMatch = Regex("\nname:[ *](.+)").findAll(contentStr).toList().flatMap(MatchResult::groupValues)
        val versionMatch = Regex("\nversion:[ *](.+)").findAll(contentStr).toList().flatMap(MatchResult::groupValues)
        if (!hasPGPBegin || nameMatch.size != 2 || versionMatch.size != 2) {
            throw HelmErrorInvalidProvenanceFileException("invalid provenance file")
        }
        return provenanceFilenameFromNameVersion(nameMatch[1], versionMatch[1])
    }

    private fun provenanceFilenameFromNameVersion(name: String, version: String): String {
        return String.format("/%s-%s.%s", name, version, PROVENANCE_FILE_EXTENSION)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun upload(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap): HelmSuccessResponse {
        val context = ArtifactUploadContext(artifactFileMap)
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        val chartFileInfo = getChartFile(artifactFileMap)
        setContextAttributes(context, artifactFileMap, chartFileInfo)
        repository.upload(context)
        // 创建包
        packageHandler.createVersion(
            context.userId,
            artifactInfo,
            chartFileInfo,
            context.getArtifactFile(CHART).getSize()
        )
        return HelmSuccessResponse.pushSuccess()
    }

    private fun getChartFile(artifactFileMap: ArtifactFileMap): MutableMap<String, Any> {
        if (!artifactFileMap.keys.contains(CHART)) throw HelmFileNotFoundException("no package or provenance file found in form fields chart and prov")
        val inputStream = (artifactFileMap[CHART] as MultipartArtifactFile).getInputStream()
        val result = inputStream.getArchivesContent("tgz")
        return YamlUtils.convertStringToEntity(result)
    }

    private fun uploadIndexYaml(indexEntity: IndexEntity) {
        val artifactFile = ArtifactFileFactory.build(YamlUtils.transEntityToStream(indexEntity))
        val uploadContext = ArtifactUploadContext(artifactFile)
        uploadContext.putAttribute(OCTET_STREAM + FULL_PATH, "/$INDEX_CACHE_YAML")
        val uploadRepository = ArtifactContextHolder.getRepository(uploadContext.repositoryDetail.category)
        uploadRepository.upload(uploadContext)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun deletePackage(artifactInfo: HelmArtifactInfo, name: String): HelmSuccessResponse {
        checkRepositoryExist(artifactInfo.projectId, artifactInfo.repoName)
        chartRepositoryService.freshIndexFile(artifactInfo)
        val indexEntity = chartRepositoryService.getOriginalIndexYaml()
        val fullPathList = indexEntity.entries[name]!!.stream().map { "$name-${it[VERSION] as String}.tgz" }.toList()
        val context = ArtifactRemoveContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)

        context.putAttribute(FULL_PATH, fullPathList)
        repository.remove(context)
        freshIndexYamlForRemove(name)
        // 删除包版本
        packageHandler.deletePackage(context.userId, name, artifactInfo)
        return HelmSuccessResponse.deleteSuccess()
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun deleteVersion(artifactInfo: HelmArtifactInfo, name: String, version: String): HelmSuccessResponse {
        val fullPath = String.format("/%s-%s.%s", name, version, CHART_PACKAGE_FILE_EXTENSION)
        with(artifactInfo) {
            checkRepositoryExist(projectId, repoName)
            val isExist = nodeClient.exist(projectId, repoName, fullPath).data!!
            if (!isExist) {
                throw HelmFileNotFoundException("remove $fullPath failed: no such file or directory")
            }
        }
        chartRepositoryService.freshIndexFile(artifactInfo)
        val context = ArtifactRemoveContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        context.putAttribute(FULL_PATH, mutableListOf(fullPath))
        repository.remove(context)
        logger.info("remove artifact [$fullPath] success!")
        freshIndexYamlForRemove(name, version)
        // 删除包版本
        packageHandler.deleteVersion(context.userId, name, version, artifactInfo)
        return HelmSuccessResponse.deleteSuccess()
    }

    @Synchronized
    private fun freshIndexYamlForRemove(name: String, version: String? = null) {
        try {
            val indexEntity = chartRepositoryService.getOriginalIndexYaml()
            indexEntity.entries.let {
                if (it[name]?.size == 1 && (version == it[name]?.get(0)?.get(VERSION) as String) || version == null) {
                    it.remove(name)
                } else {
                    run stop@{
                        it[name]?.forEachIndexed { index, chartMap ->
                            if (version == chartMap[VERSION] as String) {
                                it[name]?.removeAt(index)
                                return@stop
                            }
                        }
                    }
                }
            }
            uploadIndexYaml(indexEntity)
            logger.info("fresh index.yaml for delete [$name-$version.tgz] success!")
        } catch (exception: TypeCastException) {
            logger.error("fresh index.yaml for delete [$name-$version.tgz] failed, $exception")
            throw exception
        }
    }

    /**
     * 查询仓库是否存在
     */
    fun checkRepositoryExist(projectId: String, repoName: String) {
        repositoryClient.getRepoDetail(projectId, repoName, REPO_TYPE).data ?: run {
            logger.error("check repository [$repoName] in projectId [$projectId] failed!")
            throw HelmRepoNotFoundException("repository [$repoName] in projectId [$projectId] not existed.")
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ChartManipulationService::class.java)
    }
}
