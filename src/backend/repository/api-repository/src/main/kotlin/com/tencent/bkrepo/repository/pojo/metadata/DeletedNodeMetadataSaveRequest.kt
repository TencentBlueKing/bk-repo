package com.tencent.bkrepo.repository.pojo.metadata

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime


/**
 * 针对已删除节点创建或更新元数据请求
 */
@Schema(title = "针对已删除节点创建或更新元数据请求")
data class DeletedNodeMetadataSaveRequest(
    @get:Schema(title = "节点元数据信息", required = true)
    val metadataSaveRequest: MetadataSaveRequest,
    @get:Schema(title = "删除时间", required = true)
    val deleted: LocalDateTime,
)
