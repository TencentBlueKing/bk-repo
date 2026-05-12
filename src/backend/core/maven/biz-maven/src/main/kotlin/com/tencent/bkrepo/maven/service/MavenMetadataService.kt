package com.tencent.bkrepo.maven.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.service.cluster.condition.DefaultCondition
import com.tencent.bkrepo.maven.dao.MavenMetadataDao
import com.tencent.bkrepo.maven.model.TMavenMetadataRecord
import com.tencent.bkrepo.maven.pojo.MavenGAVC
import com.tencent.bkrepo.maven.pojo.MavenMetadataSearchPojo
import com.tencent.bkrepo.maven.pojo.MavenVersion
import com.tencent.bkrepo.maven.pojo.metadata.MavenMetadataRequest
import com.tencent.bkrepo.maven.pojo.request.MavenArtifactSearchRequest
import com.tencent.bkrepo.maven.pojo.request.MavenGroupSearchRequest
import com.tencent.bkrepo.maven.pojo.request.MavenVersionSearchRequest
import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.maven.util.MavenStringUtils.resolverName
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
@Conditional(DefaultCondition::class)
class MavenMetadataService(
    private val mavenMetadataDao: MavenMetadataDao,
) {

    protected val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss")


    fun update(node: NodeDetail) = updateMetadata(node.projectId, node.repoName, node.nodeMetadata, node.fullPath)


    fun update(node: NodeCreateRequest) =
        updateMetadata(node.projectId, node.repoName, node.nodeMetadata, node.fullPath)

    private fun updateMetadata(
        projectId: String,
        repoName: String,
        metadata: List<MetadataModel>? = null,
        fullPath: String,
    ) {
        val (criteria, mavenVersion) = nodeCriteria(
            projectId = projectId,
            repoName = repoName,
            metadata = metadata?.associate { it.key to it.value },
            fullPath = fullPath
        )
        updateMetadata(criteria, mavenVersion)
    }

    private fun updateMetadata(criteria: Criteria?, mavenVersion: MavenVersion?) {
        if (criteria == null || mavenVersion == null) return
        val query = Query(criteria)
        val update = Update().set(TMavenMetadataRecord::timestamp.name, mavenVersion.timestamp)
            .set(TMavenMetadataRecord::buildNo.name, mavenVersion.buildNo ?: 0)
        val options = FindAndModifyOptions().apply { this.upsert(true).returnNew(false) }
        val returnData = mavenMetadataDao.determineMongoTemplate()
            .findAndModify(query, update, options, TMavenMetadataRecord::class.java)
        returnData?.let {
            logger.info(
                "Old meta data info: extension[${returnData.extension}]," +
                    " groupId[${returnData.groupId}], " +
                    " artifactId[${returnData.artifactId}], " +
                    " version[${returnData.version}]" +
                    " classifier[${returnData.classifier}]," +
                    " timestamp[${returnData.timestamp}]"
            )
        }
    }

    protected fun nodeCriteria(
        projectId: String,
        repoName: String,
        metadata: Map<String, Any>? = null,
        fullPath: String,
    ): Pair<Criteria?, MavenVersion?> {
        if (validateMetaData(metadata)) return Pair(null, null)
        val groupId = metadata?.get("groupId") as String
        val artifactId = metadata["artifactId"] as String
        val version = metadata["version"] as String
        logger.info(
            "Node info: groupId[$groupId], artifactId[$artifactId], version[$version], Node fullPath: $fullPath"
        )
        val criteria = Criteria.where(TMavenMetadataRecord::projectId.name).`is`(projectId)
            .and(TMavenMetadataRecord::repoName.name).`is`(repoName)
            .and(TMavenMetadataRecord::groupId.name).`is`(groupId)
            .and(TMavenMetadataRecord::artifactId.name).`is`(artifactId)
            .and(TMavenMetadataRecord::version.name).`is`(version)
        val mavenVersion =
            fullPath.substringAfterLast("/").resolverName(artifactId, version)
        criteria.and(TMavenMetadataRecord::extension.name).`is`(mavenVersion.packaging)
        if (mavenVersion.classifier == null) {
            criteria.and(TMavenMetadataRecord::classifier.name).exists(false)
        } else {
            criteria.and(TMavenMetadataRecord::classifier.name).`is`(mavenVersion.classifier)
        }
        logger.info(
            "Node info: extension[${mavenVersion.packaging}]," +
                " classifier[${mavenVersion.classifier}], buildNo[${mavenVersion.buildNo}]" +
                " timestamp[${mavenVersion.timestamp}] , fullPath: $fullPath"
        )
        return Pair(criteria, mavenVersion)
    }

    private fun validateMetaData(metadata: Map<String, Any>? = null): Boolean {
        if (metadata.isNullOrEmpty()) return true
        return (
            metadata["groupId"] == null ||
                metadata["artifactId"] == null ||
                metadata["version"] == null
            )
    }

    fun delete(mavenArtifactInfo: ArtifactInfo, node: NodeDetail? = null, mavenGavc: MavenGAVC? = null) {
        node?.let {
            val (criteria, mavenVersion) = nodeCriteria(
                projectId = node.projectId,
                repoName = node.repoName,
                metadata = node.metadata,
                fullPath = node.fullPath
            )
            if (criteria == null) return
            mavenVersion?.timestamp?.let {
                criteria.and(TMavenMetadataRecord::timestamp.name).`is`(mavenVersion.timestamp)
            }
            val query = Query(criteria)
            mavenMetadataDao.remove(query)
        }
        mavenGavc?.let {
            val groupId = mavenGavc.groupId
            val artifactId = mavenGavc.artifactId
            val version = mavenGavc.version
            logger.info(
                "Node info: groupId[$groupId], artifactId[$artifactId], version[$version]"
            )
            val criteria = Criteria.where(TMavenMetadataRecord::projectId.name).`is`(mavenArtifactInfo.projectId)
                .and(TMavenMetadataRecord::repoName.name).`is`(mavenArtifactInfo.repoName)
                .and(TMavenMetadataRecord::groupId.name).`is`(groupId)
                .and(TMavenMetadataRecord::artifactId.name).`is`(artifactId)
                .and(TMavenMetadataRecord::version.name).`is`(version)
            val query = Query(criteria)
            mavenMetadataDao.remove(query)
        }
    }

    fun search(mavenArtifactInfo: ArtifactInfo, mavenGavc: MavenGAVC): List<TMavenMetadataRecord> {
        logger.info(
            "Searching Node info: groupId[${mavenGavc.groupId}], artifactId[${mavenGavc.artifactId}], " +
                "version[${mavenGavc.version}], repoName: ${mavenArtifactInfo.repoName}, " +
                "projectId[${mavenArtifactInfo.projectId}]"
        )
        val criteria = Criteria.where(TMavenMetadataRecord::projectId.name).`is`(mavenArtifactInfo.projectId)
            .and(TMavenMetadataRecord::repoName.name).`is`(mavenArtifactInfo.repoName)
            .and(TMavenMetadataRecord::groupId.name).`is`(mavenGavc.groupId)
            .and(TMavenMetadataRecord::artifactId.name).`is`(mavenGavc.artifactId)
            .and(TMavenMetadataRecord::version.name).`is`(mavenGavc.version)
        val query = Query(criteria)
        return mavenMetadataDao.find(query)
    }

    fun findAndModify(mavenMetadataSearchPojo: MavenMetadataSearchPojo): TMavenMetadataRecord {
        logger.info(
            "findAndModify metadata groupId[${mavenMetadataSearchPojo.groupId}], " +
                "artifactId[${mavenMetadataSearchPojo.artifactId}], " +
                "version[${mavenMetadataSearchPojo.version}]," +
                "extension[${mavenMetadataSearchPojo.extension}]," +
                "classifier[${mavenMetadataSearchPojo.classifier}]"
        )
        val criteria = Criteria.where(TMavenMetadataRecord::projectId.name).`is`(mavenMetadataSearchPojo.projectId)
            .and(TMavenMetadataRecord::repoName.name).`is`(mavenMetadataSearchPojo.repoName)
            .and(TMavenMetadataRecord::groupId.name).`is`(mavenMetadataSearchPojo.groupId)
            .and(TMavenMetadataRecord::artifactId.name).`is`(mavenMetadataSearchPojo.artifactId)
            .and(TMavenMetadataRecord::version.name).`is`(mavenMetadataSearchPojo.version)
            .and(TMavenMetadataRecord::extension.name).`is`(mavenMetadataSearchPojo.extension)
        if (mavenMetadataSearchPojo.classifier == null) {
            criteria.and(TMavenMetadataRecord::classifier.name).exists(false)
        } else {
            criteria.and(TMavenMetadataRecord::classifier.name).`is`(mavenMetadataSearchPojo.classifier)
        }
        val query = Query(criteria)
        val update = Update().apply {
            this.set(TMavenMetadataRecord::timestamp.name, ZonedDateTime.now(ZoneId.of("UTC")).format(formatter))
                .inc(TMavenMetadataRecord::buildNo.name)
        }
        val options = FindAndModifyOptions().upsert(true).returnNew(true)
        return mavenMetadataDao.determineMongoTemplate()
            .findAndModify(query, update, options, TMavenMetadataRecord::class.java)!!
    }

    fun search(mavenMetadataSearchPojo: MavenMetadataSearchPojo): List<TMavenMetadataRecord>? {
        logger.info(
            "search metadata groupId[${mavenMetadataSearchPojo.groupId}], " +
                "artifactId[${mavenMetadataSearchPojo.artifactId}], " +
                "version[${mavenMetadataSearchPojo.version}]," +
                "extension[${mavenMetadataSearchPojo.extension}]," +
                "classifier[${mavenMetadataSearchPojo.classifier}]"
        )
        val criteria = Criteria.where(TMavenMetadataRecord::projectId.name).`is`(mavenMetadataSearchPojo.projectId)
            .and(TMavenMetadataRecord::repoName.name).`is`(mavenMetadataSearchPojo.repoName)
            .and(TMavenMetadataRecord::groupId.name).`is`(mavenMetadataSearchPojo.groupId)
            .and(TMavenMetadataRecord::artifactId.name).`is`(mavenMetadataSearchPojo.artifactId)
            .and(TMavenMetadataRecord::version.name).`is`(mavenMetadataSearchPojo.version)
            .and(TMavenMetadataRecord::extension.name).`is`(mavenMetadataSearchPojo.extension)
        if (mavenMetadataSearchPojo.classifier == null) {
            criteria.and(TMavenMetadataRecord::classifier.name).exists(false)
        } else {
            criteria.and(TMavenMetadataRecord::classifier.name).`is`(mavenMetadataSearchPojo.classifier)
        }
        val query = Query(criteria)
        return mavenMetadataDao.find(query, TMavenMetadataRecord::class.java)
    }

    fun search(artifactId: String, version: String, extension: String): List<TMavenMetadataRecord> {
        logger.info(
            "search metadata artifactId[${artifactId}], version[${version}]"
        )
        val criteria = Criteria.where(TMavenMetadataRecord::artifactId.name).`is`(artifactId)
            .and(TMavenMetadataRecord::version.name).`is`(version)
            .and(TMavenMetadataRecord::extension.name).`is`(extension)
            .and(TMavenMetadataRecord::classifier.name).isNull
        val query = Query(criteria)
        return mavenMetadataDao.find(query, TMavenMetadataRecord::class.java)
    }

    fun getGroupByPage(request: MavenGroupSearchRequest): Page<String> {
        val safePageNumber = if (request.pageNumber <= 0) 1 else request.pageNumber
        val safePageSize = if (request.pageSize < 0) 0 else request.pageSize
        val criteria = Criteria.where(TMavenMetadataRecord::projectId.name).`is`(request.projectId)
            .and(TMavenMetadataRecord::repoName.name).`is`(request.repoName)
        appendPrefixRegex(criteria, TMavenMetadataRecord::groupId.name, request.groupId)
        return distinctByPage(criteria, TMavenMetadataRecord::groupId.name, safePageNumber, safePageSize)
    }

    fun getArtifactByPage(request: MavenArtifactSearchRequest): Page<String> {
        val safePageNumber = if (request.pageNumber <= 0) 1 else request.pageNumber
        val safePageSize = if (request.pageSize < 0) 0 else request.pageSize
        val criteria = Criteria.where(TMavenMetadataRecord::projectId.name).`is`(request.projectId)
            .and(TMavenMetadataRecord::repoName.name).`is`(request.repoName)
            .and(TMavenMetadataRecord::groupId.name).`is`(request.groupId)
        appendPrefixRegex(criteria, TMavenMetadataRecord::artifactId.name, request.artifact)
        return distinctByPage(criteria, TMavenMetadataRecord::artifactId.name, safePageNumber, safePageSize)
    }

    fun getVersionByPage(request: MavenVersionSearchRequest): Page<String> {
        val safePageNumber = if (request.pageNumber <= 0) 1 else request.pageNumber
        val safePageSize = if (request.pageSize < 0) 0 else request.pageSize
        val criteria = Criteria.where(TMavenMetadataRecord::projectId.name).`is`(request.projectId)
            .and(TMavenMetadataRecord::repoName.name).`is`(request.repoName)
            .and(TMavenMetadataRecord::groupId.name).`is`(request.groupId)
            .and(TMavenMetadataRecord::artifactId.name).`is`(request.artifact)
        appendPrefixRegex(criteria, TMavenMetadataRecord::version.name, request.version)
        return distinctByPage(criteria, TMavenMetadataRecord::version.name, safePageNumber, safePageSize)
    }

    private fun distinctByPage(
        criteria: Criteria,
        aggregateField: String,
        pageNumber: Int,
        pageSize: Int
    ): Page<String> {

        if (pageSize == 0) {
            return Page(pageNumber, pageSize, 0, emptyList())
        }
        val query = Query(criteria)
        val allDistinct = mavenMetadataDao.determineMongoTemplate()
            .findDistinct(query, aggregateField, TMavenMetadataRecord::class.java, String::class.java)
        val total = allDistinct.size.toLong()
        val skip = (pageNumber - 1) * pageSize
        val results = allDistinct.drop(skip).take(pageSize)
        return Page(pageNumber, pageSize, total, results)
    }

    // 前缀匹配，区分大小写，可走 DISTINCT_SCAN 索引
    private fun appendPrefixRegex(criteria: Criteria, fieldName: String, keyword: String?) {
        val value = keyword?.trim().orEmpty()
        if (value.isEmpty()) return
        criteria.and(fieldName).regex("^${EscapeUtils.escapeRegex(value)}")
    }

    fun update(request: MavenMetadataRequest) {
        logger.info("update maven metadata: [$request]")
        mavenMetadataDao.findAndModify(request, incBuildNo = false, upsert = true, returnNew = false)
    }

    fun delete(request: MavenMetadataRequest) {
        logger.info("delete maven metadata: [$request]")
        mavenMetadataDao.delete(request)
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MavenMetadataService::class.java)
    }
}
