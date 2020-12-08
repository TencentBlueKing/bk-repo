package com.tencent.bkrepo.npm.job

import com.google.gson.JsonObject
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.npm.constants.DIST
import com.tencent.bkrepo.npm.constants.SIZE
import com.tencent.bkrepo.npm.constants.VERSIONS
import com.tencent.bkrepo.npm.utils.GsonUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

/**
 * 同步历史数据中package.json的dist里面packageSize数据
 */
@Component
class PackageMetadataSynchronizeJob {

    @Autowired
    private lateinit var repositoryClient: RepositoryClient

    @Autowired
    private lateinit var nodeClient: NodeClient

    @Autowired
    private lateinit var storageService: StorageService

    @Scheduled(cron = "0 0 0 11 12 ?")
    @SchedulerLock(name = "PackageMetadataSynchronizeJob", lockAtMostFor = "PT60M")
    fun run() {
        logger.info("starting synchronized npm package metadata")
        val repositoryList = repositoryClient.pageByType(0, 100, "NPM").data?.records ?: kotlin.run {
            logger.warn("no repository found, return")
            return
        }
        logger.info("find [${repositoryList.size}] NPM repository ${repositoryList.map { it.projectId to it.name }}")
        repositoryList.forEach {
            repairPackageMetadata(it.projectId, it.name)
        }
    }

    /**
     * 修复[projectId]-[repoName]仓库下的npm的package.json数据
     */
    private fun repairPackageMetadata(projectId: String, repoName: String) {
        var successCount = 0L
        var failedCount = 0L
        var totalCount = 0L
        val startTime = LocalDateTime.now()

        // 分页查询文件节点，以package.json文件为后缀
        var page = 1
        val packageMetadataPage = queryPackageMetadata(projectId, repoName, page)
        var packageMetadataList = packageMetadataPage.records.map { resolveNode(it) }
        if (packageMetadataList.isEmpty()) {
            logger.info("no package found in repo [$projectId/$repoName], skip.")
            return
        }
        while (packageMetadataList.isNotEmpty()) {
            packageMetadataList.forEach {
                logger.info("Retrieved ${packageMetadataList.size} records to repair, process: $totalCount/${packageMetadataPage.count}")
                val packageName = it.fullPath.removePrefix("/.npm/").removeSuffix("/package.json")
                try {
                    // 修复metadata
                    repairNode(projectId, repoName, packageName, it)
                    logger.info("Success to repair package [$packageName] in repo [$projectId/$repoName].")
                    successCount += 1
                } catch (exception: RuntimeException) {
                    logger.error(
                        "Failed to to repair package [$packageName] in repo [$projectId/$repoName].",
                        exception
                    )
                    failedCount += 1
                } finally {
                    totalCount += 1
                }
            }
            page += 1
            packageMetadataList = queryPackageMetadata(projectId, repoName, page).records.map { resolveNode(it) }
        }
        val durationSeconds = Duration.between(startTime, LocalDateTime.now()).seconds
        logger.info(
            "Repair npm package metadata file in repo [$projectId/$repoName], " +
                "total: $totalCount, success: $successCount, failed: $failedCount, duration $durationSeconds s totally."
        )
    }

    private fun repairNode(projectId: String, repoName: String, packageName: String, nodeInfo: NodeInfo) {
        val successVersionList = mutableListOf<String>()
        val existsAttrVersionList = mutableListOf<String>()

        val packageJson = storageService.load(nodeInfo.sha256!!, Range.full(nodeInfo.size), null)
            ?.use { GsonUtils.transferInputStreamToJson(it) }
            ?: throw IllegalStateException("src package json not found in repo [$projectId/$repoName]")
        val queryTgzNode = queryTgzNode(projectId, repoName, packageName)
        val iterator = packageJson.getAsJsonObject(VERSIONS).entrySet().iterator()
        var isModify = false
        while (iterator.hasNext()) {
            val next = iterator.next()
            val versionDistObject = next.value.asJsonObject.getAsJsonObject(DIST)
            if (!versionDistObject.has(SIZE)) {
                versionDistObject.addProperty(SIZE, queryTgzNode[next.key]?.size)
                isModify = true
                successVersionList.add(next.key)
            } else {
                existsAttrVersionList.add(next.key)
            }
        }
        if (isModify){
            storePackageArtifact(packageJson, nodeInfo)
            logger.info("repair package metadata for package [$packageName] success in repo [$projectId/$repoName], success versions: $successVersionList")
        } else {
            logger.info("the attribute is already exist in package metadata for package $packageName with version [$existsAttrVersionList] in repo [$projectId/$repoName], not to be repaired.")
        }
    }

    private fun storePackageArtifact(packageJson: JsonObject, nodeInfo: NodeInfo) {
        val artifactFile = ArtifactFileFactory.build(GsonUtils.gsonToInputStream(packageJson))
        with(nodeInfo) {
            val request = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                folder = folder,
                overwrite = true,
                size = artifactFile.getSize(),
                sha256 = artifactFile.getFileSha256(),
                md5 = artifactFile.getFileMd5()
            )
            storageService.store(request.sha256!!, artifactFile, null)
            artifactFile.delete()
            nodeClient.create(request)
        }
    }

    private fun queryTgzNode(
        projectId: String,
        repoName: String,
        packageName: String
    ): Map<String, NodeInfo> {
        val ruleList = mutableListOf<Rule>(
            Rule.QueryRule("projectId", projectId, OperationType.EQ),
            Rule.QueryRule("repoName", repoName, OperationType.EQ),
            Rule.QueryRule("folder", false, OperationType.EQ),
            Rule.QueryRule("fullPath", "/$packageName", OperationType.PREFIX),
            Rule.QueryRule("fullPath", "tgz", OperationType.SUFFIX)
        )
        val queryModel = QueryModel(
            page = PageLimit(0, 10000),
            sort = Sort(listOf("lastModifiedDate"), Sort.Direction.ASC),
            select = mutableListOf(),
            rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)
        )
        val queryResult = nodeClient.query(queryModel).data!!
        return queryResult.records.associateBy(
            { resolverVersion(packageName, it["fullPath"] as String) },
            { resolveNode(it) })
    }

    private fun resolverVersion(packageName: String, fullPath: String): String {
        val tgzFileName = fullPath.split('/').last()
        val last = packageName.split('/').last()
        return tgzFileName.removePrefix("$last-").removeSuffix(".tgz")
    }

    private fun queryPackageMetadata(
        projectId: String,
        repoName: String,
        page: Int
    ): Page<Map<String, Any>> {
        val ruleList = mutableListOf<Rule>(
            Rule.QueryRule("projectId", projectId, OperationType.EQ),
            Rule.QueryRule("repoName", repoName, OperationType.EQ),
            Rule.QueryRule("folder", false, OperationType.EQ),
            Rule.QueryRule("name", "package.json", OperationType.EQ)
        )
        val queryModel = QueryModel(
            page = PageLimit(page, pageSize),
            // sort = Sort(listOf("lastModifiedDate"), Sort.Direction.ASC),
            sort = null,
            select = mutableListOf(),
            rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)
        )
        return nodeClient.query(queryModel).data!!
    }

    private fun resolveNode(record: Map<String, Any>): NodeInfo {
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
            metadata = mapOf()
        )
    }

    companion object {
        private const val pageSize = 10000
        private val logger: Logger = LoggerHolder.jobLogger
    }
}