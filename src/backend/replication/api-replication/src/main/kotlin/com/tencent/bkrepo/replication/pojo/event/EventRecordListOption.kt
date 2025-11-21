package com.tencent.bkrepo.replication.pojo.event

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 事件记录查询参数
 */
@Schema(title = "事件记录查询参数")
data class EventRecordListOption(
    @get:Schema(title = "当前页")
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    @get:Schema(title = "分页大小")
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    @get:Schema(title = "事件类型", allowableValues = ["NORMAL", "FEDERATION"])
    val eventType: String? = null,
    @get:Schema(title = "任务是否已完成")
    val taskCompleted: Boolean? = null,
    @get:Schema(title = "任务是否成功")
    val taskSucceeded: Boolean? = null,
    @get:Schema(title = "任务Key")
    val taskKey: String? = null,
    @get:Schema(title = "排序字段", allowableValues = ["createdDate", "lastModifiedDate"])
    val sortField: String? = "createdDate",
    @get:Schema(title = "排序方向", allowableValues = ["ASC", "DESC"])
    val sortDirection: String? = "DESC"
)

