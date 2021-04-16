package com.tencent.bkrepo.migrate.model.suyan

import com.tencent.bkrepo.migrate.model.suyan.TFailedDockerImage.Companion.FAILED_DOCKER_IMAGE
import com.tencent.bkrepo.migrate.model.suyan.TFailedDockerImage.Companion.FAILED_DOCKER_IMAGE_DEF
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("failed_docker_image")
@CompoundIndexes(
    CompoundIndex(name = FAILED_DOCKER_IMAGE, def = FAILED_DOCKER_IMAGE_DEF, background = true, unique = true)
)
data class TFailedDockerImage(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    var project: String,
    var name: String,
    var tag: String
) {
    companion object {
        const val FAILED_DOCKER_IMAGE = "failed_docker_image"
        const val FAILED_DOCKER_IMAGE_DEF = "{'project':1, 'name':1, 'tag': 1}"
    }
}
