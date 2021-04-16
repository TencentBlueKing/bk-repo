package com.tencent.bkrepo.migrate.dao.suyan

import com.tencent.bkrepo.migrate.model.suyan.TSuyanMavenArtifact
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface SuyanMavenArtifactDao : MongoRepository<TSuyanMavenArtifact, String> {
    fun findFirstById(id: String): TSuyanMavenArtifact?
    fun findFirstByRepositoryNameAndGroupIdAndArtifactIdAndVersionAndType(
        repositoryName: String,
        groupId: String,
        artifactId: String,
        version: String,
        type: String
    ): TSuyanMavenArtifact?

    fun findAllByRepositoryNameAndGroupIdAndArtifactId(
        repositoryName: String,
        groupId: String,
        artifactId: String
    ): List<TSuyanMavenArtifact>?
}
