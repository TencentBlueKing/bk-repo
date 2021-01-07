package com.tencent.bkrepo.helm.service.impl

import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.readYamlString
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.service.exception.ExternalErrorCodeException
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.async.HelmPackageHandler
import com.tencent.bkrepo.helm.constants.CHART_PACKAGE_FILE_EXTENSION
import com.tencent.bkrepo.helm.model.metadata.HelmChartMetadata
import com.tencent.bkrepo.helm.pojo.fixtool.PackageManagerResponse
import com.tencent.bkrepo.helm.service.FixToolService
import com.tencent.bkrepo.helm.utils.DecompressUtil.getArchivesContent
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime

@Service
class FixToolServiceImpl(
    private val storageService: StorageService,
    private val helmPackageHandler: HelmPackageHandler
) : FixToolService, AbstractChartService() {
    override fun fixPackageVersion(): List<PackageManagerResponse> {
        val packageManagerList = mutableListOf<PackageManagerResponse>()
        // 查找所有仓库
        logger.info("starting add package manager function to historical data")
        val repositoryList = repositoryClient.pageByType(0, 1000, "HELM").data?.records ?: run {
            logger.warn("no npm repository found, return.")
            return emptyList()
        }
        logger.info("find [${repositoryList.size}] NPM repository ${repositoryList.map { it.projectId to it.name }}")
        repositoryList.forEach {
            val packageManagerResponse = addPackageManager(it.projectId, it.name)
            packageManagerList.add(packageManagerResponse.copy(projectId = it.projectId, repoName = it.name))
        }
        return packageManagerList
    }

    private fun addPackageManager(projectId: String, repoName: String): PackageManagerResponse {
        // 查询仓库下面的所有package的包
        var successCount = 0L
        var failedCount = 0L
        var totalCount = 0L
        val failedSet = mutableSetOf<String>()
        val startTime = LocalDateTime.now()

        // 分页查询文件节点，以package.json文件为后缀
        var page = 1
        val packageMetadataPage = queryPackagePage(projectId, repoName, page)
        var packageMetadataList = packageMetadataPage.records.map { resolveNode(it) }
        if (packageMetadataList.isEmpty()) {
            logger.info("no package found in repo [$projectId/$repoName], skip.")
            return PackageManagerResponse(totalCount = 0, successCount = 0, failedCount = 0, failedSet = emptySet())
        }
        while (packageMetadataList.isNotEmpty()) {
            packageMetadataList.forEach {
                logger.info(
                    "Retrieved ${packageMetadataList.size} records to add package manager, " +
                        "process: $totalCount/${packageMetadataPage.totalRecords}"
                )
                var name = ""
                try {
                    // 添加包管理
                    val helmChartMetadata = doAddPackageManager(it.createdBy, projectId, repoName, it)
                    name = helmChartMetadata.name
                    logger.info("Success to add package manager for [$name] in repo [$projectId/$repoName].")
                    successCount += 1
                } catch (exception: RuntimeException) {
                    logger.error(
                        "Failed to to add package manager for [$name] in repo [$projectId/$repoName].",
                        exception
                    )
                    failedSet.add(name)
                    failedCount += 1
                } finally {
                    totalCount += 1
                }
            }
            page += 1
            packageMetadataList = queryPackagePage(projectId, repoName, page).records.map { resolveNode(it) }
        }
        val durationSeconds = Duration.between(startTime, LocalDateTime.now()).seconds
        logger.info(
            "Repair npm package metadata file in repo [$projectId/$repoName], " +
                "total: $totalCount, success: $successCount, failed: $failedCount, duration $durationSeconds s totally."
        )
        return PackageManagerResponse(
            totalCount = totalCount,
            successCount = successCount,
            failedCount = failedCount,
            failedSet = failedSet
        )
    }

    private fun doAddPackageManager(
        userId: String,
        projectId: String,
        repoName: String,
        nodeInfo: NodeInfo
    ): HelmChartMetadata {
        val artifactInfo = HelmArtifactInfo(projectId, repoName, "")
        val helmChartMetadata = storageService.load(nodeInfo.sha256!!, Range.full(nodeInfo.size), null)
            ?.use {
                it.getArchivesContent(CHART_PACKAGE_FILE_EXTENSION).byteInputStream()
                    .readYamlString<HelmChartMetadata>()
            }
            ?: throw IllegalStateException("src node not found in repo [$projectId/$repoName]")
        try {
            helmPackageHandler.createVersion(userId, artifactInfo, helmChartMetadata, nodeInfo.size)
        } catch (exception: ExternalErrorCodeException) {
            if (exception.errorMessage == CommonMessageCode.RESOURCE_EXISTED.getKey()) {
                logger.warn(
                    "the package manager for [${helmChartMetadata.name}] with version [${helmChartMetadata.version}] is already exists" +
                        " in repo [$projectId/$repoName], skip."
                )
                return helmChartMetadata
            }
            logger.error(
                "add package manager for [${helmChartMetadata.name}] with version [${helmChartMetadata.version}] failed " +
                    "in repo [$projectId/$repoName]."
            )
            throw exception
        }
        logger.info("add package manager for package [${helmChartMetadata.name}] success in repo [$projectId/$repoName]")
        return helmChartMetadata
    }

    private fun queryPackagePage(
        projectId: String,
        repoName: String,
        page: Int
    ): Page<Map<String, Any?>> {
        val ruleList = mutableListOf<Rule>(
            Rule.QueryRule("projectId", projectId, OperationType.EQ),
            Rule.QueryRule("repoName", repoName, OperationType.EQ),
            Rule.QueryRule("folder", false, OperationType.EQ),
            Rule.QueryRule("fullPath", "tgz", OperationType.SUFFIX)
        )
        val queryModel = QueryModel(
            page = PageLimit(page, pageSize),
            sort = null,
            select = mutableListOf(),
            rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)
        )
        return nodeClient.search(queryModel).data!!
    }

    private fun resolveNode(record: Map<String, Any?>): NodeInfo {
        return NodeInfo(
            createdBy = record["createdBy"] as String,
            createdDate = record["createdDate"] as String,
            lastModifiedBy = record["lastModifiedBy"] as String,
            lastModifiedDate = record["lastModifiedDate"] as String,
            folder = record["folder"] as Boolean,
            path = record["path"] as String,
            name = record["name"] as String,
            fullPath = record["fullPath"] as String,
            size = record["size"].toString().toLong(),
            sha256 = record["sha256"] as String,
            md5 = record["md5"] as String,
            projectId = record["projectId"] as String,
            repoName = record["repoName"] as String,
            metadata = null
        )
    }

    companion object {
        private const val pageSize = 10000
        private val logger = LoggerFactory.getLogger(FixToolServiceImpl::class.java)
    }
}