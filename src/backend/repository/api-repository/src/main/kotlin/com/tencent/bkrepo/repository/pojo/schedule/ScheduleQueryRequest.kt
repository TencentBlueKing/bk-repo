package com.tencent.bkrepo.repository.pojo.schedule

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE

data class ScheduleQueryRequest(
    val projectId: String?,
    val repoName: String?,
    val fullPathRegex: String?,
    val pipelineId: String?,
    val isEnable: Boolean?,
    val nodeMetadata: Map<String, Any>? = null, // 修改为Map结构
    var pageNumber: Int = DEFAULT_PAGE_NUMBER,
    var pageSize: Int = DEFAULT_PAGE_SIZE
)