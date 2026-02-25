package com.tencent.bkrepo.common.metadata.dao.node

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.model.TDriveSnapSeq
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Repository

@Repository
@Conditional(SyncCondition::class)
class DriveSnapSeqDao : SimpleMongoDao<TDriveSnapSeq>()
