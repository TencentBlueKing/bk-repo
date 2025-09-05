package com.tencent.bkrepo.job.service

/**
 * block node表迁移服务
 */
interface MigrateBlockNodeCollectionService {
    /**
     * 迁移block node表数据
     *
     * @param oldCollectionNamePrefix 旧表明
     * @param newCollectionNamePrefix 新表名
     * @param newShardingColumns 新的分表键
     * @param newShardingCount 新分表数
     */
    fun migrate(
        oldCollectionNamePrefix: String,
        newCollectionNamePrefix: String,
        newShardingColumns: List<String>,
        newShardingCount: Int
    )
}
