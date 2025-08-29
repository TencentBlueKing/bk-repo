package com.tencent.bkrepo.common.mongo.reactive.dao

import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.mongo.api.util.sharding.ShardingUtils


abstract class HashShardingMongoReactiveDao<E> : ShardingMongoReactiveDao<E>() {

    override fun determineShardingUtils(): ShardingUtils {
        return HashShardingUtils
    }
}
