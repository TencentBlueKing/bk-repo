package com.tencent.bkrepo.fs.server.model.drive

import com.tencent.bkrepo.common.api.mongo.ShardingDocument
import com.tencent.bkrepo.common.api.mongo.ShardingKey
import com.tencent.bkrepo.fs.server.model.drive.TDriveFileReference.Companion.COUNT_IDX
import com.tencent.bkrepo.fs.server.model.drive.TDriveFileReference.Companion.COUNT_IDX_DEF
import com.tencent.bkrepo.fs.server.model.drive.TDriveFileReference.Companion.SHA256_IDX
import com.tencent.bkrepo.fs.server.model.drive.TDriveFileReference.Companion.SHA256_IDX_DEF
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes

/**
 * Drive 文件摘要引用
 */
@ShardingDocument("drive_file_reference")
@CompoundIndexes(
    CompoundIndex(name = SHA256_IDX, def = SHA256_IDX_DEF, unique = true, background = true),
    CompoundIndex(name = COUNT_IDX, def = COUNT_IDX_DEF, background = true)
)
data class TDriveFileReference(
    var id: String? = null,
    @ShardingKey(count = SHARDING_COUNT)
    var sha256: String,
    var credentialsKey: String? = null,
    var count: Long
) {
    companion object {
        const val SHA256_IDX = "sha256_credentialsKey_idx"
        const val SHA256_IDX_DEF = "{'sha256': 1, 'credentialsKey': 1}"
        const val COUNT_IDX = "count_idx"
        const val COUNT_IDX_DEF = "{'count': 1}"
    }
}
