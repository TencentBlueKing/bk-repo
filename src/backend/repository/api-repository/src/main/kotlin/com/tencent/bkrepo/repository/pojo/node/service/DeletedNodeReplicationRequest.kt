package com.tencent.bkrepo.repository.pojo.node.service

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 同步已删除节点
 */
@Schema(title = "同步已删除节点")
data class DeletedNodeReplicationRequest(
    @get:Schema(title = "节点元数据信息", required = true)
    val nodeCreateRequest: NodeCreateRequest,
    @get:Schema(title = "删除时间", required = true)
    val deleted: LocalDateTime,
)
