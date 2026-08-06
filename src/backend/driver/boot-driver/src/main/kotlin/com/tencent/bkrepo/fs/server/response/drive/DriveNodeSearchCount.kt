package com.tencent.bkrepo.fs.server.response.drive

/**
 * 整仓文件搜索数量统计结果
 */
data class DriveNodeSearchCount(
    /**
     * 命中当前过滤条件的文件/系列总数
     */
    val total: Long,
    /**
     * 按 [com.tencent.bkrepo.fs.server.request.drive.DriveNodeSearchCountPayload.groupByMetadataKey]
     * 分桶结果；未指定 groupBy 时为空列表
     */
    val groups: List<DriveNodeSearchCountGroup> = emptyList(),
)

/**
 * 单个元数据分桶计数
 */
data class DriveNodeSearchCountGroup(
    /**
     * 分桶元数据值；缺失或空白时为 null
     */
    val value: Any? = null,
    /**
     * 该桶数量
     */
    val count: Long,
)
