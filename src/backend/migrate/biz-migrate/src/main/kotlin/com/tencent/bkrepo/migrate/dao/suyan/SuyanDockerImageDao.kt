package com.tencent.bkrepo.migrate.dao.suyan

import com.tencent.bkrepo.migrate.model.suyan.TSuyanDockerImage
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface SuyanDockerImageDao : MongoRepository<TSuyanDockerImage, String> {
    fun findFirstById(id: String): TSuyanDockerImage?
    fun findFirstByProjectAndNameAndTag(
        project: String,
        name: String,
        tag: String
    ): TSuyanDockerImage?
}
