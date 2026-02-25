package com.tencent.bkrepo.common.metadata.dao.node

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.model.TDriveNode
import com.tencent.bkrepo.common.mongo.dao.sharding.HashShardingMongoDao
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Repository

@Repository
@Conditional(SyncCondition::class)
class DriveNodeDao : HashShardingMongoDao<TDriveNode>()
