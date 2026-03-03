package com.tencent.bkrepo.common.metadata.dao.file

import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.model.TDriveFileReference
import com.tencent.bkrepo.common.mongo.reactive.dao.HashShardingMongoReactiveDao
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component

/**
 * Drive 文件摘要引用 Dao
 */
@Component
@Conditional(ReactiveCondition::class)
class RDriveFileReferenceDao : HashShardingMongoReactiveDao<TDriveFileReference>()
