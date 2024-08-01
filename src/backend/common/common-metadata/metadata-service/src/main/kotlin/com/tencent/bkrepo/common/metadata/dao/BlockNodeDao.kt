package com.tencent.bkrepo.common.metadata.dao

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.mongo.dao.sharding.HashShardingMongoDao
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Repository

@Repository
@Conditional(SyncCondition::class)
class BlockNodeDao : HashShardingMongoDao<TBlockNode>()
