package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.common.metadata.model.TMetadata
import com.tencent.bkrepo.repository.pojo.schedule.SchedulePlatformType
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleType
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("schedule_load")
@CompoundIndexes(
    CompoundIndex(
        name = "schedule_load_idx",
        def = "{'userId': 1, 'projectId': 1}",
        background = true,
    ),
    CompoundIndex(
        name = "schedule_enabled_idx",
        def = "{'isEnabled': 1}",
        background = true
    ),
)

data class TScheduleLoad(
    var id: String? = null,
    var userId: String? = null,
    var projectId: String,
    var repoName: String?,
    var fullPathRegex: String?,
    var nodeMetadata: MutableList<TMetadata>? = null,
    var cronExpression: String,
    var isCovered: Boolean = false,
    var isEnabled: Boolean = false,
    var platform: SchedulePlatformType = SchedulePlatformType.All,
    var type: ScheduleType,
    var createdDate: LocalDateTime,
    var lastModifiedDate: LocalDateTime,
)