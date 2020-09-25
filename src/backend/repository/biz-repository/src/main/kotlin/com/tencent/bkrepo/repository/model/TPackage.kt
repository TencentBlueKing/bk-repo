package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.repository.model.TPackage.Companion.PACKAGE_KEY_IDX
import com.tencent.bkrepo.repository.model.TPackage.Companion.PACKAGE_KEY_IDX_DEF
import com.tencent.bkrepo.repository.model.TPackage.Companion.PACKAGE_NAME_IDX
import com.tencent.bkrepo.repository.model.TPackage.Companion.PACKAGE_NAME_IDX_DEF
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 包模型
 */
@Document("package")
@CompoundIndexes(
    CompoundIndex(name = PACKAGE_NAME_IDX, def = PACKAGE_NAME_IDX_DEF, background = true),
    CompoundIndex(name = PACKAGE_KEY_IDX, def = PACKAGE_KEY_IDX_DEF, background = true, unique = true)
)
data class TPackage(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var projectId: String,
    var repoName: String,
    var name: String,
    var key: String,
    var type: PackageType,
    var latest: String? = null,
    var downloads: Long,
    var versions: Long,
    var description: String? = null
) {
    companion object {
        const val PACKAGE_NAME_IDX = "package_name_idx"
        const val PACKAGE_KEY_IDX = "package_key_idx"
        const val PACKAGE_NAME_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'name': 1}"
        const val PACKAGE_KEY_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'key': 1}"
    }
}