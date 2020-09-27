package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.repository.model.TPackageVersion.Companion.VERSION_METADATA_IDX
import com.tencent.bkrepo.repository.model.TPackageVersion.Companion.VERSION_METADATA_IDX_DEF
import com.tencent.bkrepo.repository.model.TPackageVersion.Companion.VERSION_NAME_IDX
import com.tencent.bkrepo.repository.model.TPackageVersion.Companion.VERSION_NAME_IDX_DEF
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("package_version")
@CompoundIndexes(
    CompoundIndex(name = VERSION_NAME_IDX, def = VERSION_NAME_IDX_DEF, background = true),
    CompoundIndex(name = VERSION_METADATA_IDX, def = VERSION_METADATA_IDX_DEF, background = true)
)
data class TPackageVersion(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var packageId: String,
    var name: String,
    var size: Long,
    var ordinal: Long,
    var downloads: Long,
    var manifestPath: String? = null,
    var artifactPath: String? = null,
    var stageTag: List<String>,
    var metadata: List<TMetadata>? = null
) {
    companion object {
        const val VERSION_NAME_IDX = "version_name_idx"
        const val VERSION_METADATA_IDX = "version_metadata_idx"

        const val VERSION_NAME_IDX_DEF = "{'packageId': 1, 'name': 1}"
        const val VERSION_METADATA_IDX_DEF = "{'packageId': 1, 'metadata': 1}"
    }
}