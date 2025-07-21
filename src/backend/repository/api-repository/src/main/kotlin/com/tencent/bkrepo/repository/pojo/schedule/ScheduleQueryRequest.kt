package com.tencent.bkrepo.repository.pojo.schedule

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE

data class ScheduleQueryRequest(
    val projectId: String?,
    val pipeLineId: String?,
    val buildId: String?,
    val isEnable: Boolean?,
    var pageNumber: Int = DEFAULT_PAGE_NUMBER,
    var pageSize: Int = DEFAULT_PAGE_SIZE
)