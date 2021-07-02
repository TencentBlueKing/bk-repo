package com.tencent.bkrepo.common.artifact.event

/**
 * 支持审计信息的事件
 */
interface AuditableEvent: ArtifactEvent {

    /**
     * 操作客户端地址
     */
    val clientAddress: String
}
