package com.tencent.bkrepo.fs.server.repository

import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.model.TDriveNode
import com.tencent.bkrepo.common.mongo.reactive.dao.HashShardingMongoReactiveDao
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component

@Component
@Conditional(ReactiveCondition::class)
class RDriveNodeDao : HashShardingMongoReactiveDao<TDriveNode>()
