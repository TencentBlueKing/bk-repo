package com.tencent.bkrepo.common.metadata.model

import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

@Document("node_file_ref_compensation")
data class TFileRefCompensation(
    val id: String? = null,
    val sha256: String,
    val credentialsKey: String?,
    val retryCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    /** PENDING / DONE / FAILED */
    val status: String = STATUS_PENDING,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
        const val MAX_RETRY = 3
    }
}
