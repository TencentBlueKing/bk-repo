package com.tencent.bkrepo.maven.service

import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.dao.MavenMetadataDao
import com.tencent.bkrepo.maven.model.TMavenMetadataRecord
import com.tencent.bkrepo.maven.pojo.MavenGAVC
import com.tencent.bkrepo.maven.util.MavenStringUtils.resolverName
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

@Service
class MavenMetadataService(
    private val mavenMetadataDao: MavenMetadataDao
) {
    fun update(mavenArtifactInfo: MavenArtifactInfo) {
        val criteria = Criteria.where(TMavenMetadataRecord::projectId.name).`is`(mavenArtifactInfo.projectId)
            .and(TMavenMetadataRecord::repoName.name).`is`(mavenArtifactInfo.repoName)
            .and(TMavenMetadataRecord::groupId.name).`is`(mavenArtifactInfo.groupId)
            .and(TMavenMetadataRecord::artifactId.name).`is`(mavenArtifactInfo.artifactId)
            .and(TMavenMetadataRecord::version.name).`is`(mavenArtifactInfo.versionId)
        val mavenVersion =
            mavenArtifactInfo.jarName.resolverName(mavenArtifactInfo.artifactId, mavenArtifactInfo.versionId)
        criteria.and(TMavenMetadataRecord::extension.name).`is`(mavenVersion.packaging)
        mavenVersion.classifier?.let {
            criteria.and(TMavenMetadataRecord::classifier.name).`is`(it)
        }
        val query = Query(criteria)
        val update = Update().set(TMavenMetadataRecord::timestamp.name, mavenVersion.timestamp)
            .set(TMavenMetadataRecord::buildNo.name, mavenVersion.buildNo)
        mavenMetadataDao.upsert(query, update)
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
}
