package com.tencent.bkrepo.common.mongo.reactive.dao

import com.tencent.bkrepo.common.mongo.util.MonthRangeShardingUtils
import com.tencent.bkrepo.common.mongo.util.ShardingUtils

abstract class MonthRangeShardingMongoReactiveDao<E> : ShardingMongoReactiveDao<E>() {

    override fun determineShardingUtils(): ShardingUtils {
        return MonthRangeShardingUtils
    }
}
