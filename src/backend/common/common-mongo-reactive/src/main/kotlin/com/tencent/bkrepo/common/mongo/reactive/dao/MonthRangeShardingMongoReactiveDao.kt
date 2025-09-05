package com.tencent.bkrepo.common.mongo.reactive.dao

import com.tencent.bkrepo.common.mongo.api.util.sharding.MonthRangeShardingUtils
import com.tencent.bkrepo.common.mongo.api.util.sharding.ShardingUtils


abstract class MonthRangeShardingMongoReactiveDao<E> : ShardingMongoReactiveDao<E>() {

    override fun determineShardingUtils(): ShardingUtils {
        return MonthRangeShardingUtils
    }
}
