package com.tencent.bkrepo.common.metadata.dao.node

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.model.TDriveSnapshot
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Repository

@Repository
@Conditional(SyncCondition::class)
class DriveSnapshotDao : SimpleMongoDao<TDriveSnapshot>()
