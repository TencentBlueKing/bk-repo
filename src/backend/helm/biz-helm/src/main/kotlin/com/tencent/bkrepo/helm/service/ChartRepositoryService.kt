package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.config.OCTET_STREAM
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.constants.DATA_TIME_FORMATTER
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.constants.NAME
import com.tencent.bkrepo.helm.constants.VERSION
import com.tencent.bkrepo.helm.pojo.IndexEntity
import com.tencent.bkrepo.helm.utils.DecompressUtil.getArchivesContent
import com.tencent.bkrepo.helm.utils.YamlUtils
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.util.NodeUtils
import com.tencent.bkrepo.repository.util.NodeUtils.FILE_SEPARATOR
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class ChartRepositoryService {

    @Value("\${helm.registry.domain: ''}")
    private lateinit var domain: String

    @Autowired
    private lateinit var nodeResource: NodeResource

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    fun getIndexYaml(artifactInfo: HelmArtifactInfo) {
        val context = ArtifactDownloadContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        context.contextAttributes[FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
        repository.download(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    fun regenerateIndexYaml(artifactInfo: HelmArtifactInfo) {
        val context = ArtifactSearchContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        val indexEntity = IndexEntity(apiVersion = "v1", generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATA_TIME_FORMATTER)))
        val nodeList =
            nodeResource.list(artifactInfo.projectId, artifactInfo.repoName, "/", includeFolder = false, deep = false).data ?: emptyList()
        logger.info("query node success, node list size [${nodeList.size}]")
        if (nodeList.isNotEmpty()) {
            nodeList.forEach { it ->
                Thread.sleep(20)
                try {
                    if (!it.name.endsWith("tgz")) return@forEach
                    context.contextAttributes[FULL_PATH] = it.fullPath
                    val artifactInputStream = repository.search(context) as ArtifactInputStream
                    val result = artifactInputStream.getArchivesContent("tgz")
                    val chartInfoMap = YamlUtils.convertStringToEntity<MutableMap<String, Any>>(result)
                    val chartName = chartInfoMap[NAME] as String
                    val chartVersion = chartInfoMap[VERSION] as String
                    chartInfoMap["digest"] = it.sha256 as String
                    chartInfoMap["created"] = convertDateTime(it.createdDate)
                    chartInfoMap["urls"] = listOf(
                        domain.trimEnd('/') + NodeUtils.formatFullPath(
                            "${artifactInfo.projectId}/${artifactInfo.repoName}/charts/$chartName-$chartVersion.tgz"
                        )
                    )
                    logger.info("helm chart $chartName , Info [$chartInfoMap]")
                    val isFirstChart = !indexEntity.entries.containsKey(chartName)
                    indexEntity.entries.let {
                        if (isFirstChart) {
                            it[chartName] = mutableListOf(chartInfoMap)
                        } else {
                            // force upload
                            run stop@{
                                it[chartName]?.forEachIndexed { index, chartMap ->
                                    if (chartVersion == chartMap[VERSION] as String) {
                                        it[chartName]?.removeAt(index)
                                        return@stop
                                    }
                                }
                            }
                            it[chartName]?.add(chartInfoMap)
                        }
                    }
                } catch (ex: Exception) {
                    logger.error("update index.yaml failed, message: ${ex.message}")
                }
            }
        }
        uploadIndexYaml(indexEntity)
        logger.info("regenerate index.yaml success！")
    }

    private fun convertDateTime(timeStr: String): String {
        val localDateTime = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_DATE_TIME)
        return localDateTime.format(DateTimeFormatter.ofPattern(DATA_TIME_FORMATTER))
    }

    private fun uploadIndexYaml(indexEntity: IndexEntity) {
        val artifactFile = ArtifactFileFactory.build(YamlUtils.transEntity2File(indexEntity).byteInputStream())
        val context = ArtifactUploadContext(artifactFile)
        context.contextAttributes[OCTET_STREAM + FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.upload(context)
    }


    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    fun installTgz(artifactInfo: HelmArtifactInfo) {
        val context = ArtifactDownloadContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        context.contextAttributes[FULL_PATH] = artifactInfo.artifactUri
        repository.download(context)
    }

    companion object{
        val logger: Logger = LoggerFactory.getLogger(ChartRepositoryService::class.java)
    }
}
