package com.tencent.bkrepo.repository.pojo.event

import com.tencent.bkrepo.common.artifact.event.base.EventType

data class EventCreateRequest(
    /**
     * 事件类型
     */
    val type: EventType,
    /**
     * 项目id
     */
    val projectId: String? = null,
    /**
     * 仓库名称
     */
    val repoName: String? = null,
    /**
     * 事件资源key，具有唯一性
     * ex:
     * 1. 节点类型对应fullPath
     * 2. 仓库类型对应仓库名称
     * 3. 包类型对应包名称
     */
    val resourceKey: String,
    /**
     * 操作用户
     */
    val userId: String,
    /**
     * 附属数据
     */
    val data: Map<String, Any> = mapOf(),

    val address: String
)
