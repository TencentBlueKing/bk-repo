package com.tencent.bkrepo.common.artifact.event

/**
 * artifact事件接口
 */
interface ArtifactEvent {
    /**
     * 事件资源类型
     */
    val resourceType: ResourceType

    /**
     * 事件类型
     */
    val eventType: EventType

    /**
     * 项目id
     */
    val projectId: String

    /**
     * 仓库名称
     */
    val repoName: String

    /**
     * 事件资源key，具有唯一性
     * ex:
     * 1. 节点类型对应fullPath
     * 2. 仓库类型对应仓库名称
     * 3. 包类型对应包名称
     */
    val resourceKey: String

    /**
     * 附属数据
     */
    val data: Map<String, Any>

    /**
     * 操作用户
     */
    val userId: String
}
