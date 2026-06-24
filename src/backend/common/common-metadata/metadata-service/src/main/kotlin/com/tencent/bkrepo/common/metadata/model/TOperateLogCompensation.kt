package com.tencent.bkrepo.common.metadata.model

import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

/** §20.3.2：Pipeline 等主路径写 node 成功后，oplog 写入失败时的补偿队列。 */
@Document("artifact_oplog_compensation")
data class TOperateLogCompensation(
    val id: String? = null,
    val log: TOperateLog,
    val retryCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastError: String? = null,
    /** PENDING / DONE / FAILED */
    val status: String = STATUS_PENDING,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
        const val MAX_RETRY = 5
    }
}
