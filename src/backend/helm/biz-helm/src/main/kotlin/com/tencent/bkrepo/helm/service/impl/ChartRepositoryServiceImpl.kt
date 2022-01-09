/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bkrepo.common.api.util.readYamlString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.helm.config.HelmProperties
import com.tencent.bkrepo.helm.constants.CHART
import com.tencent.bkrepo.helm.constants.CHART_PACKAGE_FILE_EXTENSION
import com.tencent.bkrepo.helm.constants.FILE_TYPE
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.NAME
import com.tencent.bkrepo.helm.constants.NODE_CREATE_DATE
import com.tencent.bkrepo.helm.constants.NODE_FULL_PATH
import com.tencent.bkrepo.helm.constants.NODE_NAME
import com.tencent.bkrepo.helm.constants.NODE_SHA256
import com.tencent.bkrepo.helm.constants.PROV
import com.tencent.bkrepo.helm.constants.SLEEP_MILLIS
import com.tencent.bkrepo.helm.constants.VERSION
import com.tencent.bkrepo.helm.exception.HelmBadRequestException
import com.tencent.bkrepo.helm.exception.HelmFileNotFoundException
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.pojo.metadata.HelmChartMetadata
import com.tencent.bkrepo.helm.pojo.metadata.HelmIndexYamlMetadata
import com.tencent.bkrepo.helm.service.ChartRepositoryService
import com.tencent.bkrepo.helm.utils.ChartParserUtil
import com.tencent.bkrepo.helm.utils.DecompressUtil.getArchivesContent
import com.tencent.bkrepo.helm.utils.HelmUtils
import com.tencent.bkrepo.helm.utils.TimeFormatUtil
import java.time.LocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChartRepositoryServiceImpl(
    private val helmProperties: HelmProperties
) : AbstractChartService(), ChartRepositoryService {

    @Permission(ResourceType.REPO, PermissionAction.READ)
    override fun queryIndexYaml(artifactInfo: HelmArtifactInfo) {
        downloadIndexYaml()
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
                    chartMetadata.created = convertDateTime(it[NODE_CREATE_DATE] as String)
                    chartMetadata.digest = it[NODE_SHA256] as String
                    ChartParserUtil.addIndexEntries(indexYamlMetadata, chartMetadata)
                } catch (ex: HelmFileNotFoundException) {
                    logger.error(
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

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun installTgz(artifactInfo: HelmArtifactInfo) {
        val context = ArtifactDownloadContext()
        when (context.repositoryDetail.category) {
            RepositoryCategory.REMOTE -> context.putAttribute(
                FULL_PATH,
                findRemoteArtifactFullPath(artifactInfo.getArtifactFullPath())
            )
            RepositoryCategory.LOCAL -> context.putAttribute(FULL_PATH, artifactInfo.getArtifactFullPath())
        }
        context.putAttribute(FILE_TYPE, CHART)
        try {
            ArtifactContextHolder.getRepository().download(context)
        } catch (e: Exception) {
            logger.error("Error occurred while installing chart, error: ${e.message}")
            throw HelmFileNotFoundException(e.message.toString())
        }
    }

    /**
     * 通过chart包名以及版本号查出对应remote仓库下载地址
     */
    private fun findRemoteArtifactFullPath(name: String): String {
        logger.info("get remote url for downloading...")
        val helmIndexYamlMetadata = queryOriginalIndexYaml()
        val chartName = ChartParserUtil.parseNameAndVersion(name)[NAME]
        val chartVersion = ChartParserUtil.parseNameAndVersion(name)[VERSION]
        val chartList =
            helmIndexYamlMetadata.entries[chartName]
                ?: throw HelmFileNotFoundException("File [$name] can not be found.")
        val helmChartMetadataList = chartList.filter {
            chartVersion == it.version
        }.toList()
        return if (helmChartMetadataList.isNotEmpty()) {
            require(helmChartMetadataList.size == 1) {
                "find more than one version [$chartVersion] in package [$chartName]."
            }
            val urls = helmChartMetadataList.first().urls
            if (urls.isNotEmpty()) {
                urls.first()
            } else {
                throw HelmFileNotFoundException("File [$name] can not be found.")
            }
        } else {
            throw HelmFileNotFoundException("File [$name] can not be found.")
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun installProv(artifactInfo: HelmArtifactInfo) {
        val context = ArtifactDownloadContext()
        when (context.repositoryDetail.category) {
            RepositoryCategory.REMOTE -> context.putAttribute(
                FULL_PATH,
                findRemoteArtifactFullPath(artifactInfo.getArtifactFullPath())
            )
            RepositoryCategory.LOCAL -> context.putAttribute(FULL_PATH, artifactInfo.getArtifactFullPath())
        }
        context.putAttribute(FILE_TYPE, PROV)
        try {
            ArtifactContextHolder.getRepository().download(context)
        } catch (e: Exception) {
            logger.error("Error occurred while installing prov, error: ${e.message}")
            throw HelmFileNotFoundException(e.message.toString())
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun regenerateIndexYaml(artifactInfo: HelmArtifactInfo) {
        when (getRepositoryInfo(artifactInfo).category) {
            RepositoryCategory.LOCAL -> {
                val nodeList = queryNodeList(artifactInfo, false)
                logger.info(
                    "query node list for full refresh index.yaml success in repo [${artifactInfo.getRepoIdentify()}]" +
                        ", size [${nodeList.size}], starting full refresh index.yaml ... "
                )
                val indexYamlMetadata = buildIndexYamlMetadata(nodeList, artifactInfo, true)
                uploadIndexYamlMetadata(indexYamlMetadata).also { logger.info("Full refresh index.yaml success！") }
            }
            else -> initIndexYaml(artifactInfo.projectId, artifactInfo.repoName)
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun batchInstallTgz(artifactInfo: HelmArtifactInfo, startTime: LocalDateTime) {
        val context = ArtifactQueryContext()
        when (context.repositoryDetail.category) {
            RepositoryCategory.LOCAL -> batchInstallLocalTgz(artifactInfo, startTime)
            RepositoryCategory.REMOTE -> throw HelmBadRequestException("illegal request")
        }
    }

    private fun batchInstallLocalTgz(artifactInfo: HelmArtifactInfo, startTime: LocalDateTime) {
        val nodeList = queryNodeList(artifactInfo, lastModifyTime = startTime)
        if (nodeList.isEmpty()) {
            throw HelmFileNotFoundException(
                "no chart found in repository [${artifactInfo.getRepoIdentify()}]"
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
    }
}
