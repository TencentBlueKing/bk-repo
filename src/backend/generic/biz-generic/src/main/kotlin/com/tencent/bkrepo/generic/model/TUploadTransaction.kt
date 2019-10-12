package com.tencent.bkrepo.generic.model

import com.tencent.bkrepo.generic.constant.UploadStatusEnum
import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 上传事物信息
 *
 * @author: carrypan
 * @date: 2019-09-30
 */
@Document("upload_transaction")
data class TUploadTransaction(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var projectId: String,
    var repoName: String,
    var fullPath: String,
    var status: UploadStatusEnum,
    val expires: Long,
    val overwrite: Boolean
)
