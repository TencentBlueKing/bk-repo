package com.tencent.bkrepo.fs.server.repository.drive

import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.fs.server.model.drive.TDriveBlockNode
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component

@Component
@Conditional(ReactiveCondition::class)
class RDriveBlockNodeDao : DriveHashShardingMongoReactiveDao<TDriveBlockNode>()
