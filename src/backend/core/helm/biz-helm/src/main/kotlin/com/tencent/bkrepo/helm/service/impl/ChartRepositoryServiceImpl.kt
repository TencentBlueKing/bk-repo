/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.helm.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.util.UrlFormatter
import com.tencent.bkrepo.common.api.util.readYamlString
import com.tencent.bkrepo.common.artifact.exception.ArtifactDownloadForbiddenException
import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.helm.config.HelmProperties
import com.tencent.bkrepo.helm.constants.CHART
import com.tencent.bkrepo.helm.constants.CHART_PACKAGE_FILE_EXTENSION
import com.tencent.bkrepo.helm.constants.FILE_TYPE
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.HelmMessageCode
import com.tencent.bkrepo.helm.constants.INDEX_YAML
import com.tencent.bkrepo.helm.constants.NAME
import com.tencent.bkrepo.helm.constants.NODE_CREATE_DATE
import com.tencent.bkrepo.helm.constants.NODE_FULL_PATH
import com.tencent.bkrepo.helm.constants.NODE_METADATA
import com.tencent.bkrepo.helm.constants.NODE_METADATA_NAME
import com.tencent.bkrepo.helm.constants.NODE_METADATA_VERSION
import com.tencent.bkrepo.helm.constants.NODE_NAME
import com.tencent.bkrepo.helm.constants.NODE_SHA256
import com.tencent.bkrepo.helm.constants.PROJECT_ID
import com.tencent.bkrepo.helm.constants.PROV
import com.tencent.bkrepo.helm.constants.REPO_NAME
import com.tencent.bkrepo.helm.constants.SLEEP_MILLIS
import com.tencent.bkrepo.helm.exception.HelmBadRequestException
import com.tencent.bkrepo.helm.exception.HelmFileNotFoundException
import com.tencent.bkrepo.helm.pojo.HelmDomainInfo
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.pojo.metadata.HelmChartMetadata
import com.tencent.bkrepo.helm.pojo.metadata.HelmIndexYamlMetadata
import com.tencent.bkrepo.helm.pojo.user.PackageVersionInfo
import com.tencent.bkrepo.helm.service.ChartRepositoryService
import com.tencent.bkrepo.helm.utils.ChartParserUtil
import com.tencent.bkrepo.helm.utils.DecompressUtil.getArchivesContent
import com.tencent.bkrepo.helm.utils.HelmUtils
import com.tencent.bkrepo.helm.utils.ObjectBuilderUtil
import com.tencent.bkrepo.helm.utils.TimeFormatUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ChartRepositoryServiceImpl(
    private val helmProperties: HelmProperties,
    private val helmOperationService: HelmOperationService
) : AbstractChartService(), ChartRepositoryService {

    @Permission(ResourceType.REPO, PermissionAction.READ)
    override fun allChartsList(artifactInfo: HelmArtifactInfo, startTime: LocalDateTime?): ResponseEntity<Any> {
        return queryLatestIndex(artifactInfo) { chartListSearch(artifactInfo, startTime) }
    }

    private fun chartListSearch(artifactInfo: HelmArtifactInfo, startTime: LocalDateTime?): ResponseEntity<Any> {
        val indexYamlMetadata = if (!exist(
                projectId = artifactInfo.projectId,
                repoName = artifactInfo.repoName,
                fullPath = HelmUtils.getIndexCacheYamlFullPath()
            )
        ) {
            HelmUtils.initIndexYamlMetadata()
        } else {
            queryOriginalIndexYaml()
        }
        val startDate = startTime ?: LocalDateTime.MIN
        return ResponseEntity.ok().body(
            ChartParserUtil.searchJson(indexYamlMetadata, artifactInfo.getArtifactFullPath(), startDate)
        )
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    override fun isExists(artifactInfo: HelmArtifactInfo) {
        val response = HttpContextHolder.getResponse()
        val status: HttpStatus = with(artifactInfo) {
            val projectId = Rule.QueryRule(PROJECT_ID, projectId)
            val repoName = Rule.QueryRule(REPO_NAME, repoName)
            val urlList = this.getArtifactFullPath().trimStart('/').split("/").filter { it.isNotBlank() }
            val rule: Rule? = when (urlList.size) {
                // query with name
                1 -> {
                    val name = Rule.QueryRule(NODE_METADATA_NAME, urlList[0])
                    Rule.NestedRule(mutableListOf(repoName, projectId, name))
                }
                // query with name and version
                2 -> {
                    val name = Rule.QueryRule(NODE_METADATA_NAME, urlList[0])
                    val version = Rule.QueryRule(NODE_METADATA_VERSION, urlList[1])
                    Rule.NestedRule(mutableListOf(repoName, projectId, name, version))
                }
                else -> {
                    null
                }
            }
            if (rule != null) {
                val queryModel = QueryModel(
                    page = PageLimit(CURRENT_PAGE, SIZE),
                    sort = Sort(listOf(NAME), Sort.Direction.ASC),
                    select = mutableListOf(PROJECT_ID, REPO_NAME, NODE_FULL_PATH, NODE_METADATA),
                    rule = rule
                )
                val nodeList: List<Map<String, Any?>> = nodeSearchService.searchWithoutCount(queryModel).records
                if (nodeList.isEmpty()) HttpStatus.NOT_FOUND else HttpStatus.OK
            } else {
                HttpStatus.NOT_FOUND
            }
        }
        response.status = status.value()
    }

    override fun detailVersion(
        userId: String,
        artifactInfo: HelmArtifactInfo,
        packageKey: String,
        version: String
    ): PackageVersionInfo {
        with(artifactInfo) {
            val name = PackageKeys.resolveHelm(packageKey)
            val fullPath = String.format("/%s-%s.tgz", name, version)
            val nodeDetail = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath)) ?: run {
                logger.warn("node [$fullPath] don't found.")
                throw HelmFileNotFoundException(HelmMessageCode.HELM_FILE_NOT_FOUND, fullPath, "$projectId|$repoName")
            }
            val packageVersion = packageService.findVersionByName(projectId, repoName, packageKey, version) ?: run {
                logger.warn("packageKey [$packageKey] don't found.")
                throw PackageNotFoundException(packageKey)
            }
            val basicInfo = ObjectBuilderUtil.buildBasicInfo(nodeDetail, packageVersion)
            return PackageVersionInfo(basicInfo, packageVersion.packageMetadata)
        }
    }

    override fun getRegistryDomain(): HelmDomainInfo {
        return HelmDomainInfo(UrlFormatter.formatHost(helmProperties.domain))
    }

    override fun queryIndexYaml(artifactInfo: HelmArtifactInfo) {
        helmOperationService.checkNodePermission(INDEX_YAML, PermissionAction.READ)
        queryLatestIndex(artifactInfo) { downloadIndex(artifactInfo) }
    }

    private fun <T> queryLatestIndex(
        artifactInfo: HelmArtifactInfo,
        action: () -> T
    ): T {
        return if (helmChartEventRecordDao.checkIndexExpiredStatus(artifactInfo.projectId, artifactInfo.repoName)) {
            lockAction(artifactInfo.projectId, artifactInfo.repoName) {
                val exist = helmChartEventRecordDao.checkIndexExpiredStatus(
                    artifactInfo.projectId, artifactInfo.repoName
                )
                if (exist) {
                    regenerateIndex(artifactInfo, false)
                }
                action()
            }
        } else {
            action()
        }
    }

    private fun downloadIndex(artifactInfo: HelmArtifactInfo) {
        // 创建仓库后，index.yaml文件时没有生成的，需要生成默认的
        if (!exist(artifactInfo.projectId, artifactInfo.repoName, HelmUtils.getIndexCacheYamlFullPath())) {
            regenerateIndex(artifactInfo, false)
        }
        downloadIndexYaml()
    }

    /**
     * 下载index.yaml （local类型仓库index.yaml存储时使用的name时index-cache.yaml，remote需要转换）
     */
    private fun downloadIndexYaml() {
        val context = ArtifactDownloadContext(null, ObjectBuilderUtil.buildIndexYamlRequest())
        context.putAttribute(FULL_PATH, HelmUtils.getIndexCacheYamlFullPath())
        try {
            ArtifactContextHolder.getRepository().download(context)
        } catch (e: OverloadException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Error occurred while downloading index.yaml, error: ${e.message}")
            throw HelmFileNotFoundException(
                HelmMessageCode.HELM_FILE_NOT_FOUND, "index.yaml", "${context.projectId}|${context.repoName}"
            )
        }
    }

    @Synchronized
    override fun freshIndexFile(artifactInfo: HelmArtifactInfo) {
        // 先查询index.yaml文件，如果不存在则创建，
        // 存在则根据最后一次更新时间与node节点创建时间对比进行增量更新
        with(artifactInfo) {
            if (!exist(projectId, repoName, HelmUtils.getIndexYamlFullPath())) {
                val nodeList = queryNodeList(artifactInfo, false)
                logger.info(
                    "query node list success, size [${nodeList.size}] in repo [$projectId/$repoName]," +
                        " start generate index.yaml ... "
                )
                val indexYamlMetadata = buildIndexYamlMetadata(nodeList, artifactInfo)
                uploadIndexYamlMetadata(indexYamlMetadata).also {
                    logger.info("fresh the index file success in repo [$projectId/$repoName]")
                }
                return
            }

            val originalYamlMetadata = queryOriginalIndexYaml()
            val dateTime =
                originalYamlMetadata.generated.let { TimeFormatUtil.convertToLocalTime(it) }
            val now = LocalDateTime.now()
            val nodeList = queryNodeList(artifactInfo, lastModifyTime = dateTime)
            if (nodeList.isNotEmpty()) {
                val indexYamlMetadata = buildIndexYamlMetadata(nodeList, artifactInfo)
                logger.info(
                    "start refreshing the index file in repo [$projectId/$repoName], original index file " +
                        "entries size : [${indexYamlMetadata.entriesSize()}]"
                )
                indexYamlMetadata.generated = TimeFormatUtil.convertToUtcTime(now)
                uploadIndexYamlMetadata(indexYamlMetadata).also {
                    logger.info(
                        "refresh the index file success in repo [$projectId/$repoName], " +
                            "current index file entries size : [${indexYamlMetadata.entriesSize()}]"
                    )
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun buildIndexYamlMetadata(
        result: List<Map<String, Any?>>,
        artifactInfo: HelmArtifactInfo,
        isInit: Boolean
    ): HelmIndexYamlMetadata {
        with(artifactInfo) {
            val indexYamlMetadata =
                if (!exist(projectId, repoName, HelmUtils.getIndexYamlFullPath()) || isInit) {
                    HelmUtils.initIndexYamlMetadata()
                } else {
                    queryOriginalIndexYaml()
                }
            if (result.isEmpty()) return indexYamlMetadata
            val context = ArtifactQueryContext()
            result.forEach {
                Thread.sleep(SLEEP_MILLIS)
                var chartName: String? = null
                var chartVersion: String? = null
                try {
                    val chartMetadata = queryHelmChartMetadata(context, it)
                    chartName = chartMetadata.name
                    chartVersion = chartMetadata.version
                    chartMetadata.urls = listOf(
                        UrlFormatter.format(
                            helmProperties.domain, "$projectId/$repoName/charts/$chartName-$chartVersion.tgz"
                        )
                    )
                    chartMetadata.created = TimeFormatUtil.convertToUtcTime(it[NODE_CREATE_DATE] as LocalDateTime)
                    chartMetadata.digest = it[NODE_SHA256] as String
                    ChartParserUtil.addIndexEntries(indexYamlMetadata, chartMetadata)
                } catch (ex: HelmFileNotFoundException) {
                    logger.warn(
                        "generate indexFile for chart [$chartName-$chartVersion.tgz] in " +
                            "[${artifactInfo.getRepoIdentify()}] failed, ${ex.message}"
                    )
                }
            }
            return indexYamlMetadata
        }
    }

    private fun queryHelmChartMetadata(context: ArtifactQueryContext, nodeInfo: Map<String, Any?>): HelmChartMetadata {
        context.putAttribute(FULL_PATH, nodeInfo[NODE_FULL_PATH] as String)
        val artifactInputStream =
            ArtifactContextHolder.getRepository().query(context) as ArtifactInputStream
        val content = artifactInputStream.use {
            it.getArchivesContent(CHART_PACKAGE_FILE_EXTENSION)
        }
        return content.byteInputStream().readYamlString()
    }

    @Permission(ResourceType.NODE, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun installTgz(artifactInfo: HelmArtifactInfo) {
        val context = ArtifactDownloadContext()
        context.putAttribute(FILE_TYPE, CHART)
        try {
            ArtifactContextHolder.getRepository().download(context)
        } catch (e: OverloadException) {
            throw e
        } catch (e: ArtifactDownloadForbiddenException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Error occurred while installing chart, error: ${e.message}")
            throw HelmFileNotFoundException(
                HelmMessageCode.HELM_FILE_NOT_FOUND, artifactInfo.getArtifactFullPath(), artifactInfo.getRepoIdentify()
            )
        }
    }

    @Permission(ResourceType.NODE, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun installProv(artifactInfo: HelmArtifactInfo) {
        val context = ArtifactDownloadContext()
        context.putAttribute(FILE_TYPE, PROV)
        try {
            ArtifactContextHolder.getRepository().download(context)
        } catch (e: OverloadException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Error occurred while installing prov, error: ${e.message}")
            throw HelmFileNotFoundException(
                HelmMessageCode.HELM_FILE_NOT_FOUND, artifactInfo.getArtifactFullPath(), artifactInfo.getRepoIdentify()
            )
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun regenerateIndexYaml(artifactInfo: HelmArtifactInfo, v1Flag: Boolean) {
        regenerateIndex(artifactInfo, v1Flag)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    override fun updatePackageForRemote(artifactInfo: HelmArtifactInfo) {
        helmOperationService.updatePackageForRemote(artifactInfo.projectId, artifactInfo.repoName)
    }

    @Permission(ResourceType.NODE, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun batchInstallTgz(artifactInfo: HelmArtifactInfo, startTime: LocalDateTime) {
        val context = ArtifactQueryContext()
        when (context.repositoryDetail.category) {
            RepositoryCategory.REMOTE -> throw HelmBadRequestException(
                HelmMessageCode.HELM_ILLEGAL_REQUEST,
                emptyList<String>()
            )
            else -> batchInstallLocalTgz(artifactInfo, startTime)
        }
    }

    private fun regenerateIndex(artifactInfo: HelmArtifactInfo, v1Flag: Boolean = true) {
        when (getRepositoryInfo(artifactInfo).category) {
            RepositoryCategory.REMOTE -> {
                helmOperationService.initPackageInfo(
                    projectId = artifactInfo.projectId,
                    repoName = artifactInfo.repoName,
                    userId = SecurityUtils.getUserId()
                )
            }
            else -> {
                val indexYamlMetadata = if (v1Flag) {
                    val nodeList = queryNodeList(artifactInfo, false)
                    logger.info(
                        "query node list for full refresh index.yaml success " +
                            "in repo [${artifactInfo.getRepoIdentify()}], size [${nodeList.size}]," +
                            " starting full refresh index.yaml ... "
                    )
                    buildIndexYamlMetadata(nodeList, artifactInfo, true)
                } else {
                    logger.info("Use v2 version to regenerate index")
                    regenerateHelmIndexYaml(artifactInfo)
                }
                uploadIndexYamlMetadata(indexYamlMetadata).also { logger.info("Full refresh index.yaml success！") }
            }
        }
    }

    private fun batchInstallLocalTgz(artifactInfo: HelmArtifactInfo, startTime: LocalDateTime) {
        val nodeList = queryNodeList(artifactInfo, lastModifyTime = startTime)
        if (nodeList.isEmpty()) {
            throw HelmFileNotFoundException(
                HelmMessageCode.HELM_FILE_NOT_FOUND, "chart", artifactInfo.getRepoIdentify()
            )
        }
        val context = ArtifactQueryContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        val nodeMap = mutableMapOf<String, ArtifactInputStream>()
        nodeList.forEach {
            context.putAttribute(FULL_PATH, it[NODE_FULL_PATH] as String)
            val artifactInputStream = repository.query(context) as ArtifactInputStream
            nodeMap[it[NODE_NAME] as String] = artifactInputStream
        }
        artifactResourceWriter.write(ArtifactResource(nodeMap, useDisposition = true))
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ChartRepositoryServiceImpl::class.java)
        const val CURRENT_PAGE = 0
        const val SIZE = 5
    }
}
