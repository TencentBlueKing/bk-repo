package com.tencent.bkrepo.job.batch.utils

object MongoShardingUtils {

    /**
     * 计算所在分表序号
     */
    fun shardingSequence(value: Any, shardingCount: Int): Int {
        val hashCode = value.hashCode()
        return hashCode and shardingCount - 1
    }

}
