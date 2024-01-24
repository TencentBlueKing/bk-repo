package com.tencent.bkrepo.archive.model

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.model.TArchiveFile.Companion.SHA256_IDX
import com.tencent.bkrepo.archive.model.TArchiveFile.Companion.SHA256_IDX_DEF
import com.tencent.bkrepo.archive.model.TArchiveFile.Companion.STATUS_IDX
import com.tencent.bkrepo.archive.model.TArchiveFile.Companion.STATUS_IDX_DEF
import java.time.LocalDateTime
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

@Document("archive_file")
@CompoundIndexes(
    CompoundIndex(name = SHA256_IDX, def = SHA256_IDX_DEF, unique = true, background = true),
    CompoundIndex(name = STATUS_IDX, def = STATUS_IDX_DEF, background = true),
)
@Suppress("LongParameterList")
class TArchiveFile(
    id: String? = null,
    createdBy: String,
    createdDate: LocalDateTime,
    lastModifiedBy: String,
    lastModifiedDate: LocalDateTime,
    val sha256: String,
    val size: Long,
    val storageCredentialsKey: String?,
    var status: ArchiveStatus,
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
