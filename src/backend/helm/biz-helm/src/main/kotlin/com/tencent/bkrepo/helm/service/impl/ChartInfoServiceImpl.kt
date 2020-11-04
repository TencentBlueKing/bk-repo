package com.tencent.bkrepo.helm.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.constants.CHART_NOT_FOUND
import com.tencent.bkrepo.helm.constants.NAME
import com.tencent.bkrepo.helm.constants.NODE_FULL_PATH
import com.tencent.bkrepo.helm.constants.NODE_METADATA
import com.tencent.bkrepo.helm.constants.NODE_METADATA_NAME
import com.tencent.bkrepo.helm.constants.NODE_METADATA_VERSION
import com.tencent.bkrepo.helm.constants.NO_CHART_NAME_FOUND
import com.tencent.bkrepo.helm.constants.PROJECT_ID
import com.tencent.bkrepo.helm.constants.REPO_NAME
import com.tencent.bkrepo.helm.constants.REPO_TYPE
import com.tencent.bkrepo.helm.exception.HelmFileNotFoundException
import com.tencent.bkrepo.helm.model.metadata.HelmIndexYamlMetadata
import com.tencent.bkrepo.helm.pojo.user.BasicInfo
import com.tencent.bkrepo.helm.pojo.user.PackageVersionInfo
import com.tencent.bkrepo.helm.service.ChartInfoService
import com.tencent.bkrepo.helm.service.ChartRepositoryService
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.apache.http.HttpStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ChartInfoServiceImpl(
    private val chartRepositoryService: ChartRepositoryService
) : AbstractService(), ChartInfoService {
    @Permission(ResourceType.REPO, PermissionAction.READ)
    override fun allChartsList(artifactInfo: HelmArtifactInfo, startTime: LocalDateTime?): Map<String, Any> {
        if (startTime != null) {
            val nodeList = queryNodeList(artifactInfo, lastModifyTime = startTime)
            val indexYamlMetadata = chartRepositoryService.buildIndexYamlMetadata(nodeList, artifactInfo, true)
            return indexYamlMetadata.entries
        }
        chartRepositoryService.freshIndexFile(artifactInfo)
        val indexYamlMetadata = getOriginalIndexYaml()
        return searchJson(indexYamlMetadata, artifactInfo.getArtifactFullPath())
    }

    private fun searchJson(indexYamlMetadata: HelmIndexYamlMetadata, urls: String): Map<String, Any> {
        val urlList = urls.removePrefix("/").split("/").filter { it.isNotBlank() }
        return when (urlList.size) {
            // Without name and version
            0 -> {
                indexYamlMetadata.entries
            }
            // query with name
            1 -> {
                val chartName = urlList[0]
                val chartList = indexYamlMetadata.entries[chartName]
                chartList?.let { mapOf(chartName to chartList) } ?: CHART_NOT_FOUND
            }
            // query with name and version
            2 -> {
                val chartName = urlList[0]
                val chartVersion = urlList[1]
                val chartList = indexYamlMetadata.entries[chartName] ?: return NO_CHART_NAME_FOUND
                val chartVersionList = chartList.filter { chartVersion == it.version }.toList()
                if (chartVersionList.isNotEmpty()) {
                    mapOf(chartName to chartVersionList)
                } else {
                    mapOf("error" to "no chart version found for $chartName-$chartVersion")
                }
            }
            else -> {
                // ERROR_NOT_FOUND
                CHART_NOT_FOUND
            }
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    override fun isExists(artifactInfo: HelmArtifactInfo) {
        val response = HttpContextHolder.getResponse()
        val status: Int = with(artifactInfo) {
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
                val nodeList: List<Map<String, Any?>>? = nodeClient.query(queryModel).data?.records
                if (nodeList.isNullOrEmpty()) HttpStatus.SC_NOT_FOUND else HttpStatus.SC_OK
            } else {
                HttpStatus.SC_NOT_FOUND
            }
        }
        response.status = status
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
            val nodeDetail = nodeClient.detail(projectId, repoName, REPO_TYPE, fullPath).data ?: run {
                logger.warn("node [$fullPath] don't found.")
                throw HelmFileNotFoundException("node [$fullPath] don't found.")
            }
            val packageVersion = packageClient.findVersionByName(projectId, repoName, packageKey, version).data ?: run {
                logger.warn("packageKey [$packageKey] don't found.")
                throw HelmFileNotFoundException("packageKey [$packageKey] don't found.")
            }
            val basicInfo = buildBasicInfo(nodeDetail, packageVersion)
            return PackageVersionInfo(basicInfo, emptyMap())
        }
    }

    companion object {
        const val CURRENT_PAGE = 0
        const val SIZE = 5

        val logger: Logger = LoggerFactory.getLogger(ChartInfoServiceImpl::class.java)

        fun buildBasicInfo(nodeDetail: NodeDetail, packageVersion: PackageVersion): BasicInfo {
            with(nodeDetail) {
                return BasicInfo(
                    packageVersion.name,
                    fullPath,
                    size,
                    sha256.orEmpty(),
                    md5.orEmpty(),
                    packageVersion.stageTag,
                    projectId,
                    repoName,
                    packageVersion.downloads,
                    createdBy,
                    createdDate,
                    lastModifiedBy,
                    lastModifiedDate
                )
            }
        }
    }
}