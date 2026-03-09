package com.tencent.bkrepo.fs.server.utils

import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import java.time.LocalDateTime

object DriveNodeQueryHelper {
    /**
     * 基于旧版本节点构造 COW 新副本。
     *
     * 新副本保持相同 ino，重置文档主键，挂到当前快照序列号。
     */
    fun buildCowNode(
        origin: TDriveNode,
        currentSnapSeq: Long,
        operator: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): TDriveNode {
        return origin.copy(
            id = null,
            lastModifiedBy = operator,
            lastModifiedDate = now,
            deleted = null,
            snapSeq = currentSnapSeq,
            deleteSnapSeq = Long.MAX_VALUE,
        )
    }
}
