package com.tencent.bkrepo.migrate.model.suyan

import com.tencent.bkrepo.migrate.model.suyan.TSuyanMavenArtifact.Companion.SUYAN_MAVEN_ARTIFACT_INDEX
import com.tencent.bkrepo.migrate.model.suyan.TSuyanMavenArtifact.Companion.SUYAN_MAVEN_ARTIFACT_INDEX_DEF
import com.tencent.bkrepo.migrate.model.suyan.TSuyanMavenArtifact.Companion.SUYAN_MAVEN_INDEX
import com.tencent.bkrepo.migrate.model.suyan.TSuyanMavenArtifact.Companion.SUYAN_MAVEN_INDEX_DEF
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("suyan_maven_artifact")
@CompoundIndexes(
    CompoundIndex(
        name = SUYAN_MAVEN_ARTIFACT_INDEX, def = SUYAN_MAVEN_ARTIFACT_INDEX_DEF,
        background = true, unique = true
    ),
    CompoundIndex(name = SUYAN_MAVEN_INDEX, def = SUYAN_MAVEN_INDEX_DEF, background = true)
)
data class TSuyanMavenArtifact(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    var repositoryName: String,
    var groupId: String,
    var artifactId: String,
    var type: String,
    var version: String,
    var productList: MutableSet<String>?
) {
    companion object {
        const val SUYAN_MAVEN_ARTIFACT_INDEX = "suyan_maven_artifact_index"
        const val SUYAN_MAVEN_ARTIFACT_INDEX_DEF =
            "{'repositoryName':1, 'groupId':1, 'artifactId': 1, 'type':1, 'version':1}"
        const val SUYAN_MAVEN_INDEX = "suyan_maven_index"
        const val SUYAN_MAVEN_INDEX_DEF = "{'repositoryName':1, 'groupId':1, 'artifactId': 1}"
    }
}
