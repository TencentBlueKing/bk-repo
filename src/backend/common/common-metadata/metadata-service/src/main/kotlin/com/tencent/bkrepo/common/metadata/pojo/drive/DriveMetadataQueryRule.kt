package com.tencent.bkrepo.common.metadata.pojo.drive

import com.tencent.bkrepo.common.query.enums.OperationType

/**
 * Drive 文件查询的元数据条件，语义对齐节点搜索中的 `metadata.{key}` 规则
 */
data class DriveMetadataQueryRule(
    val key: String,
    val value: Any,
    val operation: OperationType = OperationType.EQ,
)
