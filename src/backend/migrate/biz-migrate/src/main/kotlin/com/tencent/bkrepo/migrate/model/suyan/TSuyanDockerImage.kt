package com.tencent.bkrepo.migrate.model.suyan

import com.tencent.bkrepo.migrate.model.suyan.TSuyanDockerImage.Companion.SUYAN_DOCKER_IMAGE_INDEX
import com.tencent.bkrepo.migrate.model.suyan.TSuyanDockerImage.Companion.SUYAN_DOCKER_IMAGE_INDEX_DEF
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("suyan_docker_artifact")
@CompoundIndexes(
    CompoundIndex(name = SUYAN_DOCKER_IMAGE_INDEX, def = SUYAN_DOCKER_IMAGE_INDEX_DEF, background = true, unique = true)
)

data class TSuyanDockerImage(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    var project: String,
    var name: String,
    var tag: String,
    var productList: MutableSet<String>?
) {
    companion object {
        const val SUYAN_DOCKER_IMAGE_INDEX = "suyan_docker_image_index"
        const val SUYAN_DOCKER_IMAGE_INDEX_DEF = "{'project':1, 'name':1, 'tag': 1}"
    }
}
