package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import com.tencent.bkrepo.common.artifact.config.OCTET_STREAM
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.constants.DATA_TIME_FORMATTER
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.constants.NAME
import com.tencent.bkrepo.helm.constants.VERSION
import com.tencent.bkrepo.helm.lock.MongoLock
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

    @Autowired
    private lateinit var mongoLock: MongoLock

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun getIndexYaml(artifactInfo: HelmArtifactInfo) {
        var isLock = false
        try {
            isLock = mongoLock.getLock(LOCK_KEY)
            if(isLock){
                freshIndexFile(artifactInfo)
            }
        } finally {
            if(isLock){
                mongoLock.releaseLock(LOCK_KEY)
            }
        }
        downloadIndexYaml()
    }

    fun freshIndexFile(artifactInfo: HelmArtifactInfo) {
        // 先查询index.yaml文件，如果不存在则创建，
        // 存在则根据最后一次更新时间与node节点创建时间对比进行增量更新
        val exist = nodeResource.exist(artifactInfo.projectId, artifactInfo.repoName, INDEX_CACHE_YAML).data!!
        if (!exist) {
            val indexEntity = initIndexEntity()
            val nodeList = queryNodeList(artifactInfo, false)
            logger.info("query node list success, size [${nodeList.size}]")
            if (nodeList.isNotEmpty()) {
                logger.info("start generate index.yaml ... ")
                generateIndexFile(nodeList, indexEntity, artifactInfo)
            }
            uploadIndexYaml(indexEntity).also { logger.info("generate index.yaml success！") }
            return
        }

        val indexEntity = getOriginalIndexYaml()
        val dateTime = indexEntity.generated.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME) }
        val now = LocalDateTime.now()
        val nodeList = queryNodeList(artifactInfo, lastModifyTime = dateTime)
        if (nodeList.isNotEmpty()) {
            logger.info("start regenerate index.yaml ... ")
            generateIndexFile(nodeList, indexEntity, artifactInfo)
            indexEntity.generated = now.format(DateTimeFormatter.ofPattern(DATA_TIME_FORMATTER))
            uploadIndexYaml(indexEntity).also { logger.info("regenerate index.yaml success！") }
        }
    }

    private fun queryNodeList(
        artifactInfo: HelmArtifactInfo,
        exist: Boolean = true,
        lastModifyTime: LocalDateTime? = null
    ): List<Map<String, Any>> {
        val projectRule = Rule.QueryRule("projectId", artifactInfo.projectId)
        val repoNameRule = Rule.QueryRule("repoName", artifactInfo.repoName)
        val fullPathRule = Rule.QueryRule("fullPath", ".tgz", OperationType.SUFFIX)
        var createDateRule: Rule.QueryRule? = null
        if (exist) {
            createDateRule = lastModifyTime?.let { Rule.QueryRule("createdDate", it, OperationType.AFTER) }
        }
        val queryRuleList = mutableListOf(projectRule, repoNameRule, fullPathRule)
        createDateRule?.let { queryRuleList.add(it) }
        val rule = Rule.NestedRule(queryRuleList.toMutableList())
        val queryModel = QueryModel(
            page = PageLimit(0, 100000),
            sort = Sort(listOf("fullPath"), Sort.Direction.ASC),
            select = mutableListOf("projectId", "repoName", "name", "fullPath", "metadata", "sha256", "createdDate"),
            rule = rule
        )
        val result = nodeResource.query(queryModel).data ?: run {
            logger.warn("don't find node list in repository: [${artifactInfo.projectId}, ${artifactInfo.repoName}]!")
            return emptyList()
        }
        return result.records
    }

    @Suppress("UNCHECKED_CAST")
    private fun generateIndexFile(
        result: List<Map<String, Any>>,
        indexEntity: IndexEntity,
        artifactInfo: HelmArtifactInfo
    ) {
        val context = ArtifactSearchContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        result.forEach { it ->
            Thread.sleep(20)
            context.contextAttributes[FULL_PATH] = it["fullPath"] as String
            var chartName: String? = null
            var chartVersion: String? = null
            try {
                val artifactInputStream = repository.search(context) as ArtifactInputStream
                val content = artifactInputStream.use { it.getArchivesContent("tgz") }
                val chartInfoMap = YamlUtils.convertStringToEntity<MutableMap<String, Any>>(content)
                chartName = chartInfoMap[NAME] as String
                chartVersion = chartInfoMap[VERSION] as String
                chartInfoMap["urls"] = listOf(
                    domain.trimEnd('/') + NodeUtils.formatFullPath(
                        "${artifactInfo.projectId}/${artifactInfo.repoName}/charts/$chartName-$chartVersion.tgz"
                    )
                )
                chartInfoMap["created"] = convertDateTime(it["createdDate"] as String)
                chartInfoMap["digest"] = it["sha256"] as String
                addIndexEntries(indexEntity, chartInfoMap)
            } catch (ex: Exception) {
                logger.error("generate IndexFile for chart [$chartName-$chartVersion.tgz] failed!")
            }
        }
    }

    private fun uploadIndexYaml(indexEntity: IndexEntity) {
        val artifactFile = ArtifactFileFactory.build(YamlUtils.transEntityToStream(indexEntity))
        val context = ArtifactUploadContext(artifactFile)
        context.contextAttributes[OCTET_STREAM + FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.upload(context)
    }

    private fun initIndexEntity(): IndexEntity {
        return IndexEntity(
            apiVersion = "v1",
            generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATA_TIME_FORMATTER))
        )
    }

    private fun addIndexEntries(
        indexEntity: IndexEntity,
        chartInfoMap: MutableMap<String, Any>
    ) {
        val chartName = chartInfoMap[NAME] as String
        val chartVersion = chartInfoMap[VERSION] as String
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
    }

    private fun getOriginalIndexYaml(): IndexEntity {
        val context = ArtifactSearchContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        context.contextAttributes[FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
        val indexMap = (repository.search(context) as ArtifactInputStream).run {
            YamlUtils.convertFileToEntity<Map<String, Any>>(this)
        }
        logger.info("search original $INDEX_CACHE_YAML success!")
        return objectMapper.convertValue(indexMap, IndexEntity::class.java)
    }

    fun downloadIndexYaml() {
        val context = ArtifactDownloadContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        context.contextAttributes[FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
        repository.download(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    fun regenerateIndexYaml(artifactInfo: HelmArtifactInfo) {
        val indexEntity = initIndexEntity()
        val nodeList = queryNodeList(artifactInfo, false)
        logger.info("query node list for full refresh index.yaml success, size [${nodeList.size}]")
        if (nodeList.isNotEmpty()) {
            logger.info("start full refresh index.yaml ... ")
            generateIndexFile(nodeList, indexEntity, artifactInfo)
        }
        uploadIndexYaml(indexEntity).also { logger.info("Full refresh index.yaml success！") }
    }

    private fun convertDateTime(timeStr: String): String {
        val localDateTime = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_DATE_TIME)
        return localDateTime.format(DateTimeFormatter.ofPattern(DATA_TIME_FORMATTER))
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    fun installTgz(artifactInfo: HelmArtifactInfo) {
        val context = ArtifactDownloadContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        context.contextAttributes[FULL_PATH] = artifactInfo.artifactUri
        repository.download(context)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ChartRepositoryService::class.java)
        const val LOCK_KEY = "chart_index"
    }
}
