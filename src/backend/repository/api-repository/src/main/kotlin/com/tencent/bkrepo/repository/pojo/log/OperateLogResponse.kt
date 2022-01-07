package com.tencent.bkrepo.repository.pojo.log

import io.swagger.annotations.Api
import java.time.LocalDateTime

@Api("操作日志")
data class OperateLogResponse(
    val createdDate: LocalDateTime,
    val operate: String,
    val userId: String,
    val clientAddress: String,
    val result: Boolean,
    val content: Content
) {
    open class Content(
        val projectId: String? = null,
        val repoType: String? = null,
        val resKey: String,
        val des: String? = null
    )
}
