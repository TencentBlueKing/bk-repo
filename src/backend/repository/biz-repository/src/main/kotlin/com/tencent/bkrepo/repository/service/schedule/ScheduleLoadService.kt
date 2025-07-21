package com.tencent.bkrepo.repository.service.schedule

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.model.TScheduleLoad
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleLoadCreateRequest
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleQueryRequest
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleResult

interface ScheduleLoadService {
    // 创建预约下载任务
    fun saveScheduleLoad(request: ScheduleLoadCreateRequest)

    // 删除预约下载任务
    fun removeScheduleLoad(id: String)

    // 更新任务状态
    fun updateScheduleStatus(id: String, isEnabled: Boolean)

    // 查询预约下载任务
    fun queryScheduleLoad(userId: String, request: ScheduleQueryRequest): Page<ScheduleResult>

    // 根据id查询预约下载任务
    fun getScheduleLoadById(id: String): TScheduleLoad?
}