package com.tencent.bkrepo.migrate.dao.suyan

import com.tencent.bkrepo.migrate.model.suyan.TFailedDockerImage
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface FailedDockerImageDao : MongoRepository<TFailedDockerImage, String> {
    fun findFirstById(id: String): TFailedDockerImage?
    fun findFirstByProjectAndNameAndTag(
        project: String,
        name: String,
        tag: String
    ): TFailedDockerImage?
}
