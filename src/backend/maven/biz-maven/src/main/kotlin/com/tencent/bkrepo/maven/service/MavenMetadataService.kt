package com.tencent.bkrepo.maven.service

import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.dao.MavenMetadataDao
import com.tencent.bkrepo.maven.model.TMavenMetadataRecord
import com.tencent.bkrepo.maven.pojo.MavenGAVC
import com.tencent.bkrepo.maven.pojo.MavenMetadataSearchPojo
import com.tencent.bkrepo.maven.util.MavenStringUtils.resolverName
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
class MavenMetadataService(
    private val mavenMetadataDao: MavenMetadataDao
) {
    fun update(node: NodeCreateRequest) {
        val groupId = node.metadata?.get("groupId") as String
        val artifactId = node.metadata?.get("artifactId") as String
        val version = node.metadata?.get("version") as String
        val criteria = Criteria.where(TMavenMetadataRecord::projectId.name).`is`(node.projectId)
            .and(TMavenMetadataRecord::repoName.name).`is`(node.repoName)
            .and(TMavenMetadataRecord::groupId.name).`is`(groupId)
            .and(TMavenMetadataRecord::artifactId.name).`is`(artifactId)
            .and(TMavenMetadataRecord::version.name).`is`(version)
        val mavenVersion =
            node.fullPath.substringAfterLast("/").resolverName(artifactId, version)
        criteria.and(TMavenMetadataRecord::extension.name).`is`(mavenVersion.packaging)
        mavenVersion.classifier?.let {
            criteria.and(TMavenMetadataRecord::classifier.name).`is`(it)
        }
        val query = Query(criteria)
        val update = Update().set(TMavenMetadataRecord::timestamp.name, mavenVersion.timestamp)
            .set(TMavenMetadataRecord::buildNo.name, mavenVersion.buildNo?.toInt())
        val options = FindAndModifyOptions().apply { this.upsert(true).returnNew(false) }
        mavenMetadataDao.determineMongoTemplate()
            .findAndModify(query, update, options, TMavenMetadataRecord::class.java)
    }

    fun search(mavenArtifactInfo: MavenArtifactInfo, mavenGavc: MavenGAVC): List<TMavenMetadataRecord> {
        val criteria = Criteria.where(TMavenMetadataRecord::projectId.name).`is`(mavenArtifactInfo.projectId)
            .and(TMavenMetadataRecord::repoName.name).`is`(mavenArtifactInfo.repoName)
            .and(TMavenMetadataRecord::groupId.name).`is`(mavenGavc.groupId)
            .and(TMavenMetadataRecord::artifactId.name).`is`(mavenGavc.artifactId)
            .and(TMavenMetadataRecord::version.name).`is`(mavenGavc.version)
        val query = Query(criteria)
        return mavenMetadataDao.find(query)
    }

    fun findAndModify(mavenMetadataSearchPojo: MavenMetadataSearchPojo): TMavenMetadataRecord {
        val criteria = Criteria.where(TMavenMetadataRecord::projectId.name).`is`(mavenMetadataSearchPojo.projectId)
            .and(TMavenMetadataRecord::repoName.name).`is`(mavenMetadataSearchPojo.repoName)
            .and(TMavenMetadataRecord::groupId.name).`is`(mavenMetadataSearchPojo.groupId)
            .and(TMavenMetadataRecord::artifactId.name).`is`(mavenMetadataSearchPojo.artifactId)
            .and(TMavenMetadataRecord::version.name).`is`(mavenMetadataSearchPojo.version)
            .and(TMavenMetadataRecord::extension.name).`is`(mavenMetadataSearchPojo.extension)
        mavenMetadataSearchPojo.classifier?.let { criteria.and(TMavenMetadataRecord::classifier.name).`is`(it) }
        val query = Query(criteria)
        val update = Update().apply {
            this.set(TMavenMetadataRecord::timestamp.name, ZonedDateTime.now(ZoneId.of("UTC")).format(formatter))
            .inc(TMavenMetadataRecord::buildNo.name) }
        val options = FindAndModifyOptions().upsert(true).returnNew(true)
        return mavenMetadataDao.determineMongoTemplate()
            .findAndModify(query, update, options, TMavenMetadataRecord::class.java)!!
    }

    companion object {
        private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss")
    }
}
