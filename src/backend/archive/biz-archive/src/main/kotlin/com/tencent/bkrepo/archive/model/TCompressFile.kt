package com.tencent.bkrepo.archive.model

import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.model.TCompressFile.Companion.SHA256_IDX
import com.tencent.bkrepo.archive.model.TCompressFile.Companion.SHA256_IDX_DEF
import com.tencent.bkrepo.archive.model.TCompressFile.Companion.STATUS_IDX
import com.tencent.bkrepo.archive.model.TCompressFile.Companion.STATUS_IDX_DEF
import java.time.LocalDateTime
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

@Document("compress_file")
@CompoundIndexes(
    CompoundIndex(name = SHA256_IDX, def = SHA256_IDX_DEF, unique = true, background = true),
    CompoundIndex(name = STATUS_IDX, def = STATUS_IDX_DEF, background = true),
)
@Suppress("LongParameterList")
class TCompressFile(
    id: String? = null,
    createdBy: String,
    createdDate: LocalDateTime,
    lastModifiedBy: String,
    lastModifiedDate: LocalDateTime,
    val sha256: String,
    val baseSha256: String,
    val baseSize: Long? = null,
    val uncompressedSize: Long,
    var compressedSize: Long = -1,
    val storageCredentialsKey: String?,
    var status: CompressStatus,
    var chainLength: Int = -1, // 只有队头元素有
) : AbstractEntity(
    id,
    createdBy,
    createdDate,
    lastModifiedBy,
    lastModifiedDate,
) {
    companion object {
        const val SHA256_IDX = "sha256_storageCredentialsKey_idx"
        const val SHA256_IDX_DEF = "{'sha256': 1,'storageCredentialsKey': 1}"
        const val STATUS_IDX = "status_idx"
        const val STATUS_IDX_DEF = "{'status': 1}"
    }
}
