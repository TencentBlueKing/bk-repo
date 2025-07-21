package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.repository.pojo.schedule.SchedulePlatformType
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("schedule_load")
@CompoundIndexes(
    CompoundIndex(
        name = "schedule_load_idx",
        def = "{'userId': 1, 'projectId': 1,'pipeLineId': 1, 'buildId': 1}",
        background = true,
        unique = true
    ),
    CompoundIndex(
        name = "schedule_enabled_idx",
        def = "{'isEnabled': 1, 'lastExecutionTime': -1}",
        background = true
    ),
    CompoundIndex(
        name = "schedule_type_idx",
        def = "{'platform': 1}",
        background = true
    )
)
data class TScheduleLoad(
    var id: String? = null,
    var userId: String,
    var projectId: String,
    var pipeLineId: String,
    var buildId: String = "latest",
    var cronExpression: String,
    var isEnabled: Boolean = false,
    var platform: SchedulePlatformType = SchedulePlatformType.All,
    var rules: List<TScheduleRule>,
    var createdDate: LocalDateTime,
    var lastModifiedDate: LocalDateTime,
)