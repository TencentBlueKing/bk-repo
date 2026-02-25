package com.tencent.bkrepo.common.metadata.dao.node

import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.model.TDriveSnapshot
import com.tencent.bkrepo.common.mongo.reactive.dao.SimpleMongoReactiveDao
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component

@Component
@Conditional(ReactiveCondition::class)
class RDriveSnapshotDao : SimpleMongoReactiveDao<TDriveSnapshot>()
