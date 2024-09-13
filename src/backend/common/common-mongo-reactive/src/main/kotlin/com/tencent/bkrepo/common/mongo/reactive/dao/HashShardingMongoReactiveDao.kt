package com.tencent.bkrepo.common.mongo.reactive.dao

import com.tencent.bkrepo.common.mongo.util.HashShardingUtils
import com.tencent.bkrepo.common.mongo.util.ShardingUtils

abstract class HashShardingMongoReactiveDao<E> : ShardingMongoReactiveDao<E>() {

    override fun determineShardingUtils(): ShardingUtils {
        return HashShardingUtils
    }
}
