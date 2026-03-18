package com.tencent.bkrepo.fs.server.repository.drive

import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.fs.server.model.drive.TDriveFileReference
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component

/**
 * Drive 文件摘要引用 Dao
 */
@Component
@Conditional(ReactiveCondition::class)
class RDriveFileReferenceDao : DriveHashShardingMongoReactiveDao<TDriveFileReference>()
