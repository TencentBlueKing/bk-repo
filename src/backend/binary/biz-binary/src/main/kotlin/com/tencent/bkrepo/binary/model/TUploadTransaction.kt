package com.tencent.bkrepo.binary.model

import com.tencent.bkrepo.binary.constant.UploadStatusEnum
import java.time.LocalDateTime

/**
 * 上传事物信息
 *
 * @author: carrypan
 * @date: 2019-09-30
 */
data class TUploadTransaction(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var projectId: String,
    var repoName: String,
    var fullPath: String,
    var status: UploadStatusEnum
)
