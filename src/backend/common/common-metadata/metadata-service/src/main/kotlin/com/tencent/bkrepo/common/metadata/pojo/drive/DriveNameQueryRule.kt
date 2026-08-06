package com.tencent.bkrepo.common.metadata.pojo.drive

import com.tencent.bkrepo.common.query.enums.OperationType

/**
 * Drive 文件名查询条件，语义对齐节点搜索中的 `name` 规则
 */
data class DriveNameQueryRule(
    val value: Any,
    val operation: OperationType = OperationType.EQ,
)
